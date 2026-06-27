package ru.inversion.msmev.mi.response;

public class MiAsyncResponseTerminalException extends RuntimeException {

   private final ProcessResult result;

   public MiAsyncResponseTerminalException( ProcessResult result )
   {
      super( result.resultCode() + ": " + result.resultInfo() );
      this.result = result;
   }

   public ProcessResult result()
   {
      return result;
   }
}