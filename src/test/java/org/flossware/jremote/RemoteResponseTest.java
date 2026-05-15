package org.flossware.jremote;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;

class RemoteResponseTest {
    private final ObjectMapper mapper = new ObjectMapper()
        .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Test
    void testSuccessResponse() {
        RemoteResponse response = RemoteResponse.success("result", String.class);

        assertTrue(response.isSuccess());
        assertEquals("result", response.result());
        assertNull(response.error());
        assertEquals("java.lang.String", response.returnTypeName());
    }

    @Test
    void testFailureResponse() {
        RemoteException error = new RemoteException(
            "TestException",
            "Test error",
            new StackTraceElement[0]
        );
        RemoteResponse response = RemoteResponse.failure(error);

        assertFalse(response.isSuccess());
        assertNull(response.result());
        assertNotNull(response.error());
        assertEquals("TestException", response.error().getOriginalExceptionType());
    }

    @Test
    void testJsonSerializationSuccess() throws Exception {
        RemoteResponse original = RemoteResponse.success(42, Integer.class);

        String json = mapper.writeValueAsString(original);
        RemoteResponse deserialized = mapper.readValue(json, RemoteResponse.class);

        assertTrue(deserialized.isSuccess());
        assertEquals("java.lang.Integer", deserialized.returnTypeName());
    }

    @Test
    void testJsonSerializationFailure() throws Exception {
        RemoteException error = new RemoteException(
            "TestException",
            "Test message",
            new StackTraceElement[0]
        );
        RemoteResponse original = RemoteResponse.failure(error);

        String json = mapper.writeValueAsString(original);
        RemoteResponse deserialized = mapper.readValue(json, RemoteResponse.class);

        assertFalse(deserialized.isSuccess());
        assertEquals("TestException", deserialized.error().getOriginalExceptionType());
    }
}
