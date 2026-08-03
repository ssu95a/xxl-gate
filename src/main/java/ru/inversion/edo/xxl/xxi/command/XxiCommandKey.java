package ru.inversion.edo.xxl.xxi.command;

/** */
public record XxiCommandKey( Integer infId, String action )
{
   public XxiCommandKey
   {
      if( action == null || action.isBlank() )
          throw new IllegalArgumentException( "Command action must not be blank" );

      if( infId != null && infId <= 0 )
          throw new IllegalArgumentException( "Command infId must be positive or null" );

      action = action.trim().toLowerCase();
   }

   /** */
   public boolean global()
   {
      return infId == null;
   }
}