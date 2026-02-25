package org.mod.schemcheck.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WsProtocolTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void noArgConstructorCreatesEmptyFields() {
        WsProtocol msg = new WsProtocol();
        assertNull(msg.id);
        assertNull(msg.action);
        assertNull(msg.data);
    }

    @Test
    void parameterizedConstructorSetsFields() {
        WsProtocol msg = new WsProtocol("req-1", "progress", "{\"filename\":\"test.litematic\"}");

        assertEquals("req-1", msg.id);
        assertEquals("progress", msg.action);
        assertEquals("{\"filename\":\"test.litematic\"}", msg.data);
    }

    @Test
    void jsonRoundTrip() throws Exception {
        WsProtocol original = new WsProtocol("id-123", "check", "{\"key\":\"value\"}");

        String json = mapper.writeValueAsString(original);
        WsProtocol restored = mapper.readValue(json, WsProtocol.class);

        assertEquals(original.id, restored.id);
        assertEquals(original.action, restored.action);
        assertEquals(original.data, restored.data);
    }

    @Test
    void deserializeFromJson() throws Exception {
        String json = "{\"id\":\"abc\",\"action\":\"material\",\"data\":\"payload\"}";
        WsProtocol msg = mapper.readValue(json, WsProtocol.class);

        assertEquals("abc", msg.id);
        assertEquals("material", msg.action);
        assertEquals("payload", msg.data);
    }
}
