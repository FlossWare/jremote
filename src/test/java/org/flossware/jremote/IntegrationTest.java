package org.flossware.jremote;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import static org.junit.jupiter.api.Assertions.*;

class IntegrationTest {
    private static JRemoteServer server;
    private static Thread serverThread;
    private static final int TEST_PORT = 20000;

    @BeforeAll
    static void startServer() throws Exception {
        server = JRemoteServer.builder()
            .registerFactory(TestService.class, TestServiceImpl.class)
            .build();

        serverThread = Thread.ofVirtual().start(() ->
            server.start(TEST_PORT)
        );

        Thread.sleep(1000);
    }

    @AfterAll
    static void stopServer() {
        if (serverThread != null) {
            serverThread.interrupt();
        }
    }

    @Test
    void testRemoteMethodInvocation() {
        try (JRemoteClient client = new JRemoteClient("localhost", TEST_PORT)) {
            TestService service = client.create(TestService.class);

            String result = service.echo("Hello");
            assertEquals("Echo: Hello", result);
        }
    }

    @Test
    void testRemoteAddition() {
        try (JRemoteClient client = new JRemoteClient("localhost", TEST_PORT)) {
            TestService service = client.create(TestService.class);

            int result = service.add(5, 3);
            assertEquals(8, result);
        }
    }

    @Test
    void testVoidMethod() {
        try (JRemoteClient client = new JRemoteClient("localhost", TEST_PORT)) {
            TestService service = client.create(TestService.class);

            assertDoesNotThrow(() -> service.voidMethod());
        }
    }

    @Test
    void testNullReturnValue() {
        try (JRemoteClient client = new JRemoteClient("localhost", TEST_PORT)) {
            TestService service = client.create(TestService.class);

            Object result = service.getNullValue();
            assertNull(result);
        }
    }

    @Test
    void testExceptionPropagation() {
        try (JRemoteClient client = new JRemoteClient("localhost", TEST_PORT)) {
            TestService service = client.create(TestService.class);

            RemoteException exception = assertThrows(
                RemoteException.class,
                () -> service.throwsException()
            );

            assertEquals("java.lang.IllegalStateException", exception.getOriginalExceptionType());
            assertEquals("Test exception", exception.getOriginalMessage());
        }
    }

    @Test
    void testMultipleSequentialCalls() {
        try (JRemoteClient client = new JRemoteClient("localhost", TEST_PORT)) {
            TestService service = client.create(TestService.class);

            assertEquals("Echo: First", service.echo("First"));
            assertEquals("Echo: Second", service.echo("Second"));
            assertEquals(10, service.add(7, 3));
            assertNull(service.getNullValue());
        }
    }

    @Test
    void testMultipleInstances() {
        try (JRemoteClient client = new JRemoteClient("localhost", TEST_PORT)) {
            TestService service1 = client.create(TestService.class);
            TestService service2 = client.create(TestService.class);

            assertEquals("Echo: First", service1.echo("First"));
            assertEquals("Echo: Second", service2.echo("Second"));

            // Each instance should work independently
            assertNotNull(service1);
            assertNotNull(service2);
        }
    }

    @Test
    void testExplicitDestroy() {
        try (JRemoteClient client = new JRemoteClient("localhost", TEST_PORT)) {
            TestService service = client.create(TestService.class);

            assertEquals("Echo: Test", service.echo("Test"));

            // Destroy instance
            client.destroy(service);

            // After destruction, the proxy should not be in tracking map
            assertThrows(IllegalArgumentException.class, () -> client.destroy(service));
        }
    }

    @Test
    void testClientToStringMethod() {
        try (JRemoteClient client = new JRemoteClient("localhost", TEST_PORT)) {
            TestService service = client.create(TestService.class);

            String toString = service.toString();
            assertTrue(toString.contains("TestService"));
            assertTrue(toString.contains("proxy"));
        }
    }
}
