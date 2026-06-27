package ru.inversion.msmev.mi.response.request;

import ru.inversion.msmev.mi.response.MiAsyncResponse;
import ru.inversion.msmev.mi.response.ProcessResult;

import java.util.List;

/**
 * Частная обработка REQUEST_REJECTED / REQUEST_FAILED
 * для конкретного вида интеграции.
 */
public interface MiRequestFailureProcessor {

   /**
    *
    */
   List<String> supportsNamespaces( );

   /**
    * Полностью применить request-level ошибку к XXI.
    */
   ProcessResult handle(MiAsyncResponse response );
}