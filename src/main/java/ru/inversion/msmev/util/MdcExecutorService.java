package ru.inversion.msmev.util;

import org.slf4j.MDC;

import java.util.List;
import java.util.Map;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

public final class MdcExecutorService extends AbstractExecutorService
{
   private final ExecutorService delegate;

   public MdcExecutorService( ExecutorService delegate )
   {
      this.delegate = delegate;
   }

   @Override
   public void execute( Runnable command )
   {
      delegate.execute( wrap(command) );
   }

   private Runnable wrap( Runnable command )
   {
      Map<String, String> context = MDC.getCopyOfContextMap();

      return () -> {
         Map<String, String> previous =
                 MDC.getCopyOfContextMap();

         try
         {
            if( context == null )
               MDC.clear();
            else
               MDC.setContextMap(context);

            command.run();
         }
         finally
         {
            if( previous == null )
               MDC.clear();
            else
               MDC.setContextMap(previous);
         }
      };
   }

   @Override
   public void shutdown()
   {
      delegate.shutdown();
   }

   @Override
   public List<Runnable> shutdownNow()
   {
      return delegate.shutdownNow();
   }

   @Override
   public boolean isShutdown()
   {
      return delegate.isShutdown();
   }

   @Override
   public boolean isTerminated()
   {
      return delegate.isTerminated();
   }

   @Override
   public boolean awaitTermination( long timeout, TimeUnit unit ) throws InterruptedException
   {
      return delegate.awaitTermination(timeout, unit);
   }
}