package ru.inversion.edo.xxl.xxi.repo;

import javax.persistence.NamedNativeQueries;
import javax.persistence.NamedNativeQuery;

/**
 * Чтение MI_INF без предположений о наличии PG-полей транспорта в Oracle.
 * Маршрутизация Oracle Multi-Bus хранится в MI_Prp, не в MI_INF.
 */
@NamedNativeQueries({
      @NamedNativeQuery(
            name = "PInfSql.byId",
            query = """
                  SELECT i.inf_id, i.wsp_id, i.initiator_cd,
                         i.name_inf, i.namespace_inf,
                         CAST(NULL AS VARCHAR2(100)) AS request_queue,
                         CAST(NULL AS VARCHAR2(100)) AS response_queue
                    FROM xxi.mi_inf i
                   WHERE i.inf_id = ?
                  """
      ),
      @NamedNativeQuery(
            name = "PInfSql.byId.pg",
            query = """
                  SELECT i.inf_id, i.wsp_id, i.initiator_cd,
                         i.name_inf, i.namespace_inf,
                         i.request_queue, i.response_queue
                    FROM xxi.mi_inf i
                   WHERE i.inf_id = ?
                  """
      )
})
public final class PInfSql
{
   private PInfSql() { }
}
