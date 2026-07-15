package ru.inversion.msmev.util;

import org.slf4j.MDC;

public final class XxlLog
{
   private static final String PREFIX = "xxl.prefix";

   private XxlLog()
   {
   }

   public static Scope module( Module module )
   {
      String previous = MDC.get(PREFIX);
      MDC.put(PREFIX, module.label());
      return new Scope(previous);
   }

   public enum Module
   {
      ASYNC(" [XXL-ASYNC]"),
      BUSINESS(" [XXL-BUSINESS]"),
      INTERNAL(" [XXL-INTERNAL]"),
      XXI(" [XXI]"),
      DB(" [XXL-DB]"),
      TRANSPORT(" [XXL-TRANSPORT]");

      private final String label;

      Module( String label )
      {
         this.label = label;
      }

      public String label()
      {
         return label;
      }
   }
   public static final class Scope implements AutoCloseable
   {
      private final String previous;

      private Scope( String previous )
      {
         this.previous = previous;
      }

      @Override
      public void close()
      {
         if( previous == null )
             MDC.remove(PREFIX);
         else
             MDC.put(PREFIX, previous);
      }
   }
}