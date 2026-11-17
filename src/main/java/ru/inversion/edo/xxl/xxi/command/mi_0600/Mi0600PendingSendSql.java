package ru.inversion.edo.xxl.xxi.command.mi_0600;

import javax.persistence.NamedNativeQueries;
import javax.persistence.NamedNativeQuery;

/**
 * Только чтение кандидатов на отправку MI_0600.
 *
 * Запрос не захватывает работу и не меняет статус MI_REQ.
 * Претендент должен повторно пройти штатные проверки
 * XxiCommandDispatcher / take4Proc перед публикацией.
 */
@NamedNativeQueries({
      @NamedNativeQuery(
            name = "Mi0600PendingSendSql",
            query = """
                  SELECT req_id, inf_id, external_uuid, correlation_id
                    FROM (
                         SELECT r.req_id, r.inf_id, r.external_uuid, r.correlation_id
                           FROM xxi.mi_req r
                           JOIN xxi.mi_inf i ON i.inf_id = r.inf_id
                          WHERE r.status_cd = 0
                            AND r.inf_id IN (601, 603)
                            AND i.initiator_cd = -1
                          ORDER BY r.req_id
                    )
                   WHERE ROWNUM <= ?
                  """
      ),
      @NamedNativeQuery(
            name = "Mi0600PendingSendSql.pg",
            query = """
                  SELECT r.req_id, r.inf_id, r.external_uuid, r.correlation_id
                    FROM xxi.mi_req r
                    JOIN xxi.mi_inf i ON i.inf_id = r.inf_id
                   WHERE r.status_cd = 0
                     AND r.inf_id IN (601, 603)
                     AND i.initiator_cd = -1
                   ORDER BY r.req_id
                   LIMIT ?
                  """
      )
})
public final class Mi0600PendingSendSql
{
   private Mi0600PendingSendSql() { }
}
