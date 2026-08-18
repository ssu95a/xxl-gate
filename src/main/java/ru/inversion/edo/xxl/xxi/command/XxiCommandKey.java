package ru.inversion.edo.xxl.xxi.command;

import java.util.Locale;

/** <h5>Ключ для директ команды</h5>
 *  <p>
 *  зависит от:
 *  ВС, необходимого действия
 *  если ВС == null, то команда для всего ВС
 * */
public record XxiCommandKey( Integer infId, String action )
{
   public XxiCommandKey
   {
      if( action == null || action.isBlank() )
          throw new IllegalArgumentException( "Command action must not be blank" );

      if( infId != null && infId <= 0 )
          throw new IllegalArgumentException( "Command infId must be positive or null" );

      action = action.trim().toLowerCase(Locale.ROOT);
   }

   /** */
   public boolean global()
   {
      return infId == null;
   }
}