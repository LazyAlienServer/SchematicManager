package org.mod.schemcheck.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ResultMsgTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void defaultValuesAreZero() {
        ResultMsg msg = new ResultMsg();
        assertEquals(0, msg.correct);
        assertEquals(0, msg.total);
    }

    @Test
    void fieldsAreAssignable() {
        ResultMsg msg = new ResultMsg();
        msg.correct = 42;
        msg.total = 100;

        assertEquals(42, msg.correct);
        assertEquals(100, msg.total);
    }

    @Test
    void jsonRoundTrip() throws Exception {
        ResultMsg original = new ResultMsg();
        original.correct = 99;
        original.total = 200;

        String json = mapper.writeValueAsString(original);
        ResultMsg restored = mapper.readValue(json, ResultMsg.class);

        assertEquals(original.correct, restored.correct);
        assertEquals(original.total, restored.total);
    }
}
