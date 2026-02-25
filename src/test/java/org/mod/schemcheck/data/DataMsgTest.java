package org.mod.schemcheck.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DataMsgTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void defaultIncludeBuiltIsTrue() {
        DataMsg msg = new DataMsg();
        assertTrue(msg.includeBuilt);
    }

    @Test
    void fieldsAreAssignable() {
        DataMsg msg = new DataMsg();
        msg.filename = "test.litematic";
        msg.mx1 = 1; msg.my1 = 2; msg.mz1 = 3;
        msg.mx2 = 4; msg.my2 = 5; msg.mz2 = 6;
        msg.includeBuilt = false;

        assertEquals("test.litematic", msg.filename);
        assertEquals(1, msg.mx1);
        assertEquals(2, msg.my1);
        assertEquals(3, msg.mz1);
        assertEquals(4, msg.mx2);
        assertEquals(5, msg.my2);
        assertEquals(6, msg.mz2);
        assertFalse(msg.includeBuilt);
    }

    @Test
    void jsonRoundTrip() throws Exception {
        DataMsg original = new DataMsg();
        original.filename = "build.litematic";
        original.mx1 = 10; original.my1 = 20; original.mz1 = 30;
        original.mx2 = 40; original.my2 = 50; original.mz2 = 60;
        original.includeBuilt = false;

        String json = mapper.writeValueAsString(original);
        DataMsg restored = mapper.readValue(json, DataMsg.class);

        assertEquals(original.filename, restored.filename);
        assertEquals(original.mx1, restored.mx1);
        assertEquals(original.my1, restored.my1);
        assertEquals(original.mz1, restored.mz1);
        assertEquals(original.mx2, restored.mx2);
        assertEquals(original.my2, restored.my2);
        assertEquals(original.mz2, restored.mz2);
        assertEquals(original.includeBuilt, restored.includeBuilt);
    }
}
