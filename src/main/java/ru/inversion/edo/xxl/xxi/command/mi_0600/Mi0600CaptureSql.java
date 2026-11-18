package ru.inversion.edo.xxl.xxi.command.mi_0600;

import javax.persistence.NamedNativeQueries;
import javax.persistence.NamedNativeQuery;

/**
 * Read-only dialect SQL declarations for legacy outgoing 601/603/611 capture.
 * Selection predicates are admin-configured trusted SQL fragments (aliases o/b).
 * XXIDsDao chooses ".pg" automatically for PostgreSQL.
 */
@NamedNativeQueries({
   @NamedNativeQuery(
      name = "Mi0600CaptureSql.selectSource",
      query = """
         SELECT o.int_id, o.filename
           FROM xxi.ni_outfiles o
           JOIN xxi.ni_bases b ON b.id_base = o.id_base
          WHERE o.rec_state = '0'
            AND (%s)
            AND NOT EXISTS (
               SELECT 1 FROM xxi.mi_0600_f f
                WHERE f.czip_file_name = o.filename
            )
            AND ROWNUM <= ?
          FOR UPDATE OF o.rec_state SKIP LOCKED
         """
   ),
   @NamedNativeQuery(
      name = "Mi0600CaptureSql.selectSource.pg",
      query = """
         SELECT o.int_id, o.filename
           FROM xxi.ni_outfiles o
           JOIN xxi."NI_BASES" b ON b.id_base = o.id_base
          WHERE o.rec_state = '0'
            AND (%s)
            AND NOT EXISTS (
               SELECT 1 FROM xxi.mi_0600_f f
                WHERE f.czip_file_name = o.filename
            )
          ORDER BY o.int_id
          LIMIT ?
          FOR UPDATE OF o SKIP LOCKED
         """
   ),
   @NamedNativeQuery(
      name = "Mi0600CaptureSql.inconsistencies",
      query = """
         SELECT o.int_id, o.filename, o.rec_state
           FROM xxi.ni_outfiles o
           JOIN xxi.ni_bases b ON b.id_base = o.id_base
          WHERE (%s)
            AND o.rec_state = '0'
            AND EXISTS (
                SELECT 1 FROM xxi.mi_0600_f f
                 WHERE f.czip_file_name = o.filename
            )
            AND ROWNUM <= ?
         """
   ),
   @NamedNativeQuery(
      name = "Mi0600CaptureSql.inconsistencies.pg",
      query = """
         SELECT o.int_id, o.filename, o.rec_state
           FROM xxi.ni_outfiles o
           JOIN xxi."NI_BASES" b ON b.id_base = o.id_base
          WHERE (%s)
            AND o.rec_state = '0'
            AND EXISTS (
                SELECT 1 FROM xxi.mi_0600_f f
                 WHERE f.czip_file_name = o.filename
            )
          ORDER BY o.int_id
          LIMIT ?
         """
   ),
   @NamedNativeQuery(
      name = "Mi0600CaptureSql.fileData",
      query = """
         SELECT o.filebody AS file_data
           FROM xxi.ni_outfiles o
          WHERE o.int_id = ?
         """
   ),
   @NamedNativeQuery(
      name = "Mi0600CaptureSql.fileData.pg",
      query = """
         SELECT p_odb_blob_bfile.get_clob(
                   'SELECT FILEBODY FROM NI_OUTFILES WHERE int_id = $1', ?
                ) AS file_data
         """
   )
})
public final class Mi0600CaptureSql
{
   private Mi0600CaptureSql() { }
}
