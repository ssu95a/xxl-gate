package ru.inversion.msmev.util;

import org.slf4j.MDC;

public final class XxlLog
{
   private static final String CONSOLE_PREFIX = "xxl.console.prefix";

   private static final String FILE_PREFIX = "xxl.file.prefix";

   private static final String RESET = "\u001B[0m";

   private XxlLog()
   { }

   public static Scope module( Module module )
   {
      String previousConsole = MDC.get(CONSOLE_PREFIX);

      String previousFile = MDC.get(FILE_PREFIX);

      MDC.put( CONSOLE_PREFIX, " " + module.color() + module.label() + RESET );
      MDC.put( FILE_PREFIX, " " + module.label() );

      return new Scope(previousConsole, previousFile);
   }

   public enum Module
   {
      ASYNC("[XXL-ASYNC]", "\u001B[95m"),       // pink / bright magenta
      BUSINESS("[XXL-BUSINESS]", "\u001B[92m"), // bright green
      INTERNAL("[XXL-INTERNAL]", "\u001B[96m"), // bright cyan
      XXI("[XXI]", "\u001B[93m"),               // bright yellow
      DB("[XXL-DB]", "\u001B[94m"),             // bright blue
      TRANSPORT("[XXL-TRANSPORT]", "\u001B[90m");

      private final String label;
      private final String color;

      Module( String label, String color )
      {
         this.label = label;
         this.color = color;
      }

      public String label()
      {
         return label;
      }

      public String color()
      {
         return color;
      }
   }

   public static final class Scope implements AutoCloseable
   {
      private final String previousConsole;
      private final String previousFile;

      private Scope( String previousConsole, String previousFile )
      {
         this.previousConsole = previousConsole;
         this.previousFile = previousFile;
      }

      @Override
      public void close()
      {
         restore(CONSOLE_PREFIX, previousConsole);
         restore(FILE_PREFIX, previousFile);
      }

      private void restore( String key, String value )
      {
         if( value == null )
            MDC.remove(key);
         else
            MDC.put(key, value);
      }
   }
}