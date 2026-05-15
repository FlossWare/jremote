package org.flossware.jremote;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import static org.junit.jupiter.api.Assertions.*;

class MultiServiceIntegrationTest {
    private static JRemoteServer server;
    private static Thread serverThread;
    private static final int TEST_PORT = 21000;
    private static final String USERS_SERVICE_ID = "users";
    private static final String ORDERS_SERVICE_ID = "orders";

    @BeforeAll
    static void startServer() throws Exception {
        server = JRemoteServer.builder()
            .register(USERS_SERVICE_ID, TestService.class, new TestServiceImpl())
            .register(ORDERS_SERVICE_ID, OrderService.class, new OrderServiceImpl())
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
    void testMultipleServices() {
        try (JRemoteClient client = new JRemoteClient("localhost", TEST_PORT)) {
            TestService users = client.getProxy(USERS_SERVICE_ID, TestService.class);
            OrderService orders = client.getProxy(ORDERS_SERVICE_ID, OrderService.class);

            String userResult = users.echo("Alice");
            assertEquals("Echo: Alice", userResult);

            String orderResult = orders.createOrder(123);
            assertEquals("Order created: 123", orderResult);
        }
    }

    @Test
    void testServiceIsolation() {
        try (JRemoteClient client = new JRemoteClient("localhost", TEST_PORT)) {
            OrderService orders1 = client.getProxy(ORDERS_SERVICE_ID, OrderService.class);
            OrderService orders2 = client.getProxy(ORDERS_SERVICE_ID, OrderService.class);

            int initialCount = orders1.getOrderCount();

            orders1.createOrder(1);
            orders2.createOrder(2);

            int finalCount = orders1.getOrderCount();

            assertEquals(initialCount + 2, finalCount);
        }
    }

    @Test
    void testConnectionReuse() {
        try (JRemoteClient client = new JRemoteClient("localhost", TEST_PORT, 1, 2)) {
            TestService users = client.getProxy(USERS_SERVICE_ID, TestService.class);
            OrderService orders = client.getProxy(ORDERS_SERVICE_ID, OrderService.class);

            users.echo("First");
            int poolSize1 = client.getPoolSize();

            orders.createOrder(1);
            int poolSize2 = client.getPoolSize();

            users.echo("Second");
            orders.createOrder(2);
            int poolSize3 = client.getPoolSize();

            assertTrue(poolSize1 <= 2);
            assertTrue(poolSize2 <= 2);
            assertTrue(poolSize3 <= 2);
        }
    }

    @Test
    void testMultipleClientsMultipleServices() {
        try (JRemoteClient client1 = new JRemoteClient("localhost", TEST_PORT);
             JRemoteClient client2 = new JRemoteClient("localhost", TEST_PORT)) {

            TestService users1 = client1.getProxy(USERS_SERVICE_ID, TestService.class);
            TestService users2 = client2.getProxy(USERS_SERVICE_ID, TestService.class);

            OrderService orders1 = client1.getProxy(ORDERS_SERVICE_ID, OrderService.class);
            OrderService orders2 = client2.getProxy(ORDERS_SERVICE_ID, OrderService.class);

            assertEquals("Echo: Client1", users1.echo("Client1"));
            assertEquals("Echo: Client2", users2.echo("Client2"));

            orders1.createOrder(100);
            orders2.createOrder(200);

            assertTrue(orders1.getOrderCount() >= 2);
        }
    }

    @Test
    void testInvalidServiceId() {
        try (JRemoteClient client = new JRemoteClient("localhost", TEST_PORT)) {
            TestService invalid = client.getProxy("nonexistent", TestService.class);

            Exception exception = assertThrows(
                Exception.class,
                () -> invalid.echo("test")
            );

            // Could be RemoteException or UndeclaredThrowableException wrapping RemoteException
            Throwable cause = exception;
            if (exception instanceof java.lang.reflect.UndeclaredThrowableException) {
                cause = exception.getCause();
            }

            assertTrue(cause instanceof RemoteException);
            RemoteException remoteEx = (RemoteException) cause;
            assertTrue(remoteEx.getOriginalExceptionType().contains("ServiceNotFoundException"));
            assertTrue(remoteEx.getMessage().contains("nonexistent"));
        }
    }

    @Test
    void testSecurityValidation() {
        try (JRemoteClient client = new JRemoteClient("localhost", TEST_PORT)) {
            TestService users = client.getProxy(USERS_SERVICE_ID, TestService.class);

            RemoteException exception = assertThrows(
                RemoteException.class,
                () -> users.throwsException()
            );

            assertEquals("java.lang.IllegalStateException", exception.getOriginalExceptionType());
            assertEquals("Test exception", exception.getOriginalMessage());
        }
    }

    @Test
    void testBuilderPattern() {
        JRemoteServer testServer = JRemoteServer.builder()
            .register("service1", TestService.class, new TestServiceImpl())
            .register("service2", OrderService.class, new OrderServiceImpl())
            .build();

        assertNotNull(testServer);
    }

    @Test
    void testClientAutoCloseable() {
        JRemoteClient client = new JRemoteClient("localhost", TEST_PORT);
        TestService users = client.getProxy(USERS_SERVICE_ID, TestService.class);

        assertEquals("Echo: Test", users.echo("Test"));

        client.close();

        assertEquals(0, client.getPoolSize());
    }

    @Test
    void testNullObjectId() {
        try (JRemoteClient client = new JRemoteClient("localhost", TEST_PORT)) {
            assertThrows(IllegalArgumentException.class, () ->
                client.getProxy(null, TestService.class)
            );

            assertThrows(IllegalArgumentException.class, () ->
                client.getProxy("", TestService.class)
            );

            assertThrows(IllegalArgumentException.class, () ->
                client.getProxy("  ", TestService.class)
            );
        }
    }

    @Test
    void testProxyObjectMethods() {
        try (JRemoteClient client = new JRemoteClient("localhost", TEST_PORT)) {
            TestService users = client.getProxy(USERS_SERVICE_ID, TestService.class);

            String toString = users.toString();
            assertTrue(toString.contains("TestService"));
            assertTrue(toString.contains(USERS_SERVICE_ID));

            int hashCode = users.hashCode();
            assertNotEquals(0, hashCode);

            assertEquals(users, users);
        }
    }
}
