package org.flossware.jremote;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.Proxy;
import java.net.Socket;

/**
 * Client for invoking remote services.
 * Supports both connection pooling (instance API) and single-use connections (deprecated static API).
 */
public class JRemoteClient implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(JRemoteClient.class);
    private static final ObjectMapper objectMapper = new ObjectMapper()
        .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final ConnectionPool connectionPool;

    /**
     * Create a client with connection pooling (default pool size).
     */
    public JRemoteClient(String host, int port) {
        this.connectionPool = new ConnectionPool(host, port);
        logger.info("Created JRemoteClient with connection pooling for {}:{}", host, port);
    }

    /**
     * Create a client with connection pooling (custom pool size).
     */
    public JRemoteClient(String host, int port, int minConnections, int maxConnections) {
        this.connectionPool = new ConnectionPool(host, port, minConnections, maxConnections);
        logger.info("Created JRemoteClient with connection pooling for {}:{} (min={}, max={})",
            host, port, minConnections, maxConnections);
    }

    /**
     * Get a proxy for a remote service identified by objectId.
     */
    @SuppressWarnings("unchecked")
    public <T> T getProxy(String objectId, Class<T> interfaceClass) {
        if (objectId == null || objectId.isBlank()) {
            throw new IllegalArgumentException("objectId cannot be null or blank");
        }
        if (interfaceClass == null) {
            throw new IllegalArgumentException("interfaceClass cannot be null");
        }

        logger.debug("Creating proxy for service '{}' with interface {}",
            objectId, interfaceClass.getName());

        return (T) Proxy.newProxyInstance(
            interfaceClass.getClassLoader(),
            new Class<?>[]{interfaceClass},
            (proxy, method, args) -> {
                if (method.getDeclaringClass() == Object.class) {
                    return switch (method.getName()) {
                        case "toString" ->
                            interfaceClass.getName() + "@" + objectId;
                        case "hashCode" ->
                            (interfaceClass.getName() + objectId).hashCode();
                        case "equals" -> proxy == args[0];
                        default -> null;
                    };
                }

                Socket socket = null;
                try {
                    socket = connectionPool.acquire();

                    var writer = new BufferedWriter(
                        new OutputStreamWriter(socket.getOutputStream()));
                    var reader = new BufferedReader(
                        new InputStreamReader(socket.getInputStream()));

                    RemoteInvocation invocation = new RemoteInvocation(
                        objectId,
                        method.getName(),
                        method.getParameterTypes(),
                        args
                    );

                    String requestJson = objectMapper.writeValueAsString(invocation);
                    logger.debug("Sending request to {}: {}", objectId, requestJson);

                    writer.write(requestJson);
                    writer.newLine();
                    writer.flush();

                    String responseJson = reader.readLine();
                    if (responseJson == null) {
                        throw new RemoteException(
                            "ConnectionException",
                            "Server closed connection",
                            new StackTraceElement[0]
                        );
                    }

                    logger.debug("Received response: {}", responseJson);

                    RemoteResponse response = objectMapper.readValue(
                        responseJson,
                        RemoteResponse.class
                    );

                    if (response.isSuccess()) {
                        Object result = response.result();

                        Class<?> returnType = method.getReturnType();
                        if (result == null || returnType.isInstance(result)) {
                            return result;
                        }

                        return objectMapper.convertValue(result, returnType);
                    } else {
                        throw response.error();
                    }

                } catch (RemoteException e) {
                    logger.error("Remote method invocation failed for {}", objectId, e);
                    // Connection may be bad, don't return to pool
                    if (socket != null) {
                        try {
                            socket.close();
                        } catch (Exception ignore) {}
                        socket = null;
                    }
                    throw e;
                } catch (Exception e) {
                    logger.error("Client communication error for {}", objectId, e);
                    // Connection may be bad, don't return to pool
                    if (socket != null) {
                        try {
                            socket.close();
                        } catch (Exception ignore) {}
                        socket = null;
                    }
                    throw new RemoteException(
                        e.getClass().getName(),
                        e.getMessage(),
                        e.getStackTrace()
                    );
                } finally {
                    if (socket != null) {
                        connectionPool.release(socket);
                    }
                }
            }
        );
    }

    /**
     * Close the connection pool and all connections.
     */
    @Override
    public void close() {
        connectionPool.close();
    }

    /**
     * Get the connection pool size.
     */
    public int getPoolSize() {
        return connectionPool.size();
    }

    /**
     * Get the number of available connections.
     */
    public int getAvailableConnections() {
        return connectionPool.available();
    }

    /**
     * Create a proxy for a remote service (single-use connection, no pooling).
     * @deprecated Use the instance-based API with {@link #JRemoteClient(String, int)} and {@link #getProxy(String, Class)} instead.
     */
    @Deprecated
    @SuppressWarnings("unchecked")
    public static <T> T create(Class<T> interfaceClass, String host, int port) {
        logger.warn("Using deprecated static create() method - consider using instance-based API with connection pooling");

        return (T) Proxy.newProxyInstance(
            interfaceClass.getClassLoader(),
            new Class<?>[]{interfaceClass},
            (proxy, method, args) -> {
                if (method.getDeclaringClass() == Object.class) {
                    return switch (method.getName()) {
                        case "toString" ->
                            interfaceClass.getName() + "@" + host + ":" + port;
                        case "hashCode" ->
                            (interfaceClass.getName() + host + port).hashCode();
                        case "equals" -> proxy == args[0];
                        default -> null;
                    };
                }

                try (Socket socket = new Socket(host, port);
                     var writer = new BufferedWriter(
                         new OutputStreamWriter(socket.getOutputStream()));
                     var reader = new BufferedReader(
                         new InputStreamReader(socket.getInputStream()))) {

                    RemoteInvocation invocation = new RemoteInvocation(
                        method.getName(),
                        method.getParameterTypes(),
                        args
                    );

                    String requestJson = objectMapper.writeValueAsString(invocation);
                    logger.debug("Sending request: {}", requestJson);

                    writer.write(requestJson);
                    writer.newLine();
                    writer.flush();

                    String responseJson = reader.readLine();
                    logger.debug("Received response: {}", responseJson);

                    RemoteResponse response = objectMapper.readValue(
                        responseJson,
                        RemoteResponse.class
                    );

                    if (response.isSuccess()) {
                        Object result = response.result();

                        Class<?> returnType = method.getReturnType();
                        if (result == null || returnType.isInstance(result)) {
                            return result;
                        }

                        return objectMapper.convertValue(result, returnType);
                    } else {
                        throw response.error();
                    }

                } catch (RemoteException e) {
                    logger.error("Remote method invocation failed", e);
                    throw e;
                } catch (Exception e) {
                    logger.error("Client communication error", e);
                    throw new RemoteException(
                        e.getClass().getName(),
                        e.getMessage(),
                        e.getStackTrace()
                    );
                }
            }
        );
    }
}
