package org.flossware.jremote;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for all serialization formats.
 */
class MultiFormatIntegrationTest {
    private static final int TEST_PORT = 21000;
    private static JRemoteServer server;
    private static Thread serverThread;

    @BeforeAll
    static void startServer() {
        server = JRemoteServer.builder()
            .registerFactory(TestService.class, TestServiceImpl.class)
            .build();

        serverThread = Thread.ofVirtual().start(() -> server.start(TEST_PORT));

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @AfterAll
    static void stopServer() {
        if (serverThread != null) {
            serverThread.interrupt();
        }
    }

    @ParameterizedTest
    @EnumSource(SerializationFormat.class)
    void testEchoWithFormat(SerializationFormat format) {
        try (JRemoteClient client = new JRemoteClient("localhost", TEST_PORT, format)) {
            TestService service = client.create(TestService.class);
            String result = service.echo("Hello " + format);
            assertEquals("Echo: Hello " + format, result);
        }
    }

    @ParameterizedTest
    @EnumSource(SerializationFormat.class)
    void testAddWithFormat(SerializationFormat format) {
        try (JRemoteClient client = new JRemoteClient("localhost", TEST_PORT, format)) {
            TestService service = client.create(TestService.class);
            int result = service.add(10, 20);
            assertEquals(30, result);
        }
    }

    @ParameterizedTest
    @EnumSource(SerializationFormat.class)
    void testVoidMethodWithFormat(SerializationFormat format) {
        try (JRemoteClient client = new JRemoteClient("localhost", TEST_PORT, format)) {
            TestService service = client.create(TestService.class);
            assertDoesNotThrow(() -> service.voidMethod());
        }
    }

    @ParameterizedTest
    @EnumSource(SerializationFormat.class)
    void testMultipleInstancesWithFormat(SerializationFormat format) {
        try (JRemoteClient client = new JRemoteClient("localhost", TEST_PORT, format)) {
            TestService service1 = client.create(TestService.class);
            TestService service2 = client.create(TestService.class);

            String result1 = service1.echo("First");
            String result2 = service2.echo("Second");

            assertEquals("Echo: First", result1);
            assertEquals("Echo: Second", result2);
        }
    }
}
