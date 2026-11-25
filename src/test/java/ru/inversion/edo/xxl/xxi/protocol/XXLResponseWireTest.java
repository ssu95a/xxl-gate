package ru.inversion.edo.xxl.xxi.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class XXLResponseWireTest {
    private final ObjectMapper json = new ObjectMapper();
    private final XmlMapper xml = new XmlMapper();

    @Test
    void encodesVersion11ParametersInTheXmlResponse() throws Exception {
        XXLResponse response = XXLResponse.success().action("send_document").resultCode("SEND_PUBLISHED")
                .parameter("req_id", 7L)
                .parameter("published", true)
                .parameter("text", "a\"<>&\nб")
                .parameter("meta", Map.of("x", 1))
                .parameter("optional", null)
                .parameter("time", OffsetDateTime.parse("2026-11-24T12:30:00+03:00"))
                .build();

        JsonNode wire = xml.readTree(xml.writeValueAsString(response));
        assertEquals("1.1", wire.path("version").asText());
        assertEquals("SEND_PUBLISHED", wire.path("result_code").asText());
        assertEquals(json.readTree("""
                [{"name":"req_id","value":"7"},
                 {"name":"published","value":"true"},
                 {"name":"text","value":"a\\\"<>&\\nб"},
                 {"name":"meta","value":"{\\\"x\\\":1}"},
                 {"name":"optional","value":null},
                 {"name":"time","value":"2026-11-24T12:30:00+03:00"}]
                """), json.readTree(wire.path("parameters").asText()));
        assertEquals(7L, response.getParameters().get("req_id"));
    }

    @Test
    void encodesEmptyVersion11Errors() throws Exception {
        XXLResponse response = XXLResponse.fail().action("send_document").causeCode("DB_ERROR").build();
        JsonNode wire = xml.readTree(xml.writeValueAsString(response));

        assertEquals("1.1", wire.path("version").asText());
        assertEquals("[]", wire.path("parameters").asText());
        assertEquals("DB_ERROR", wire.path("cause_code").asText());
        assertFalse(response.isSuccess());
    }

    @Test
    void rejectsNamesAndValuesBeyondReaderLimits() {
        assertThrows(IllegalArgumentException.class, () -> XXLResponse.success()
                .parameter("x".repeat(101), "value").build());
        assertThrows(IllegalArgumentException.class, () -> XXLResponse.success()
                .parameter("name", "x".repeat(2001)).build());
    }
}
