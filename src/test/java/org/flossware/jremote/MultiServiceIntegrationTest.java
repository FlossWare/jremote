package org.flossware.jremote;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import static org.junit.jupiter.api.Assertions.*;

class MultiServiceIntegrationTest {
    private static JRemoteServer server;
    private static Thread serverThread;
    private static final int TEST_PORT = 21000;

    @BeforeAll
    static void startServer() throws Exception {
        server = JRemoteServer.builder()
            .registerFactory(TestService.class, TestServiceImpl.class)
            .registerFactory(OrderService.class, OrderServiceImpl.class)
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
    void testMultipleServiceTypes() {
        try (JRemoteClient client = new JRemoteClient("localhost", TEST_PORT)) {
            TestService testService = client.create(TestService.class);
            OrderService orderService = client.create(OrderService.class);

            assertEquals("Echo: Test", testService.echo("Test"));
            orderService.createOrder(123);
            assertEquals(1, orderService.getOrderCount());
        }
    }

    @Test
    void testMultipleInstancesOfSameService() {
        try (JRemoteClient client = new JRemoteClient("localhost", TEST_PORT)) {
            OrderService order1 = client.create(OrderService.class);
            OrderService order2 = client.create(OrderService.class);

            order1.createOrder(1);
            order2.createOrder(2);

            // Each instance should have its own state
            assertEquals(1, order1.getOrderCount());
            assertEquals(1, order2.getOrderCount());
        }
    }

    @Test
    void testServiceIsolation() {
        try (JRemoteClient client = new JRemoteClient("localhost", TEST_PORT)) {
            TestService testService = client.create(TestService.class);
            OrderService orderService = client.create(OrderService.class);

            // Verify they are independent
            assertNotNull(testService);
            assertNotNull(orderService);

            // Each should work correctly
            assertEquals("Echo: Hello", testService.echo("Hello"));
            orderService.createOrder(100);
        }
    }

    @Test
    void testConcurrentClients() throws Exception {
        Thread client1 = Thread.ofVirtual().start(() -> {
            try (JRemoteClient client = new JRemoteClient("localhost", TEST_PORT)) {
                TestService service = client.create(TestService.class);
                for (int i = 0; i < 10; i++) {
                    service.echo("Client1-" + i);
                }
            }
        });

        Thread client2 = Thread.ofVirtual().start(() -> {
            try (JRemoteClient client = new JRemoteClient("localhost", TEST_PORT)) {
                OrderService service = client.create(OrderService.class);
                for (int i = 0; i < 10; i++) {
                    service.createOrder(i);
                }
            }
        });

        client1.join();
        client2.join();
    }

    @Test
    void testAutoCleanupOnClose() {
        try (JRemoteClient client = new JRemoteClient("localhost", TEST_PORT)) {
            TestService service1 = client.create(TestService.class);
            TestService service2 = client.create(TestService.class);

            assertEquals("Echo: Test", service1.echo("Test"));
            assertEquals("Echo: Test", service2.echo("Test"));

            // Both instances will be auto-destroyed on close
        }
    }

    @Test
    void testDestroySpecificInstance() {
        try (JRemoteClient client = new JRemoteClient("localhost", TEST_PORT)) {
            TestService service1 = client.create(TestService.class);
            TestService service2 = client.create(TestService.class);

            assertEquals("Echo: First", service1.echo("First"));
            assertEquals("Echo: Second", service2.echo("Second"));

            // Destroy only service1
            client.destroy(service1);

            // service2 should still work
            assertEquals("Echo: Still works", service2.echo("Still works"));

            // service1 should not be destroyable again
            assertThrows(IllegalArgumentException.class, () -> client.destroy(service1));
        }
    }
}
