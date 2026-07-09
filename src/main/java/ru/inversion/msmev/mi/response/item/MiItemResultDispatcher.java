package ru.inversion.msmev.mi.response.item;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.inversion.mi.transport.model.MiAsyncItemResult;
import ru.inversion.msmev.error.Errors;
import ru.inversion.msmev.error.XXLException;
import ru.inversion.msmev.mi.response.MiAsyncResponse;
import ru.inversion.utils.Checks;
import ru.inversion.utils.U;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;

@Component
public class MiItemResultDispatcher {

   private final Map<String, MiItemResultRepository> repositories;

   private final ExecutorService itemExecutor;

   private final int parallelism;

   /** */
   public MiItemResultDispatcher(

      List<MiItemResultRepository> repositories,

      @Qualifier("miResponseItemExecutor")
      ExecutorService itemExecutor,

      @Value("${mi.response.item-parallelism:4}")
      int parallelism
   )
   {
      if( parallelism < 1)
          parallelism = 4;

      this.repositories = buildSet(repositories);
      this.itemExecutor = Objects.requireNonNull( itemExecutor, "itemExecutor" );
      this.parallelism  = parallelism;
   }

   /**
    * Обработать ITEM_RESULT контейнер.
    * <p>
    * Одновременно запускается не более parallelism items.
    * Каждый repository.applyItem() должен открывать собственный
    * TaskContext и собственную DB-транзакцию.
    */
   public MiItemApplySummary dispatch( MiAsyncResponse response )
   {
      Checks.Require.object( response, "response" );

      if( response.itemCount() == 0 )
          throw Errors.miResponseBadFormat( "ITEM_RESULT container is empty", response.parameters() );

      MiItemResultRepository repository = findRepository( response.infNamespace() );

      ExecutorCompletionService<MiItemExecution> completionService = new ExecutorCompletionService<>( itemExecutor );

      Set<Future<MiItemExecution>> inFlight = new HashSet<>();

      List<MiItemExecution> completed = new ArrayList<>( response.itemCount() );

      int nextItemIndex = 0;

      int initialWindow = Math.min( parallelism, response.itemCount() );

      /*
       * Первоначальное окно.
       */
      while( nextItemIndex < initialWindow)
      {
         Future<MiItemExecution> future = submit( completionService, repository, response, nextItemIndex );
         inFlight.add(future);
         nextItemIndex++;
      }

      XXLException retryableFailure = null;
      RuntimeException terminalFailure = null;

      boolean stopSubmitting = false;

      /*
       * В памяти одновременно находится не более parallelism
       * активных Future для данного контейнера.
       */
      while( !inFlight.isEmpty())
      {
         Future<MiItemExecution> completedFuture;

         try {

            completedFuture = completionService.take();

         } catch (InterruptedException exception) {

            cancelAll(inFlight);

            Thread.currentThread().interrupt();

            throw Errors.technicalBreak("ITEM_RESULT processing interrupted",exception,response.parameters() );
         }

         inFlight.remove(completedFuture);
         MiItemExecution execution = getCompleted( completedFuture, response );

         completed.add(execution);

         if( execution.failure() != null )
         {
            Throwable failure = execution.failure();

            if( failure instanceof XXLException exception )
            {

               if( isRetryable(exception) )
               {
                  if( retryableFailure == null )
                      retryableFailure = exception;

               }
               else
               {
                  if( terminalFailure == null )
                      terminalFailure = exception;
               }

            }
            else if (terminalFailure == null)
            {
               terminalFailure = unexpectedItemFailure( response, execution, failure );
            }

            /*
             * Новые items не запускаем.
             * Уже запущенные обязательно дожидаемся.
             */
            stopSubmitting = true;
         }

         /*
          * Освободилось место в окне.
          * При отсутствии системной ошибки запускаем следующий item.
          */
         if (!stopSubmitting
                 && nextItemIndex < response.itemCount()) {

            Future<MiItemExecution> future =
                    submit(
                            completionService,
                            repository,
                            response,
                            nextItemIndex
                    );

            inFlight.add(future);
            nextItemIndex++;
         }

      }//end while

      /*
       * Terminal имеет приоритет над retry.
       *
       * Terminal означает ошибку контракта или реализации,
       * которую повторная доставка, скорее всего, не исправит.
       */
      if( terminalFailure != null )
          throw terminalFailure;

      if( retryableFailure != null )
          throw retryableFailure;

      completed.sort( Comparator.comparingInt( MiItemExecution::itemIndex ) );

      return summarize( response, completed );
   }

   /**
    * Отправить один item в executor.
    */
   private Future<MiItemExecution> submit(
           ExecutorCompletionService<MiItemExecution> completionService,
           MiItemResultRepository repository,
           MiAsyncResponse response,
           int itemIndex
   ) {
      MiAsyncItemResult item =
              response.itemResults()
                      .get(itemIndex);

      try {
         return completionService.submit(
                 () -> executeItem(
                         repository,
                         response,
                         item,
                         itemIndex
                 )
         );

      } catch (RejectedExecutionException exception) {
         throw Errors.technicalBreak(
                 "MI item executor rejected task",
                 exception,
                 response.itemParameters(
                         item,
                         itemIndex
                 )
         );
      }
   }

   /**
    * Выполнение одного item в рабочем потоке.
    *
    * repository.applyItem() должен:
    * - открыть отдельный TaskContext;
    * - получить отдельное DB-соединение;
    * - выполнить собственный commit/rollback;
    * - вернуть FAILED для ожидаемой бизнес-ошибки;
    * - бросить исключение для инфраструктурной ошибки.
    */
   private MiItemExecution executeItem(
           MiItemResultRepository repository,
           MiAsyncResponse response,
           MiAsyncItemResult item,
           int itemIndex
   ) {
      try {
         MiItemApplyResult result =
                 repository.applyItem(
                         response,
                         item,
                         itemIndex
                 );

         validateResult(
                 response,
                 item,
                 itemIndex,
                 result
         );

         return new MiItemExecution(
                 itemIndex,
                 item.itemExternalUuid(),
                 result,
                 null
         );

      } catch (RuntimeException failure) {
         return new MiItemExecution(
                 itemIndex,
                 item.itemExternalUuid(),
                 null,
                 failure
         );
      }
   }

   /**
    * Получить завершённую задачу.
    *
    * Обычные RuntimeException уже преобразованы executeItem()
    * в MiItemExecution.failure.
    */
   private MiItemExecution getCompleted(
           Future<MiItemExecution> future,
           MiAsyncResponse response
   ) {
      try {
         return future.get();

      } catch (InterruptedException exception) {
         Thread.currentThread().interrupt();

         throw Errors.technicalBreak(
                 "ITEM_RESULT result wait interrupted",
                 exception,
                 response.parameters()
         );

      } catch (CancellationException exception) {
         throw Errors.technicalBreak(
                 "ITEM_RESULT task was cancelled",
                 exception,
                 response.parameters()
         );

      } catch (ExecutionException exception) {
         Throwable cause =
                 exception.getCause();

         /*
          * JVM Error не превращаем в обычную бизнес-ошибку.
          */
         if (cause instanceof Error error)
            throw error;

         throw Errors.internal(
                 "Unexpected ITEM_RESULT task failure",
                 cause,
                 response.parameters()
         );
      }
   }

   /**
    * Проверить, что repository вернул результат именно
    * для переданного item.
    */
   private void validateResult(
           MiAsyncResponse response,
           MiAsyncItemResult item,
           int itemIndex,
           MiItemApplyResult result
   ) {
      if (result == null) {
         throw Errors.internal(
                 "ITEM_RESULT repository returned null",
                 null,
                 response.itemParameters(
                         item,
                         itemIndex
                 )
         );
      }

      if (result.status() == null) {
         throw Errors.internal(
                 "ITEM_RESULT repository returned null status",
                 null,
                 response.itemParameters(
                         item,
                         itemIndex
                 )
         );
      }

      if (result.itemIndex() != itemIndex) {
         throw Errors.internal(
                 "ITEM_RESULT repository returned incorrect itemIndex",
                 null,
                 U.toMap(
                         "expected_item_index",
                         itemIndex,
                         "actual_item_index",
                         result.itemIndex(),
                         "item_external_uuid",
                         item.itemExternalUuid()
                 )
         );
      }

      UUID expectedUuid =
              item.itemExternalUuid();

      if (!Objects.equals(
              expectedUuid,
              result.itemExternalUuid()
      )) {
         throw Errors.internal(
                 "ITEM_RESULT repository returned incorrect itemExternalUuid",
                 null,
                 U.toMap(
                         "item_index",
                         itemIndex,
                         "expected_item_external_uuid",
                         expectedUuid,
                         "actual_item_external_uuid",
                         result.itemExternalUuid()
                 )
         );
      }
   }


   /**
    * Собрать итог успешно обработанного контейнера.
    *
    * FAILED является обработанным бизнес-результатом,
    * поэтому не приводит к retry.
    */
   private MiItemApplySummary summarize(
           MiAsyncResponse response,
           List<MiItemExecution> executions
   ) {
      int applied = 0;
      int alreadyApplied = 0;
      int failed = 0;

      List<MiItemApplyResult> results =
              new ArrayList<>(
                      executions.size()
              );

      for (MiItemExecution execution : executions) {
         MiItemApplyResult result =
                 execution.result();

         results.add(result);

         switch (result.status()) {
            case APPLIED ->
                    applied++;

            case ALREADY_APPLIED ->
                    alreadyApplied++;

            case FAILED ->
                    failed++;
         }
      }

      return new MiItemApplySummary(
              response.itemCount(),
              applied,
              alreadyApplied,
              failed,
              List.copyOf(results)
      );
   }

   /**
    * Неожиданная ошибка конкретного item.
    */
   private RuntimeException unexpectedItemFailure(
           MiAsyncResponse response, MiItemExecution execution, Throwable failure
   )
   {
      MiAsyncItemResult item = response.itemResults().get(execution.itemIndex());

      return Errors.internal(
              "Unexpected ITEM_RESULT processing error",
              failure,
              response.itemParameters(
                item,
                execution.itemIndex()
              )
      );
   }

   /**
    * Ошибки, после которых сообщение нужно доставить повторно.
    */
   private boolean isRetryable( XXLException exception )
   {
      return switch (exception.getResultCode()) {
         case Errors.ResultCode.DB_ERROR,
              Errors.ResultCode.TECHNICAL_BREAK -> true;

         default -> false;
      };
   }

   /**
    * Best effort отмена уже отправленных задач.
    */
   private void cancelAll( Set<Future<MiItemExecution>> futures )
   {
      for( Future<MiItemExecution> future : futures )
           future.cancel(true);
   }


   /** */
   private MiItemResultRepository findRepository( String infNamespace )
   {
      String namespace = normalize(infNamespace);

      MiItemResultRepository repository = repositories.get(namespace);

      if( repository == null )
      {
         throw Errors.config (
           "ITEM_RESULT repository not found",
           U.toMap( "inf_namespace", infNamespace, "available_namespaces", repositories.keySet() )
         );
      }

      return repository;
   }


   /** */
   private Map<String, MiItemResultRepository> buildSet( List<MiItemResultRepository> source )
   {
      List<MiItemResultRepository> repositories = source == null ? List.of() : source;

      Map<String, MiItemResultRepository> result = new LinkedHashMap<>();

      for( MiItemResultRepository repository : repositories )
      {
         if (repository == null)
         {
            continue;
            //throw Errors.config( "Null ITEM_RESULT repository", Map.of() );
         }

         String namespace = normalize( repository.infNamespace() );

         if( namespace == null )
             continue;
             //throw Errors.config( "Empty infNamespace in ITEM_RESULT repository", U.toMap( "repository", repository.getClass().getName() ) );

         MiItemResultRepository previous = result.put( namespace, repository );

         if( previous != null )
         {
            throw Errors.config(
                    "Duplicate ITEM_RESULT repository",
                    U.toMap(
                            "inf_namespace",
                            namespace,
                            "repository_1",
                            previous.getClass().getName(),
                            "repository_2",
                            repository.getClass().getName()
                    )
            );
         }
      }//end for

      return Collections.unmodifiableMap(result);
   }

   /** */
   private String normalize( String value )
   {
      if( value == null )
          return null;

      String normalized = value.trim().toLowerCase(Locale.ROOT);
      return normalized.isEmpty() ? null : normalized;
   }
}