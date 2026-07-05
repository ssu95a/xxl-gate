package ru.inversion.msmev.mi.response.request;

import ru.inversion.msmev.mi.response.MiAsyncResponse;
import ru.inversion.msmev.mi.response.ProcessResult;

import java.util.List;

/**
 * Частная обработка ошибочных ситуаций уровня запроса
 * REQUEST_REJECTED / REQUEST_FAILED
 * для конкретного вида(ов) ВС
 * <p>
 * По умолчанию применяется обработчик, который ставит запросу статус -1
 *
 * @see DefaultMiRequestFailureHandler
 */
public interface MiRequestFailureProcessor {

   /**
    * Поддерживаемые обработчиком ВС
    */
   List<String> supportsNamespaces( );

   /**
    * Полностью применить request-level ошибку к XXI.
    */
   ProcessResult handle( MiAsyncResponse response );
}