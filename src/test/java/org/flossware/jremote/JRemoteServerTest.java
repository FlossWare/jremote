package org.flossware.jremote;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

class JRemoteServerTest {
    private JRemoteServer server;
    private TestService implementation;
    private Thread serverThread;
    private static final int TEST_PORT = 19999;

    @BeforeEach
    void setUp() {
        implementation = new TestServiceImpl();
    }

    @AfterEach
    void tearDown() {
        if (serverThread != null) {
            serverThread.interrupt();
        }
    }

    @Test
    void testConstructorValidatesInterface() {
        assertThrows(IllegalArgumentException.class, () ->
            new JRemoteServer(TestServiceImpl.class, implementation)
        );
    }

    @Test
    void testConstructorValidatesImplementation() {
        assertThrows(IllegalArgumentException.class, () ->
            new JRemoteServer(TestService.class, new Object())
        );
    }

    @Test
    void testConstructorAcceptsValidArguments() {
        assertDoesNotThrow(() ->
            new JRemoteServer(TestService.class, implementation)
        );
    }

    @Test
    void testConstructorRejectsNullInterface() {
        assertThrows(IllegalArgumentException.class, () ->
            new JRemoteServer(null, implementation)
        );
    }

    @Test
    void testConstructorRejectsNullImplementation() {
        assertThrows(IllegalArgumentException.class, () ->
            new JRemoteServer(TestService.class, null)
        );
    }

    @Test
    void testDeprecatedConstructor() {
        assertDoesNotThrow(() ->
            new JRemoteServer(implementation)
        );
    }

    @Test
    void testDeprecatedConstructorWithNoInterface() {
        Object noInterface = new Object();
        assertThrows(IllegalArgumentException.class, () ->
            new JRemoteServer(noInterface)
        );
    }

    @Test
    void testServerStartsOnPort() throws Exception {
        server = new JRemoteServer(TestService.class, implementation);
        CountDownLatch latch = new CountDownLatch(1);

        serverThread = Thread.ofVirtual().start(() -> {
            latch.countDown();
            server.start(TEST_PORT);
        });

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        Thread.sleep(500);

        try (var socket = new java.net.Socket("localhost", TEST_PORT)) {
            assertTrue(socket.isConnected());
        }
    }
}
