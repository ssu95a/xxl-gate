package ru.inversion.msmev.mi.business;

import org.springframework.stereotype.Component;
import ru.inversion.mi.transport.ReceivedMessage;
import ru.inversion.msmev.error.Errors;
import ru.inversion.msmev.error.XXLException;
import ru.inversion.msmev.util.Attrs;
import ru.inversion.msmev.xxi.repo.InfRepository;
import ru.inversion.utils.S;
import ru.inversion.utils.U;

import java.util.*;

/**
 * Dispatcher бизнес-запросов MI -> XXI.
 *
 * Зона ответственности:
 * - парсит ReceivedMessage в MiBusinessRequest;
 * - находит business handler;
 * - вызывает XXI API/handler для регистрации или обработки входящего запроса;
 * - формирует MiBusinessResponse;
 * - при бизнес-ошибке формирует error-response, а не просто падает.
 *
 * Не публикует ответ в очередь сам.
 */
@Component
public final class MiBusinessRequestDispatcher
{
   private final MiBusinessRequestParser parser;
   private final Map<Integer, MiBusinessRequestHandler> handlers;

   final private InfRepository infRepository;

   public MiBusinessRequestDispatcher( MiBusinessRequestParser parser, List<MiBusinessRequestHandler> handlers, InfRepository infRepository )
   {
      this.parser   = parser;
      this.handlers = buildRegistry(handlers);
      this.infRepository = infRepository;
   }

   /** */
   public MiBusinessResponse dispatch( ReceivedMessage message )
   {
      MiBusinessRequest request = null;

      try
      {
         request = parser.parse(message);

         if( request == null )
             throw Errors.miBusinessPayloadBadFormat( "MI business parser returned null", attributes(message, null).toMap() );

         Integer infId = resolveInfId( request.requestType() );

         if( infId == null )
         {
            throw Errors.miBusinessPayloadBadFormat(
              "MI business requestType is empty",
              attributes(message, request).toMap()
            );
         }

         MiBusinessRequestHandler handler = handlers.get(infId);

         if( handler == null )
         {
            throw Errors.miBusinessPayloadBadFormat(
                 "Unsupported MI business requestType",
                 attributes(message, request) .put("available_request_types", handlers.keySet()).toMap()
            );
         }

         MiBusinessResponse response = handler.handle(request);

         if( response == null )
         {
            throw Errors.miBusinessCallFailed(
                    "MI business handler returned null",
                    null,
                    attributes(message, request)
                            .put("handler", handler.getClass().getName())
                            .toMap()
            );
         }

         return response;
      }
      catch( XXLException exception )
      {
         return errorResponse(message, request, exception);
      }
      catch( Exception exception )
      {
         XXLException normalized =
                 Errors.internal(
                         "MI business request dispatch failed",
                         exception,
                         attributes(message, request).toMap()
                 );

         return errorResponse(message, request, normalized);
      }
   }

   /** */
   private static Map<Integer, MiBusinessRequestHandler> buildRegistry( List<MiBusinessRequestHandler> source )
   {
      Map<Integer, MiBusinessRequestHandler> result = new LinkedHashMap<>();

      if( source == null || source.isEmpty() )
          return Map.of();

      for( MiBusinessRequestHandler handler : source )
      {
         if( handler == null )
             continue;

         final Set<Integer> infIds = handler.infIds();

         if( infIds == null || infIds.isEmpty() )
         {
            throw Errors.config (
              "MI business handler declares no requestTypes",
              U.toMap( "handler", handler.getClass().getName() )
            );
         }

         for( Integer infId : infIds )
         {
            if( infId == null )
                continue;

            MiBusinessRequestHandler previous = result.putIfAbsent( infId, handler);

            if( previous != null )
               throw Errors.config (
                  "Duplicate MI business requestType", U.toMap( "infId", infId, "handler_1", previous.getClass().getName(), "handler_2", handler.getClass().getName() )
               );
         }
      }

      return Map.copyOf(result);
   }

   /** */
   private static MiBusinessResponse errorResponse (
      ReceivedMessage message,
      MiBusinessRequest request,
      XXLException exception
   )
   {
      return MiBusinessResponse.error(
              originalRequestId(message, request),
              exception.getResultCode(),
              exception.getMessage(),
              attributes(message, request)
                      .merge(exception.getAttributes())
                      .put("namespace", exception.getNamespace().name())
                      .toMap()
      );
   }

   /** */
   private static UUID originalRequestId(
           ReceivedMessage message,
           MiBusinessRequest request
   )
   {
      if( request != null && request.messageId() != null )
         return request.messageId();

      if( message != null )
         return message.getRequestId();

      return null;
   }

   /** */
   private static Attrs attributes(
           ReceivedMessage message,
           MiBusinessRequest request
   )
   {
      Attrs result = Attrs.create();

      if( message != null )
      {
         result
           .putIfNotNull("request_id", message.getRequestId())
           .putIfNotNull("original_request_id", message.getOriginalRequestId())
           .putIfNotNull("mi_correlation_id", message.getMiCorrelationId())
           .putIfNotNull("inf_id", message.getInfId())
           .putIfNotNull("inf_namespace", message.getInfNamespace())
           .putIfNotNull("source_system", message.getSourceSystem())
           .putIfNotNull("source_version", message.getSourceVersion())
           .putIfNotNull("created_at", message.getCreatedAt())
           .putIfNotNull("occurred_at", message.getOccurredAt())
           .putIfNotNull("delivery_tag", message.getDeliveryTag());
      }

      if( request != null )
      {
         result
                 .putIfNotNull("business_message_id", request.messageId())
                 .putIfNotNull("request_type", request.requestType())
                 .putIfNotNull("business_created_at", request.createdAt());
      }

      return result;
   }

   /** */
   private Integer resolveInfId( String ns )
   {
      if( S.isNullOrEmpty(ns) )
          return null;

      ns = ns.trim().toLowerCase(Locale.ROOT);

      return infRepository.findInfIdByNamespace(ns);

   }
}