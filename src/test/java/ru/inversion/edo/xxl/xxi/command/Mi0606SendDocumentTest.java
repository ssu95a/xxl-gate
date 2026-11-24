package ru.inversion.edo.xxl.xxi.command;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import ru.inversion.edo.xxl.error.Errors;
import ru.inversion.edo.xxl.error.XXLExceptionMapper;
import ru.inversion.edo.xxl.transport.MiPublishReceipt;
import ru.inversion.edo.xxl.transport.MiPublisher;
import ru.inversion.edo.xxl.transport.PayloadDto;
import ru.inversion.edo.xxl.transport.XxlMiEnvelope;
import ru.inversion.edo.xxl.xxi.command.mi_0600.MI_0606_Handler;
import ru.inversion.edo.xxl.xxi.command.mi_0600.MI_0606_Repository;
import ru.inversion.edo.xxl.xxi.protocol.XXLRequest;
import ru.inversion.edo.xxl.xxi.protocol.XXLResponse;
import ru.inversion.edo.xxl.xxi.repo.InfRepository;
import ru.inversion.edo.xxl.xxi.repo.PInf;
import ru.inversion.edo.xxl.xxi.repo.PReq;
import ru.inversion.edo.xxl.xxi.repo.ReqRepository;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class Mi0606SendDocumentTest {
   private static final long OUTFILE_ID = 123L;
   private static final long REQ_ID = 456L;
   private static final int INF_ID = 606;
   private static final UUID CALL_UUID = UUID.randomUUID();
   private static final UUID REQUEST_UUID = UUID.randomUUID();
   private static final UUID CORRELATION_ID = UUID.randomUUID();
   private static final String PARAMETERS = "{\"outfile_id\":123}";

   private final MI_0606_Repository repository = mock(MI_0606_Repository.class);
   private final ReqRepository reqRepository = mock(ReqRepository.class);
   private final InfRepository infRepository = mock(InfRepository.class);
   private final MiPublisher publisher = mock(MiPublisher.class);
   private XxiCommandDispatcher dispatcher;
   private MI_0606_Handler handler;

   @BeforeEach
   void setUp() {
      XxiCommandContextFactory factory = new XxiCommandContextFactory(reqRepository, infRepository);
      handler = new MI_0606_Handler(reqRepository, publisher, repository, factory);
      XxiHandlerRegistry registry = new XxiHandlerRegistry(List.of(handler), List.of(handler));
      registry.init();
      dispatcher = new XxiCommandDispatcher(new XxiRequestValidator(), registry, factory,
            new XXLExceptionMapper(new ObjectMapper()), mock(XxiResponseHandler.class));
   }

   @Test
   void dispatchesDirectCommandThroughRegistrationAndExistingSendWorkflow() {
      registeredRequest(false, 0);
      publishingSucceeds();

      XXLResponse response = dispatcher.dispatch(request(PARAMETERS));

      assertTrue(response.isSuccess(), response.getResultInfo());
      assertEquals("send_document", response.getAction());
      assertEquals("SEND_PUBLISHED", response.getResultCode());
      assertEquals(REQ_ID, response.getParameters().get("req_id"));
      assertEquals(3, response.getParameters().get("status_cd"));

      ArgumentCaptor<XxlMiEnvelope> envelope = ArgumentCaptor.forClass(XxlMiEnvelope.class);
      InOrder order = inOrder(repository, reqRepository, publisher);
      order.verify(repository).createRequest(OUTFILE_ID);
      order.verify(reqRepository).getRequest(REQ_ID);
      order.verify(reqRepository).take4Proc(REQ_ID, "MI_0606_Handler", CALL_UUID);
      order.verify(repository).prepareSendDocument(REQ_ID);
      order.verify(publisher).publishAsync(envelope.capture());
      order.verify(reqRepository).toSent(REQ_ID, CALL_UUID);

      assertEquals(REQUEST_UUID, envelope.getValue().ids().externalRequestUuid());
      assertEquals(CORRELATION_ID, envelope.getValue().ids().correlationId());
      assertEquals(CALL_UUID, envelope.getValue().ids().callUuid());
   }

   @Test
   void sendsDocumentByOutfileIdWhenInvokedFrom607() {
      registeredRequest(false, 0);
      publishingSucceeds();

      XXLResponse response = handler.sendDocument(OUTFILE_ID, CALL_UUID);

      assertTrue(response.isSuccess(), response.getResultInfo());
      assertEquals("SEND_PUBLISHED", response.getResultCode());
      assertEquals(REQ_ID, response.getParameters().get("req_id"));

      InOrder order = inOrder(repository, reqRepository, publisher);
      order.verify(repository).createRequest(OUTFILE_ID);
      order.verify(reqRepository).take4Proc(REQ_ID, "MI_0606_Handler", CALL_UUID);
      order.verify(repository).prepareSendDocument(REQ_ID);
      order.verify(publisher).publishAsync(any());
      order.verify(reqRepository).toSent(REQ_ID, CALL_UUID);
   }

   @Test
   void returnsExistingRequestEvenWhenItsStatusIsStillNew() {
      registeredRequest(true, 0);

      XXLResponse response = dispatcher.dispatch(request(PARAMETERS));

      assertTrue(response.isSuccess(), response.getResultInfo());
      assertEquals("send_document", response.getAction());
      assertEquals("ALREADY_REGISTERED", response.getResultCode());
      assertEquals(REQ_ID, response.getParameters().get("req_id"));
      assertEquals(0, response.getParameters().get("status_cd"));
      verify(repository, never()).prepareSendDocument(REQ_ID);
      verify(reqRepository, never()).take4Proc(REQ_ID, "MI_0606_Handler", CALL_UUID);
      verifyNoInteractions(publisher);
   }

   @Test
   void rejectsFractionalIdBeforeRegistration() {
      XXLResponse response = dispatcher.dispatch(request("{\"outfile_id\":123.5}"));

      assertFalse(response.isSuccess());
      assertEquals(Errors.ResultCode.CONTRACT_ERROR, response.getResultCode());
      verifyNoInteractions(repository, reqRepository, publisher);
   }

   @Test
   void returnsRegisteredRequestIdWhenSentStatusCouldNotBeRecorded() {
      registeredRequest(false, 0);
      publishingSucceeds();
      doThrow(new IllegalStateException("status update failed")).when(reqRepository).toSent(REQ_ID, CALL_UUID);

      XXLResponse response = dispatcher.dispatch(request(PARAMETERS));

      assertFalse(response.isSuccess());
      assertEquals(Errors.ResultCode.MI_PUBLISHED_STATUS_UPDATE_FAILED, response.getResultCode());
      assertEquals(REQ_ID, response.getParameters().get("req_id"));
      assertEquals("UNKNOWN", response.getParameters().get("delivery_outcome"));
      assertEquals("0", response.getParameters().get("retry_safe"));
      verify(publisher).publishAsync(any());
      verify(reqRepository, never()).toError(REQ_ID, CALL_UUID);
   }

   @Test
   void returnsRegisteredRequestIdWhenPublisherFails() {
      registeredRequest(false, 0);
      when(repository.prepareSendDocument(REQ_ID)).thenReturn(PayloadDto.json(new byte[]{1}));
      when(publisher.publishAsync(any())).thenThrow(
            Errors.miPublishFailed("MI publication failed", new IllegalStateException("timeout"), Map.of()));

      XXLResponse response = dispatcher.dispatch(request(PARAMETERS));

      assertFalse(response.isSuccess());
      assertEquals(Errors.ResultCode.MI_PUBLISH_FAILED, response.getResultCode());
      assertEquals(REQ_ID, response.getParameters().get("req_id"));
      assertEquals("UNKNOWN", response.getParameters().get("delivery_outcome"));
      assertEquals("0", response.getParameters().get("retry_safe"));
      verify(publisher).publishAsync(any());
      verify(reqRepository, never()).toSent(REQ_ID, CALL_UUID);
   }

   private void registeredRequest(boolean alreadyRegistered, int status) {
      when(repository.createRequest(OUTFILE_ID)).thenReturn(new MI_0606_Repository.Registration(REQ_ID, alreadyRegistered));
      PReq req = new PReq();
      req.setRequestId(REQ_ID);
      req.setInfId(INF_ID);
      req.setStatus(status);
      req.setExternalUuid(REQUEST_UUID);
      req.setCorrelationId(CORRELATION_ID);
      when(reqRepository.getRequest(REQ_ID)).thenReturn(req);

      PInf inf = new PInf();
      inf.setInfId(INF_ID);
      inf.setWspId(600);
      inf.setNamespace("SMEV.606");
      when(infRepository.getInf(INF_ID)).thenReturn(inf);
   }

   private void publishingSucceeds() {
      when(repository.prepareSendDocument(REQ_ID))
            .thenReturn(PayloadDto.json("{\"type\":\"SEND_DOCUMENT\"}".getBytes(StandardCharsets.UTF_8)));
      when(publisher.publishAsync(any())).thenReturn(
            new MiPublishReceipt(UUID.randomUUID(), null, null, CORRELATION_ID.toString(), OffsetDateTime.now()));
   }

   private XXLRequest request(String parametersJson) {
      return XXLRequest.builder()
            .version("1.0")
            .action("send_document")
            .mode("auto")
            .infId(INF_ID)
            .callUuid(CALL_UUID)
            .timestamp("2026-11-24T13:00:00.000 +03:00")
            .parametersJson(parametersJson)
            .build();
   }
}
