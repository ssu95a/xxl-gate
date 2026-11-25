package ru.inversion.edo.xxl.mi.business.handlers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.MockedStatic;
import org.springframework.http.MediaType;
import ru.inversion.datacall.IDataCall;
import ru.inversion.datacall.SQLCallBuilder;
import ru.inversion.dataset.IParameters;
import ru.inversion.edo.xxl.error.XXLException;
import ru.inversion.edo.xxl.mi.business.MiBusinessPayload;
import ru.inversion.edo.xxl.mi.business.MiBusinessRequest;
import ru.inversion.edo.xxl.mi.business.MiBusinessResult;
import ru.inversion.edo.xxl.xxi.command.mi_0600.MI_0606_Handler;
import ru.inversion.edo.xxl.xxi.db.XxiRepositoryExecutor;
import ru.inversion.edo.xxl.xxi.protocol.XXLResponse;
import ru.inversion.mi.transport.async.payload.ReceivedPayload;
import ru.inversion.tc.TaskContext;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.Mockito.*;

class Handler607DuplicateTest
{
   private static final String OPERATION = "MI_0607.process_Document_Received";
   private static final OffsetDateTime DOC_DATE = OffsetDateTime.parse("2026-11-20T12:00:00+03:00");
   private static final String DOCUMENT_RECEIVED_JSON = """
         {
           "taskId": "00000000-0000-4000-8000-000000000607",
           "docType": "UNO",
           "docDate": "2026-04-28T09:15:00Z",
           "resolutionId": "00000000-0000-4000-8000-000000000608",
           "fileName": "document.json",
           "file": "e30=",
           "resolutions": [
             {
               "resolutionFileName": "resolution.json",
               "resolutionFile": "e30="
             }
           ],
           "responseDateTime": "2026-04-28T09:15:00Z"
         }
         """;

   @ParameterizedTest
   @ValueSource(ints = {607, 608})
   void repeatedBusinessRequestIsRejectedEvenWhenTransportAndFileChange(int infId) throws Exception
   {
      XxiRepositoryExecutor db = mock(XxiRepositoryExecutor.class);
      TaskContext firstContext = mock(TaskContext.class);
      TaskContext duplicateContext = mock(TaskContext.class);
      when(firstContext.getConnection()).thenReturn(mock(Connection.class));
      when(duplicateContext.getConnection()).thenReturn(mock(Connection.class));
      when(db.<Repository_607.ReceiveResult>execute(eq(OPERATION), anyMap(), any()))
            .thenAnswer(call -> call.<XxiRepositoryExecutor.XxiDbWork<Repository_607.ReceiveResult>>
                  getArgument(2).execute(firstContext))
            .thenAnswer(call -> call.<XxiRepositoryExecutor.XxiDbWork<Repository_607.ReceiveResult>>
                  getArgument(2).execute(duplicateContext));

      IDataCall firstCall = mock(IDataCall.class);
      when(firstCall.execute()).thenReturn(firstCall);
      when(firstCall.getReturnValue()).thenReturn(0);
      when(firstCall.get("req_id")).thenReturn(10L);
      when(firstCall.get("itm_id")).thenReturn(20L);
      when(firstCall.get("base_id")).thenReturn(30L);
      when(firstCall.get("file_type")).thenReturn("UNO");
      when(firstCall.get("parse_result")).thenReturn(40L);

      UUID original = UUID.randomUUID();
      String duplicateInfo = "MI_0607_DUPLICATE_REQUEST: requestId=" + original;
      IDataCall duplicateCall = mock(IDataCall.class);
      when(duplicateCall.execute()).thenReturn(duplicateCall);
      when(duplicateCall.getReturnValue()).thenReturn(-1);
      when(duplicateCall.get("ret_info")).thenReturn(duplicateInfo);

      SQLCallBuilder firstBuilder = mock(SQLCallBuilder.class, RETURNS_SELF);
      SQLCallBuilder duplicateBuilder = mock(SQLCallBuilder.class, RETURNS_SELF);
      when(firstBuilder.build()).thenReturn(firstCall);
      when(duplicateBuilder.build()).thenReturn(duplicateCall);

      MI_0606_Handler outgoing606 = mock(MI_0606_Handler.class);
      when(outgoing606.sendDocument(eq(40L), any(UUID.class))).thenReturn(
            XXLResponse.success().resultCode("SEND_PUBLISHED").parameter("req_id", 41L).build());
      Handler_607 handler = new Handler_607(new Repository_607(db), outgoing606);
      assertEquals(Set.of(607, 608), handler.infIds());
      MiBusinessRequest first = request(infId, original, "first.xml", "AQ==");
      MiBusinessRequest duplicate = request(infId, original, "other.xml", "Ag==");
      MiBusinessResult success;
      MiBusinessResult failure;
      try (MockedStatic<SQLCallBuilder> factory = mockStatic(SQLCallBuilder.class)) {
         factory.when(() -> SQLCallBuilder.NEW(firstContext)).thenReturn(firstBuilder);
         factory.when(() -> SQLCallBuilder.NEW(duplicateContext)).thenReturn(duplicateBuilder);
         success = handler.handle(first);
         failure = handler.handle(duplicate);
      }

      assertTrue(success.success());
      assertFalse(failure.success());
      assertEquals("MI_0607_DUPLICATE_REQUEST", failure.responseCode());
      assertEquals(duplicateInfo, failure.responseInfo());
      assertEquals(infId, failure.attributes().get("inf_id"));
      assertNull(failure.attributes().get("req_id"));
      assertNull(failure.attributes().get("itm_id"));
      assertNull(failure.attributes().get("base_id"));
      assertNull(failure.attributes().get("file_type"));
      assertNull(failure.attributes().get("parse_result"));
      InOrder sendOrder = inOrder(firstContext, outgoing606);
      sendOrder.verify(firstContext).commit();
      sendOrder.verify(outgoing606).sendDocument(eq(40L), any(UUID.class));
      verifyNoMoreInteractions(outgoing606);
      verify(firstContext, never()).rollback();
      verify(duplicateContext).rollback();
      verify(duplicateContext, never()).commit();

      ArgumentCaptor<URL> definition = ArgumentCaptor.forClass(URL.class);
      verify(firstBuilder).url(definition.capture());
      assertNotNull(definition.getValue());
      try (InputStream xml = definition.getValue().openStream()) {
         assertTrue(new String(xml.readAllBytes(), StandardCharsets.UTF_8)
               .contains("name=\"" + OPERATION + "\""));
      }

      ArgumentCaptor<IParameters> params = ArgumentCaptor.forClass(IParameters.class);
      verify(firstBuilder).callBackParameters(params.capture());
      verify(duplicateBuilder).callBackParameters(params.capture());
      IParameters firstArgs = params.getAllValues().get(0);
      IParameters duplicateArgs = params.getAllValues().get(1);
      assertEquals(infId, firstArgs.getParameter("inf_id"));
      assertEquals(infId, duplicateArgs.getParameter("inf_id"));
      assertEquals(original.toString(), firstArgs.getParameter("original_request_uuid"));
      assertEquals(original.toString(), duplicateArgs.getParameter("original_request_uuid"));
      assertNotEquals(firstArgs.getParameter("message_uuid"), duplicateArgs.getParameter("message_uuid"));
      assertNotEquals(firstArgs.getParameter("task_id"), duplicateArgs.getParameter("task_id"));
      assertEquals("other.xml", duplicateArgs.getParameter("file_name"));
      assertArrayEquals(new byte[]{2}, ((InputStream) duplicateArgs.getParameter("file_body")).readAllBytes());
      assertEquals(Timestamp.valueOf("2026-11-20 09:00:00"), duplicateArgs.getParameter("doc_date"));
   }

   @Test
   void missingOriginalRequestIdFailsBeforeOpeningDatabaseContext()
   {
      XxiRepositoryExecutor db = mock(XxiRepositoryExecutor.class);
      Repository_607 repository = new Repository_607(db);

      XXLException result = assertThrows(XXLException.class, () ->
            repository.processDocumentReceived(607, null, UUID.randomUUID(), UUID.randomUUID(),
                  UUID.randomUUID(), "UNO", DOC_DATE, "first.xml", new byte[]{1}));

      assertTrue(result.getMessage().contains("originalRequestId"));
      verifyNoInteractions(db);
   }

   @ParameterizedTest
   @ValueSource(ints = {607, 608})
   void failed606PublicationLeavesCommittedRequestSuccessful(int infId) throws Exception
   {
      Repository_607 repository = mock(Repository_607.class);
      MI_0606_Handler outgoing606 = mock(MI_0606_Handler.class);
      when(repository.processDocumentReceived(eq(infId), any(), any(), any(), any(), anyString(), any(), anyString(), any()))
            .thenReturn(new Repository_607.ReceiveResult(0, "OK", 10L, 20L, 30L, "UNO", 40L));
      when(outgoing606.sendDocument(eq(40L), any(UUID.class)))
            .thenThrow(new IllegalStateException("MI publication failed"));
      Handler_607 handler = new Handler_607(repository, outgoing606);

      MiBusinessResult result = handler.handle(request(infId, UUID.randomUUID(), "first.xml", "AQ=="));

      assertTrue(result.success());
      verify(outgoing606).sendDocument(eq(40L), any(UUID.class));
      verifyNoMoreInteractions(outgoing606);
   }

   @ParameterizedTest
   @CsvSource({
         "607, 2026-04-28T09:15:00Z, 2026-04-28T09:15:00Z",
         "608, 2026-11-25T11:43:01.207202-05:00, 2026-11-25T16:43:01.207202Z",
         "607, 2026-11-25T11:43:01.207202, 2026-11-25T08:43:01.207202Z",
         "608, 2026-11-25T11:43:01.207202, 2026-11-25T08:43:01.207202Z"
   })
   void acceptsDocumentReceivedWithExplicitOffsetOrMoscowTime(int infId, String date, String expected) throws Exception
   {
      Repository_607 repository = mock(Repository_607.class);
      MI_0606_Handler outgoing606 = mock(MI_0606_Handler.class);
      when(repository.processDocumentReceived(eq(infId), any(), any(), any(), any(), anyString(), any(), anyString(), any()))
            .thenReturn(new Repository_607.ReceiveResult(0, "OK", 10L, 20L, 30L, "UNO", 40L));
      when(outgoing606.sendDocument(eq(40L), any(UUID.class))).thenReturn(XXLResponse.success().build());
      String json = DOCUMENT_RECEIVED_JSON.replace("2026-04-28T09:15:00Z", date);
      MiBusinessRequest request = request(infId, UUID.randomUUID(), json);

      MiBusinessResult result = new Handler_607(repository, outgoing606).handle(request);

      assertTrue(result.success());
      OffsetDateTime expectedDate = OffsetDateTime.parse(expected);
      verify(repository).processDocumentReceived(
            eq(infId), eq(request.requestId()), eq(request.messageId()), eq(request.correlationId()),
            eq(UUID.fromString("00000000-0000-4000-8000-000000000607")), eq("UNO"),
            argThat(actualDate -> actualDate != null && actualDate.isEqual(expectedDate)), eq("document.json"),
            aryEq("{}".getBytes(StandardCharsets.UTF_8)));
   }

   @ParameterizedTest
   @ValueSource(strings = {"invalid", "2010-03-28T02:30:00", "2010-10-31T02:30:00"})
   void reportsInvalidJsonFieldWithoutCallingRepository(String date) throws Exception
   {
      Repository_607 repository = mock(Repository_607.class);
      MI_0606_Handler outgoing606 = mock(MI_0606_Handler.class);
      String badJson = DOCUMENT_RECEIVED_JSON.replace(
            "\"docDate\": \"2026-04-28T09:15:00Z\"", "\"docDate\": \"" + date + "\"");
      MiBusinessRequest request = request(607, UUID.randomUUID(), badJson);

      XXLException failure = assertThrows(XXLException.class,
            () -> new Handler_607(repository, outgoing606).handle(request));

      assertEquals("CONTRACT_ERROR", failure.getResultCode());
      Map<String, Object> attributes = new LinkedHashMap<>(failure.getAttributes());
      attributes.put("payload_value", "sensitive document content");
      Map<String, Object> log = MiBusinessResult.error(request.requestId(), failure.getResultCode(),
            failure.getMessage(), attributes).dump();

      assertEquals("docDate", log.get("payload_field"));
      assertEquals("OffsetDateTime", log.get("payload_expected_type"));
      assertNotNull(log.get("payload_error_type"));
      assertNotNull(log.get("payload_line"));
      assertNotNull(log.get("payload_column"));
      assertFalse(log.containsValue("sensitive document content"));
      verifyNoInteractions(repository, outgoing606);
   }

   private static MiBusinessRequest request(int infId, UUID requestId, String fileName, String fileData) throws Exception
   {
      String json = """
            {"taskId":"%s","docType":"UNO","docDate":"%s","fileName":"%s",
             "file":"%s","responseDateTime":"%s"}
            """.formatted(UUID.randomUUID(), DOC_DATE, fileName, fileData, DOC_DATE);
      return request(infId, requestId, json);
   }

   private static MiBusinessRequest request(int infId, UUID requestId, String json) throws Exception
   {
      ReceivedPayload source = mock(ReceivedPayload.class);
      when(source.openStream()).thenReturn(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
      UUID messageId = UUID.randomUUID();
      return new MiBusinessRequest(infId, requestId, messageId, null, UUID.randomUUID(),
            "DOCUMENT_RECEIVED", null, DOC_DATE, null, null,
            new MiBusinessPayload(source, MediaType.APPLICATION_JSON), Map.of(), Map.of());
   }
}
