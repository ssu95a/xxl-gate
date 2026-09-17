package ru.inversion.edo.xxl.xxi.repo;

import ru.inversion.edo.xxl.Tags;

import java.util.NoSuchElementException;

public enum InfRole {

   Initiator(-1),
   Respondent(+1);

   final private int code;

   InfRole( int code) {
      this.code = code;
   }

   public int code() {
      return code;
   }

   public static InfRole of(int v )
   {
      return switch (v) {
         case 1 -> Respondent;
         case-1 -> Initiator;
         default -> throw new NoSuchElementException(Tags.PRODUCT_LABEL + "No item InfRole with code " + v);
      };
   }

   public static InfRole of( Integer v )
   {
      if( v == null )
         return null;

      return of( v.intValue() );
   }
   
}
