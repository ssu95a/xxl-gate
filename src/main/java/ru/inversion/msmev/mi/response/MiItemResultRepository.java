package ru.inversion.msmev.mi.response;

public interface MiItemResultRepository {

   /**
    * Namespace интеграции, например mi_0001.
    */
   String infNamespace();

   /**
    * Применить весь ITEM_RESULT контейнер к XXI.
    */
   void apply(MiAsyncResponse response);
}