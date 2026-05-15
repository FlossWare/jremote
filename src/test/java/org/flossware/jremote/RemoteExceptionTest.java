package org.flossware.jremote;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;

class RemoteExceptionTest {
    private final ObjectMapper mapper = new ObjectMapper()
        .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Test
    void testFromThrowable() {
        IllegalArgumentException original = new IllegalArgumentException("Test error");
        RemoteException remote = RemoteException.fromThrowable(original);

        assertEquals("java.lang.IllegalArgumentException", remote.getOriginalExceptionType());
        assertEquals("Test error", remote.getOriginalMessage());
        assertTrue(remote.getMessage().contains("IllegalArgumentException"));
    }

    @Test
    void testJsonSerialization() throws Exception {
        RemoteException original = new RemoteException(
            "java.lang.RuntimeException",
            "Test message",
            new StackTraceElement[]{
                new StackTraceElement("TestClass", "testMethod", "Test.java", 42)
            }
        );

        String json = mapper.writeValueAsString(original);
        RemoteException deserialized = mapper.readValue(json, RemoteException.class);

        assertEquals(original.getOriginalExceptionType(), deserialized.getOriginalExceptionType());
        assertEquals(original.getOriginalMessage(), deserialized.getOriginalMessage());
    }

    @Test
    void testStackTracePreservation() {
        Exception original = new RuntimeException("Test");
        StackTraceElement[] originalStackTrace = original.getStackTrace();

        RemoteException remote = RemoteException.fromThrowable(original);

        assertNotNull(remote.getStackTrace());
        assertTrue(remote.getStackTrace().length > 0);
    }
}
