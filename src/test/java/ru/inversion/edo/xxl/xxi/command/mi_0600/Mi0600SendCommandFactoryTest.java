package ru.inversion.edo.xxl.xxi.command.mi_0600;

import org.junit.jupiter.api.Test;
import ru.inversion.edo.xxl.xxi.command.XxiRequestValidator;
import ru.inversion.edo.xxl.xxi.protocol.XXLRequest;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class Mi0600SendCommandFactoryTest
{
   private final Mi0600SendCommandFactory factory =
         new Mi0600SendCommandFactory(new XxiRequestValidator());

   @Test
   void producesValidatedNativeSendRequestWithoutTransport()
   {
      UUID externalUuid = UUID.randomUUID();
      UUID correlationId = UUID.randomUUID();
      UUID callUuid = UUID.randomUUID();

      MI_0600_Repository.PendingSendCandidate candidate =
            new MI_0600_Repository.PendingSendCandidate(
                  351L, 601, externalUuid, correlationId);

      XXLRequest request = factory.create(candidate, callUuid,
            OffsetDateTime.parse("2026-11-17T21:59:38.337+03:00"));

      assertEquals("1.0", request.getVersion());
      assertEquals("send", request.getAction());
      assertEquals("auto", request.getMode());
      assertEquals(351L, request.getRequestId());
      assertEquals(601, request.getInfId());
      assertEquals(externalUuid, request.getExternalUuid());
      assertEquals(correlationId, request.getCorrelationId());
      assertEquals(callUuid, request.getCallUuid());
      assertEquals("2026-11-17T21:59:38.337 +03:00", request.getTimestamp());
      assertEquals("{}", request.getParametersJson());
      assertNull(request.getMessageType());
      new XxiRequestValidator().validate(request);
   }

   @Test
   void candidateRejectsMissingAndUnsupportedFields()
   {
      UUID a = UUID.randomUUID();
      UUID b = UUID.randomUUID();

      assertThrows(IllegalArgumentException.class,
            () -> new MI_0600_Repository.PendingSendCandidate(0, 601, a, b));
      assertThrows(IllegalArgumentException.class,
            () -> new MI_0600_Repository.PendingSendCandidate(352, 611, a, b));
      assertThrows(IllegalArgumentException.class,
            () -> new MI_0600_Repository.PendingSendCandidate(352, 603, null, b));
      assertThrows(IllegalArgumentException.class,
            () -> new MI_0600_Repository.PendingSendCandidate(352, 603, a, null));
   }

   @Test
   void transportStubFailsClosedAndDoesNotClaimSuccessfulPublication()
   {
      MI_0600_MbusStubTestHelper.assertStubFails(
            new Mi0600MbusPublisherStub(),
            factory.create(
                  new MI_0600_Repository.PendingSendCandidate(
                        352, 603, UUID.randomUUID(), UUID.randomUUID()),
                  UUID.randomUUID(),
                  OffsetDateTime.parse("2026-11-17T21:59:38.337+03:00")));
   }

   private static final class MI_0600_MbusStubTestHelper
   {
      static void assertStubFails(Mi0600MbusPublisher publisher, XXLRequest request)
      {
         UnsupportedOperationException error = assertThrows(
               UnsupportedOperationException.class, () -> publisher.publish(request));
         assertTrue(error.getMessage().contains("Multi-Bus"));
      }
   }
}
