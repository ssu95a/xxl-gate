package ru.inversion.edo.xxl.mi.business.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.http.MediaType;
import ru.inversion.datacall.IDataCall;
import ru.inversion.datacall.SQLCallBuilder;
import ru.inversion.dataset.IParameters;
import ru.inversion.edo.xxl.error.XXLException;
import ru.inversion.edo.xxl.mi.business.MiBusinessPayload;
import ru.inversion.edo.xxl.mi.business.MiBusinessRequest;
import ru.inversion.edo.xxl.mi.business.MiBusinessResult;
import ru.inversion.edo.xxl.xxi.db.XxiRepositoryExecutor;
import ru.inversion.mi.transport.async.payload.ReceivedPayload;
import ru.inversion.tc.TaskContext;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class Handler607DuplicateTest
{
   private static final String OPERATION = "MI_0607.process_Document_Received";
   private static final OffsetDateTime DOC_DATE = OffsetDateTime.parse("2026-11-20T12:00:00+03:00");

   @Test
   void repeatedBusinessRequestIsRejectedEvenWhenTransportAndFileChange() throws Exception
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

      Handler_607 handler = new Handler_607(new Repository_607(db), new ObjectMapper().findAndRegisterModules());
      MiBusinessRequest first = request(original, "first.xml", "AQ==");
      MiBusinessRequest duplicate = request(original, "other.xml", "Ag==");
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
      assertNull(failure.attributes().get("req_id"));
      assertNull(failure.attributes().get("itm_id"));
      assertNull(failure.attributes().get("base_id"));
      assertNull(failure.attributes().get("file_type"));
      assertNull(failure.attributes().get("parse_result"));
      verify(firstContext).commit();
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
            repository.processDocumentReceived(null, UUID.randomUUID(), UUID.randomUUID(),
                  UUID.randomUUID(), "UNO", DOC_DATE, "first.xml", new byte[]{1}));

      assertTrue(result.getMessage().contains("originalRequestId"));
      verifyNoInteractions(db);
   }

   private static MiBusinessRequest request(UUID original, String fileName, String fileData) throws Exception
   {
      String json = """
            {"taskId":"%s","docType":"UNO","docDate":"%s","fileName":"%s",
             "fileData":"%s","responseDateTime":"%s"}
            """.formatted(UUID.randomUUID(), DOC_DATE, fileName, fileData, DOC_DATE);
      ReceivedPayload source = mock(ReceivedPayload.class);
      when(source.openStream()).thenReturn(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
      UUID messageId = UUID.randomUUID();
      return new MiBusinessRequest(607, messageId, messageId, original, UUID.randomUUID(),
            "DOCUMENT_RECEIVED", null, DOC_DATE, null, null,
            new MiBusinessPayload(source, MediaType.APPLICATION_JSON), Map.of(), Map.of());
   }
}
