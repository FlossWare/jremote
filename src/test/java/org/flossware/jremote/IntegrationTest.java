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
        TestService implementation = new TestServiceImpl();
        server = new JRemoteServer(TestService.class, implementation);

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
        TestService client = JRemoteClient.create(
            TestService.class,
            "localhost",
            TEST_PORT
        );

        String result = client.echo("Hello");
        assertEquals("Echo: Hello", result);
    }

    @Test
    void testRemoteAddition() {
        TestService client = JRemoteClient.create(
            TestService.class,
            "localhost",
            TEST_PORT
        );

        int result = client.add(5, 3);
        assertEquals(8, result);
    }

    @Test
    void testVoidMethod() {
        TestService client = JRemoteClient.create(
            TestService.class,
            "localhost",
            TEST_PORT
        );

        assertDoesNotThrow(() -> client.voidMethod());
    }

    @Test
    void testNullReturnValue() {
        TestService client = JRemoteClient.create(
            TestService.class,
            "localhost",
            TEST_PORT
        );

        Object result = client.getNullValue();
        assertNull(result);
    }

    @Test
    void testExceptionPropagation() {
        TestService client = JRemoteClient.create(
            TestService.class,
            "localhost",
            TEST_PORT
        );

        RemoteException exception = assertThrows(
            RemoteException.class,
            () -> client.throwsException()
        );

        assertEquals("java.lang.IllegalStateException", exception.getOriginalExceptionType());
        assertEquals("Test exception", exception.getOriginalMessage());
    }

    @Test
    void testMultipleSequentialCalls() {
        TestService client = JRemoteClient.create(
            TestService.class,
            "localhost",
            TEST_PORT
        );

        assertEquals("Echo: First", client.echo("First"));
        assertEquals("Echo: Second", client.echo("Second"));
        assertEquals(10, client.add(7, 3));
        assertNull(client.getNullValue());
    }

    @Test
    void testClientToStringMethod() {
        TestService client = JRemoteClient.create(
            TestService.class,
            "localhost",
            TEST_PORT
        );

        String toString = client.toString();
        assertTrue(toString.contains("TestService"));
        assertTrue(toString.contains("localhost"));
        assertTrue(toString.contains(String.valueOf(TEST_PORT)));
    }

    @Test
    void testClientHashCodeMethod() {
        TestService client = JRemoteClient.create(
            TestService.class,
            "localhost",
            TEST_PORT
        );

        int hashCode = client.hashCode();
        assertNotEquals(0, hashCode);
    }

    @Test
    void testClientEqualsMethod() {
        TestService client1 = JRemoteClient.create(
            TestService.class,
            "localhost",
            TEST_PORT
        );
        TestService client2 = JRemoteClient.create(
            TestService.class,
            "localhost",
            TEST_PORT
        );

        assertEquals(client1, client1);
        assertNotEquals(client1, client2);
    }
}
