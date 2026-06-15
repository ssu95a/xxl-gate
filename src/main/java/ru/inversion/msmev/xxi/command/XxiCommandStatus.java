package ru.inversion.msmev.xxi.command;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import ru.inversion.msmev.Tags;

import java.util.NoSuchElementException;

/** Статусы запроса в XXI */
@RequiredArgsConstructor
@Getter
public enum XxiCommandStatus {

   STATUS_NEW    (0),
   STATUS_DONE   (1),
   STATUS_IN_WORK(2),
   STATUS_SENT   (3),
   STATUS_ERROR (-1);

   final int value;

   /** */
   public int value() {
      return value;
   }

   public static XxiCommandStatus of(int v )
   {
      return switch (v) {
         case 0 -> STATUS_NEW;
         case 1 -> STATUS_DONE;
         case 2 -> STATUS_IN_WORK;
         case 3 -> STATUS_SENT;
         case-1 -> STATUS_ERROR;
         default -> throw new NoSuchElementException(Tags.PRODUCT_LABEL + "No item XxiCommandStatus with code " + v);
      };
   }

   public static XxiCommandStatus of( Integer v )
   {
      if( v == null )
         return null;

      return of( v.intValue() );
   }

}
