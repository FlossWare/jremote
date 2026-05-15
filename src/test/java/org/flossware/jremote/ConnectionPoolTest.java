package org.flossware.jremote;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

class ConnectionPoolTest {
    private ConnectionPool pool;
    private ServerSocket testServer;
    private static final int TEST_PORT = 29999;

    @AfterEach
    void tearDown() throws Exception {
        if (pool != null) {
            pool.close();
        }
        if (testServer != null) {
            testServer.close();
        }
    }

    @Test
    void testCreatePool() {
        pool = new ConnectionPool("localhost", TEST_PORT);

        assertNotNull(pool);
        assertEquals(0, pool.size()); // Min is 1 but connections fail (no server)
    }

    @Test
    void testCreatePoolWithCustomSize() {
        pool = new ConnectionPool("localhost", TEST_PORT, 2, 5);

        assertNotNull(pool);
    }

    @Test
    void testInvalidPoolSizes() {
        assertThrows(IllegalArgumentException.class, () ->
            new ConnectionPool("localhost", TEST_PORT, -1, 10)
        );

        assertThrows(IllegalArgumentException.class, () ->
            new ConnectionPool("localhost", TEST_PORT, 0, 0)
        );

        assertThrows(IllegalArgumentException.class, () ->
            new ConnectionPool("localhost", TEST_PORT, 10, 5)
        );
    }

    @Test
    void testAcquireAndRelease() throws Exception {
        startTestServer();
        pool = new ConnectionPool("localhost", TEST_PORT, 0, 5);

        Socket socket = pool.acquire();
        assertNotNull(socket);
        assertTrue(socket.isConnected());
        assertEquals(1, pool.size());
        assertEquals(0, pool.available());

        pool.release(socket);
        assertEquals(1, pool.size());
        assertEquals(1, pool.available());
    }

    @Test
    void testConnectionReuse() throws Exception {
        startTestServer();
        pool = new ConnectionPool("localhost", TEST_PORT, 0, 5);

        Socket socket1 = pool.acquire();
        pool.release(socket1);

        Socket socket2 = pool.acquire();

        assertSame(socket1, socket2);
        assertEquals(1, pool.size());
    }

    @Test
    void testMultipleAcquire() throws Exception {
        startTestServer();
        pool = new ConnectionPool("localhost", TEST_PORT, 0, 5);

        Socket socket1 = pool.acquire();
        Socket socket2 = pool.acquire();
        Socket socket3 = pool.acquire();

        assertNotNull(socket1);
        assertNotNull(socket2);
        assertNotNull(socket3);
        assertNotSame(socket1, socket2);
        assertNotSame(socket2, socket3);

        assertEquals(3, pool.size());
        assertEquals(0, pool.available());

        pool.release(socket1);
        pool.release(socket2);
        pool.release(socket3);

        assertEquals(3, pool.size());
        assertEquals(3, pool.available());
    }

    @Test
    void testReleaseInvalidConnection() throws Exception {
        startTestServer();
        pool = new ConnectionPool("localhost", TEST_PORT, 0, 5);

        Socket socket = pool.acquire();
        socket.close(); // Make it invalid

        pool.release(socket);

        assertEquals(0, pool.size());
        assertEquals(0, pool.available());
    }

    @Test
    void testReleaseNull() {
        pool = new ConnectionPool("localhost", TEST_PORT, 0, 5);

        assertDoesNotThrow(() -> pool.release(null));
    }

    @Test
    void testClose() throws Exception {
        startTestServer();
        pool = new ConnectionPool("localhost", TEST_PORT, 0, 5);

        Socket socket1 = pool.acquire();
        Socket socket2 = pool.acquire();

        pool.close();

        assertTrue(socket1.isClosed());
        assertTrue(socket2.isClosed());
        assertEquals(0, pool.size());
        assertEquals(0, pool.available());
    }

    @Test
    void testAcquireAfterClose() throws Exception {
        pool = new ConnectionPool("localhost", TEST_PORT, 0, 5);
        pool.close();

        assertThrows(IllegalStateException.class, () -> pool.acquire());
    }

    private void startTestServer() throws IOException {
        testServer = new ServerSocket(TEST_PORT);

        // Accept connections in background
        Thread.ofVirtual().start(() -> {
            try {
                while (!testServer.isClosed()) {
                    Socket client = testServer.accept();
                    // Keep connection open but don't do anything
                }
            } catch (IOException e) {
                // Server closed, expected
            }
        });

        // Give server time to start
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
