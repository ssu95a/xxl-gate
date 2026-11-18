package ru.inversion.edo.xxl.xxi.repo;

import org.junit.jupiter.api.Test;

import javax.persistence.NamedNativeQueries;
import javax.persistence.NamedNativeQuery;
import java.sql.ResultSet;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PReqCrossDatabaseMappingTest
{
   private static Map<String, String> queries()
   {
      NamedNativeQueries declaration =
            PReqSql.class.getAnnotation(NamedNativeQueries.class);
      assertNotNull(declaration);
      return Arrays.stream(declaration.value())
            .collect(Collectors.toMap(NamedNativeQuery::name, NamedNativeQuery::query));
   }

   @Test
   void bothDialectsExposeSameOriginalUuidAliasAndBindParameters()
   {
      Map<String, String> sql = queries();
      assertEquals(4, sql.size());

      for (String name : new String[] {"PReqSql.byId", "PReqSql.byExternalUuid"})
      {
         String oracle = sql.get(name);
         String postgres = sql.get(name + ".pg");
         assertNotNull(oracle);
         assertNotNull(postgres);
         assertTrue(oracle.contains("r.original_req_uuid AS original_request_uuid"));
         assertTrue(postgres.contains("r.original_request_uuid"));
         assertTrue(oracle.contains("r.external_uuid"));
         assertTrue(postgres.contains("r.external_uuid"));
         assertEquals(1, oracle.chars().filter(c -> c == '?').count());
         assertEquals(1, postgres.chars().filter(c -> c == '?').count());
      }

      assertTrue(sql.get("PReqSql.byExternalUuid.pg")
            .contains("CAST(? AS uuid)"));
      assertTrue(sql.get("PReqSql.byExternalUuid")
            .contains("r.external_uuid = ?"));
   }

   @Test
   void oracleCharUuidAndNullableOriginalAreMapped()
         throws Exception
   {
      ResultSet rs = mock(ResultSet.class);
      UUID external = UUID.fromString("01a1eae1-0603-7e53-8c2b-48b259be3cff");
      UUID correlation = UUID.fromString("01a1eebf-77f6-7d80-b847-cfe19c093c6d");

      when(rs.getLong("req_id")).thenReturn(351L);
      when(rs.getInt("inf_id")).thenReturn(601);
      when(rs.getInt("status_cd")).thenReturn(0);
      when(rs.getInt("itype")).thenReturn(1);
      when(rs.getString("external_uuid")).thenReturn("  " + external + " ");
      when(rs.getString("correlation_id")).thenReturn(correlation.toString());
      when(rs.getString("original_request_uuid")).thenReturn(null);

      PReq req = ReqRepository.mapRequest(rs);

      assertEquals(351L, req.getRequestId());
      assertEquals(601, req.getInfId());
      assertEquals(0, req.getStatus());
      assertEquals(1, req.getType());
      assertEquals(external, req.getExternalUuid());
      assertEquals(correlation, req.getCorrelationId());
      assertNull(req.getOriginalRequestUuid());
   }

   @Test
   void rejectsCorruptedUuidWithoutSilentlyChangingIdentity()
         throws Exception
   {
      ResultSet rs = mock(ResultSet.class);
      when(rs.getLong("req_id")).thenReturn(351L);
      when(rs.getInt("inf_id")).thenReturn(601);
      when(rs.getInt("status_cd")).thenReturn(0);
      when(rs.getInt("itype")).thenReturn(1);
      when(rs.getString("external_uuid")).thenReturn("NOT_A_UUID");

      assertThrows(java.sql.SQLException.class,
            () -> ReqRepository.mapRequest(rs));
   }
}
