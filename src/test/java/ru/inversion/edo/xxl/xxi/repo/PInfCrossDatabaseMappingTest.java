package ru.inversion.edo.xxl.xxi.repo;

import org.junit.jupiter.api.Test;
import javax.persistence.NamedNativeQueries;
import javax.persistence.NamedNativeQuery;
import java.sql.ResultSet;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PInfCrossDatabaseMappingTest
{
   @Test
   void oracleHasNoTransportQueueColumnReferencesAndPgRetainsThem()
   {
      NamedNativeQueries queries = PInfSql.class.getAnnotation(NamedNativeQueries.class);
      Map<String, String> sql = Arrays.stream(queries.value())
            .collect(Collectors.toMap(NamedNativeQuery::name, NamedNativeQuery::query));

      String oracle = sql.get("PInfSql.byId");
      String pg = sql.get("PInfSql.byId.pg");
      assertNotNull(oracle);
      assertNotNull(pg);
      assertTrue(oracle.contains("CAST(NULL AS VARCHAR2(100)) AS request_queue"));
      assertTrue(oracle.contains("CAST(NULL AS VARCHAR2(100)) AS response_queue"));
      assertFalse(oracle.contains("i.request_queue"));
      assertFalse(oracle.contains("i.response_queue"));
      assertTrue(pg.contains("i.request_queue"));
      assertTrue(pg.contains("i.response_queue"));
      assertEquals(1, oracle.chars().filter(c -> c == '?').count());
      assertEquals(1, pg.chars().filter(c -> c == '?').count());
   }

   @Test
   void initiatorAndRespondentAreMappedByExactDatabaseCodes() throws Exception
   {
      ResultSet initiator = mock(ResultSet.class);
      when(initiator.getInt("inf_id")).thenReturn(601);
      when(initiator.getInt("wsp_id")).thenReturn(600);
      when(initiator.getInt("initiator_cd")).thenReturn(-1);
      PInf source = InfRepository.mapInf(initiator);
      assertEquals(InfRole.Initiator, source.role());
      assertNull(source.getRequestQueue());
      assertNull(source.getResponseQueue());

      ResultSet respondent = mock(ResultSet.class);
      when(respondent.getInt("inf_id")).thenReturn(602);
      when(respondent.getInt("wsp_id")).thenReturn(600);
      when(respondent.getInt("initiator_cd")).thenReturn(1);
      assertEquals(InfRole.Respondent, InfRepository.mapInf(respondent).role());

      ResultSet corrupt = mock(ResultSet.class);
      when(corrupt.getInt("inf_id")).thenReturn(601);
      when(corrupt.getInt("wsp_id")).thenReturn(600);
      when(corrupt.getInt("initiator_cd")).thenReturn(0);
      assertThrows(java.sql.SQLException.class, () -> InfRepository.mapInf(corrupt));
   }
}
