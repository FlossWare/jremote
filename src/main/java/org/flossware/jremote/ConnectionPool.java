package org.flossware.jremote;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.Socket;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Thread-safe connection pool for reusing Socket connections.
 * Manages a pool of connections with configurable min/max size.
 */
public class ConnectionPool implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(ConnectionPool.class);
    private static final int DEFAULT_MIN_CONNECTIONS = 1;
    private static final int DEFAULT_MAX_CONNECTIONS = 10;
    private static final long ACQUIRE_TIMEOUT_MS = 5000;

    private final BlockingQueue<Socket> availableConnections;
    private final Set<Socket> allConnections;
    private final String host;
    private final int port;
    private final int minSize;
    private final int maxSize;
    private volatile boolean closed = false;

    public ConnectionPool(String host, int port) {
        this(host, port, DEFAULT_MIN_CONNECTIONS, DEFAULT_MAX_CONNECTIONS);
    }

    public ConnectionPool(String host, int port, int minSize, int maxSize) {
        if (minSize < 0 || maxSize < 1 || minSize > maxSize) {
            throw new IllegalArgumentException(
                "Invalid pool size: min=" + minSize + ", max=" + maxSize);
        }

        this.host = host;
        this.port = port;
        this.minSize = minSize;
        this.maxSize = maxSize;
        this.availableConnections = new ArrayBlockingQueue<>(maxSize);
        this.allConnections = ConcurrentHashMap.newKeySet();

        logger.info("Created connection pool for {}:{} (min={}, max={})",
            host, port, minSize, maxSize);

        // Pre-create minimum connections
        for (int i = 0; i < minSize; i++) {
            try {
                Socket socket = createConnection();
                availableConnections.offer(socket);
                allConnections.add(socket);
            } catch (IOException e) {
                logger.warn("Failed to pre-create connection {}/{}", i + 1, minSize, e);
            }
        }
    }

    /**
     * Acquire a connection from the pool.
     * Blocks if pool is exhausted until a connection becomes available.
     */
    public Socket acquire() throws IOException {
        if (closed) {
            throw new IllegalStateException("Connection pool is closed");
        }

        Socket socket = null;

        // Try to get from pool
        while (socket == null) {
            socket = availableConnections.poll();

            if (socket != null) {
                // Validate connection
                if (isValid(socket)) {
                    logger.debug("Acquired connection from pool: {}", socket);
                    return socket;
                } else {
                    logger.debug("Removed invalid connection from pool");
                    allConnections.remove(socket);
                    closeQuietly(socket);
                    socket = null;
                }
            } else {
                // Pool is empty, try to create new connection
                synchronized (this) {
                    if (allConnections.size() < maxSize) {
                        socket = createConnection();
                        allConnections.add(socket);
                        logger.debug("Created new connection: {} (total: {})",
                            socket, allConnections.size());
                        return socket;
                    }
                }

                // Pool exhausted, wait for connection to be released
                try {
                    logger.debug("Pool exhausted, waiting for available connection...");
                    socket = availableConnections.poll(ACQUIRE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                    if (socket == null) {
                        throw new IOException("Timeout waiting for connection from pool");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while waiting for connection", e);
                }
            }
        }

        return socket;
    }

    /**
     * Release a connection back to the pool.
     * Invalid connections are discarded.
     */
    public void release(Socket socket) {
        if (socket == null) {
            return;
        }

        if (closed) {
            closeQuietly(socket);
            allConnections.remove(socket);
            return;
        }

        if (isValid(socket)) {
            boolean offered = availableConnections.offer(socket);
            if (offered) {
                logger.debug("Released connection back to pool: {}", socket);
            } else {
                logger.debug("Pool full, closing excess connection");
                closeQuietly(socket);
                allConnections.remove(socket);
            }
        } else {
            logger.debug("Released invalid connection, discarding");
            closeQuietly(socket);
            allConnections.remove(socket);
        }
    }

    /**
     * Close all connections and shut down the pool.
     */
    @Override
    public void close() {
        if (closed) {
            return;
        }

        closed = true;
        logger.info("Closing connection pool for {}:{}", host, port);

        for (Socket socket : allConnections) {
            closeQuietly(socket);
        }

        availableConnections.clear();
        allConnections.clear();
        logger.info("Connection pool closed");
    }

    /**
     * Get the current number of connections in the pool.
     */
    public int size() {
        return allConnections.size();
    }

    /**
     * Get the number of available connections.
     */
    public int available() {
        return availableConnections.size();
    }

    private Socket createConnection() throws IOException {
        try {
            Socket socket = new Socket(host, port);
            socket.setKeepAlive(true);
            socket.setTcpNoDelay(true);
            logger.debug("Created new connection to {}:{}", host, port);
            return socket;
        } catch (IOException e) {
            logger.error("Failed to create connection to {}:{}", host, port, e);
            throw e;
        }
    }

    private boolean isValid(Socket socket) {
        return socket != null &&
               !socket.isClosed() &&
               socket.isConnected() &&
               !socket.isInputShutdown() &&
               !socket.isOutputShutdown();
    }

    private void closeQuietly(Socket socket) {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException e) {
                logger.debug("Error closing socket", e);
            }
        }
    }
}
