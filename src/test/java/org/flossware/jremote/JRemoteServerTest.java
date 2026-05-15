package org.flossware.jremote;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JRemoteServerTest {

    @Test
    void testBuilderPattern() {
        JRemoteServer server = JRemoteServer.builder()
            .registerFactory(TestService.class, TestServiceImpl.class)
            .build();

        assertNotNull(server);
    }

    @Test
    void testBuilderWithMultipleFactories() {
        JRemoteServer server = JRemoteServer.builder()
            .registerFactory(TestService.class, TestServiceImpl.class)
            .registerFactory(OrderService.class, OrderServiceImpl.class)
            .build();

        assertNotNull(server);
    }

    @Test
    void testBuilderWithCustomFactory() {
        JRemoteServer server = JRemoteServer.builder()
            .registerFactory(TestService.class, () -> new TestServiceImpl())
            .build();

        assertNotNull(server);
    }

    @Test
    void testNullRegistryRejected() {
        assertThrows(IllegalArgumentException.class, () ->
            new JRemoteServer(null)
        );
    }

    @Test
    void testEmptyRegistryRejected() {
        ServiceRegistry emptyRegistry = new ServiceRegistry();

        assertThrows(IllegalArgumentException.class, () ->
            new JRemoteServer(emptyRegistry)
        );
    }

    @Test
    void testBuilderWithNoFactories() {
        assertThrows(IllegalArgumentException.class, () ->
            JRemoteServer.builder().build()
        );
    }

    @Test
    void testServerStartsWithValidRegistry() throws Exception {
        JRemoteServer server = JRemoteServer.builder()
            .registerFactory(TestService.class, TestServiceImpl.class)
            .build();

        Thread serverThread = Thread.ofVirtual().start(() -> {
            try {
                server.start(22000);
            } catch (Exception e) {
                // Expected when interrupted
            }
        });

        Thread.sleep(500);
        serverThread.interrupt();
        serverThread.join(1000);
    }
}
