package ru.inversion.edo.xxl.xxi.repo;

import javax.persistence.NamedNativeQueries;
import javax.persistence.NamedNativeQuery;

/**
 * Диалектные имена колонок MI_REQ с единым набором выходных alias.
 *
 * Oracle: ORIGINAL_REQ_UUID; PostgreSQL: ORIGINAL_REQUEST_UUID.
 * Никакого переключения диалекта в Java-коде.
 */
@NamedNativeQueries({
      @NamedNativeQuery(
            name = "PReqSql.byId",
            query = """
                  SELECT r.req_id, r.inf_id, r.status_cd, r.itype,
                         r.external_uuid, r.correlation_id,
                         r.original_req_uuid AS original_request_uuid
                    FROM xxi.mi_req r
                   WHERE r.req_id = ?
                  """
      ),
      @NamedNativeQuery(
            name = "PReqSql.byId.pg",
            query = """
                  SELECT r.req_id, r.inf_id, r.status_cd, r.itype,
                         r.external_uuid, r.correlation_id,
                         r.original_request_uuid
                    FROM xxi.mi_req r
                   WHERE r.req_id = ?
                  """
      ),
      @NamedNativeQuery(
            name = "PReqSql.byExternalUuid",
            query = """
                  SELECT r.req_id, r.inf_id, r.status_cd, r.itype,
                         r.external_uuid, r.correlation_id,
                         r.original_req_uuid AS original_request_uuid
                    FROM xxi.mi_req r
                   WHERE r.external_uuid = ?
                  """
      ),
      @NamedNativeQuery(
            name = "PReqSql.byExternalUuid.pg",
            query = """
                  SELECT r.req_id, r.inf_id, r.status_cd, r.itype,
                         r.external_uuid, r.correlation_id,
                         r.original_request_uuid
                    FROM xxi.mi_req r
                   WHERE r.external_uuid = CAST(? AS uuid)
                  """
      )
})
public final class PReqSql
{
   private PReqSql() { }
}
