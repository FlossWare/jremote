package org.flossware.jremote;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for constructor argument support in remote instance creation.
 */
class ConstructorArgsTest {
    private static JRemoteServer server;
    private static Thread serverThread;
    private static final int TEST_PORT = 23000;

    @BeforeAll
    static void startServer() throws Exception {
        server = JRemoteServer.builder()
            .registerFactory(ConfigurableService.class, ConfigurableServiceImpl.class)
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
    void testCreateWithConstructorArguments() {
        try (JRemoteClient client = new JRemoteClient("localhost", TEST_PORT)) {
            ConfigurableService service = client.create(
                ConfigurableService.class,
                "test-config",
                8080
            );

            assertEquals("test-config", service.getConfig());
            assertEquals(8080, service.getPort());
        }
    }

    @Test
    void testCreateMultipleInstancesWithDifferentArgs() {
        try (JRemoteClient client = new JRemoteClient("localhost", TEST_PORT)) {
            ConfigurableService service1 = client.create(
                ConfigurableService.class,
                "config-1",
                8080
            );

            ConfigurableService service2 = client.create(
                ConfigurableService.class,
                "config-2",
                9090
            );

            assertEquals("config-1", service1.getConfig());
            assertEquals(8080, service1.getPort());

            assertEquals("config-2", service2.getConfig());
            assertEquals(9090, service2.getPort());
        }
    }

    @Test
    void testCreateWithPrimitiveArguments() {
        try (JRemoteClient client = new JRemoteClient("localhost", TEST_PORT)) {
            ConfigurableService service = client.create(
                ConfigurableService.class,
                "production",
                5432
            );

            assertNotNull(service);
            assertEquals("production", service.getConfig());
            assertEquals(5432, service.getPort());
        }
    }
}
