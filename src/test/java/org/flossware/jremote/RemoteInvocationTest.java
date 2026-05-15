package org.flossware.jremote;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;

class RemoteInvocationTest {
    private final ObjectMapper mapper = new ObjectMapper()
        .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Test
    void testMethodCallCreation() {
        RemoteInvocation inv = RemoteInvocation.methodCall(
            "obj-123",
            "testMethod",
            new Class<?>[]{String.class, int.class},
            new Object[]{"arg1", 42}
        );

        assertEquals(RemoteInvocation.RequestType.METHOD_CALL, inv.requestType());
        assertEquals("obj-123", inv.objectId());
        assertEquals("testMethod", inv.methodName());
        assertArrayEquals(
            new String[]{"java.lang.String", "int"},
            inv.parameterTypeNames()
        );
    }

    @Test
    void testCreateInstanceRequest() {
        RemoteInvocation inv = RemoteInvocation.createInstance(
            "client-123",
            "com.example.TestService",
            new Class<?>[]{String.class},
            new Object[]{"arg1"}
        );

        assertEquals(RemoteInvocation.RequestType.CREATE_INSTANCE, inv.requestType());
        assertEquals("client-123", inv.objectId());
        assertEquals("com.example.TestService", inv.interfaceClassName());
        assertArrayEquals(
            new String[]{"java.lang.String"},
            inv.parameterTypeNames()
        );
    }

    @Test
    void testCreateInstanceNoArgs() {
        RemoteInvocation inv = RemoteInvocation.createInstance(
            "client-123",
            "com.example.TestService",
            null,
            null
        );

        assertEquals(RemoteInvocation.RequestType.CREATE_INSTANCE, inv.requestType());
        assertEquals("com.example.TestService", inv.interfaceClassName());
        assertEquals(0, inv.parameterTypeNames().length);
    }

    @Test
    void testDestroyInstanceRequest() {
        RemoteInvocation inv = RemoteInvocation.destroyInstance(
            "client-123",
            "obj-456"
        );

        assertEquals(RemoteInvocation.RequestType.DESTROY_INSTANCE, inv.requestType());
        assertEquals("obj-456", inv.objectId());
        assertEquals("client-123", inv.interfaceClassName());
    }

    @Test
    void testGetParameterTypes() throws Exception {
        RemoteInvocation inv = RemoteInvocation.methodCall(
            "obj-123",
            "test",
            new Class<?>[]{String.class, Integer.class},
            new Object[]{}
        );

        Class<?>[] types = inv.getParameterTypes();
        assertArrayEquals(new Class<?>[]{String.class, Integer.class}, types);
    }

    @Test
    void testGetParameterTypesWithPrimitives() throws Exception {
        RemoteInvocation inv = RemoteInvocation.methodCall(
            "obj-123",
            "test",
            new Class<?>[]{int.class, boolean.class, double.class},
            new Object[]{1, true, 3.14}
        );

        Class<?>[] types = inv.getParameterTypes();
        assertArrayEquals(new Class<?>[]{int.class, boolean.class, double.class}, types);
    }

    @Test
    void testJsonSerializationMethodCall() throws Exception {
        RemoteInvocation original = RemoteInvocation.methodCall(
            "obj-123",
            "echo",
            new Class<?>[]{String.class},
            new Object[]{"test"}
        );

        String json = mapper.writeValueAsString(original);
        RemoteInvocation deserialized = mapper.readValue(json, RemoteInvocation.class);

        assertEquals(RemoteInvocation.RequestType.METHOD_CALL, deserialized.requestType());
        assertEquals(original.objectId(), deserialized.objectId());
        assertEquals(original.methodName(), deserialized.methodName());
        assertArrayEquals(
            original.parameterTypeNames(),
            deserialized.parameterTypeNames()
        );
    }

    @Test
    void testJsonSerializationCreateInstance() throws Exception {
        RemoteInvocation original = RemoteInvocation.createInstance(
            "client-123",
            "com.example.TestService",
            new Class<?>[]{String.class},
            new Object[]{"arg"}
        );

        String json = mapper.writeValueAsString(original);
        RemoteInvocation deserialized = mapper.readValue(json, RemoteInvocation.class);

        assertEquals(RemoteInvocation.RequestType.CREATE_INSTANCE, deserialized.requestType());
        assertEquals(original.interfaceClassName(), deserialized.interfaceClassName());
    }

    @Test
    void testDefaultRequestTypeIsMethodCall() throws Exception {
        // Simulate JSON without requestType field (backward compatibility)
        String json = "{\"objectId\":\"obj-123\",\"interfaceClassName\":null,\"methodName\":\"test\",\"parameterTypeNames\":[],\"args\":[]}";

        RemoteInvocation deserialized = mapper.readValue(json, RemoteInvocation.class);

        assertEquals(RemoteInvocation.RequestType.METHOD_CALL, deserialized.requestType());
    }
}
