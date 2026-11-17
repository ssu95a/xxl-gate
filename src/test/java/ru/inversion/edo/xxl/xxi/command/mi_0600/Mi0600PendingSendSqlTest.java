package ru.inversion.edo.xxl.xxi.command.mi_0600;

import org.junit.jupiter.api.Test;

import javax.persistence.NamedNativeQueries;
import javax.persistence.NamedNativeQuery;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class Mi0600PendingSendSqlTest
{
   @Test
   void oracleAndPgSelectOnlyNewInitiator601And603()
   {
      NamedNativeQueries declarations =
            Mi0600PendingSendSql.class.getAnnotation(NamedNativeQueries.class);
      assertNotNull(declarations);

      Map<String, String> queries = java.util.Arrays.stream(declarations.value())
            .collect(Collectors.toMap(NamedNativeQuery::name, NamedNativeQuery::query));

      assertEquals(2, queries.size());
      String oracle = queries.get("Mi0600PendingSendSql");
      String pg = queries.get("Mi0600PendingSendSql.pg");
      assertNotNull(oracle);
      assertNotNull(pg);

      for (String sql : queries.values())
      {
         String normalized = sql.toLowerCase(java.util.Locale.ROOT);
         assertTrue(normalized.contains("r.status_cd = 0"));
         assertTrue(normalized.contains("r.inf_id in (601, 603)"));
         assertTrue(normalized.contains("i.initiator_cd = -1"));
         assertFalse(normalized.contains("for update"));
         assertFalse(normalized.contains("update xxi."));
         assertFalse(normalized.contains("delete from"));
      }

      assertTrue(oracle.contains("ROWNUM <= ?"));
      assertTrue(pg.contains("LIMIT ?"));
   }
}
