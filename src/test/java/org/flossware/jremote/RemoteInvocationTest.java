package org.flossware.jremote;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;

class RemoteInvocationTest {
    private final ObjectMapper mapper = new ObjectMapper()
        .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Test
    void testConstructorWithClasses() {
        RemoteInvocation inv = new RemoteInvocation(
            "testMethod",
            new Class<?>[]{String.class, int.class},
            new Object[]{"arg1", 42}
        );

        assertEquals("testMethod", inv.methodName());
        assertArrayEquals(
            new String[]{"java.lang.String", "int"},
            inv.parameterTypeNames()
        );
    }

    @Test
    void testGetParameterTypes() throws Exception {
        RemoteInvocation inv = new RemoteInvocation(
            "test",
            new Class<?>[]{String.class, Integer.class},
            new Object[]{}
        );

        Class<?>[] types = inv.getParameterTypes();
        assertArrayEquals(new Class<?>[]{String.class, Integer.class}, types);
    }

    @Test
    void testGetParameterTypesWithPrimitives() throws Exception {
        RemoteInvocation inv = new RemoteInvocation(
            "test",
            new Class<?>[]{int.class, boolean.class, double.class},
            new Object[]{1, true, 3.14}
        );

        Class<?>[] types = inv.getParameterTypes();
        assertArrayEquals(new Class<?>[]{int.class, boolean.class, double.class}, types);
    }

    @Test
    void testJsonSerialization() throws Exception {
        RemoteInvocation original = new RemoteInvocation(
            "echo",
            new Class<?>[]{String.class},
            new Object[]{"test"}
        );

        String json = mapper.writeValueAsString(original);
        RemoteInvocation deserialized = mapper.readValue(json, RemoteInvocation.class);

        assertEquals(original.methodName(), deserialized.methodName());
        assertArrayEquals(
            original.parameterTypeNames(),
            deserialized.parameterTypeNames()
        );
    }
}
