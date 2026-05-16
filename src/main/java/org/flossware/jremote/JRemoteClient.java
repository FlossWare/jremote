package org.flossware.jremote;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.Socket;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client for invoking remote services via dynamic proxies.
 * Supports factory-based instance creation with connection pooling.
 */
public class JRemoteClient implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(JRemoteClient.class);

    private final ConnectionPool connectionPool;
    private final String clientId;
    private final Map<Object, String> proxyToObjectId;
    private final SerializationStrategy serializationStrategy;
    private volatile boolean closed = false;

    /**
     * Create client with default connection pool (min=1, max=10) and JSON format.
     */
    public JRemoteClient(String host, int port) {
        this(host, port, 1, 10, SerializationFormat.JSON);
    }

    /**
     * Create client with default connection pool and specified format.
     */
    public JRemoteClient(String host, int port, SerializationFormat format) {
        this(host, port, 1, 10, format);
    }

    /**
     * Create client with custom connection pool configuration and JSON format.
     */
    public JRemoteClient(String host, int port, int minConnections, int maxConnections) {
        this(host, port, minConnections, maxConnections, SerializationFormat.JSON);
    }

    /**
     * Create client with custom connection pool configuration and specified format.
     */
    public JRemoteClient(String host, int port, int minConnections, int maxConnections,
                         SerializationFormat format) {
        this.connectionPool = new ConnectionPool(host, port, minConnections, maxConnections);
        this.clientId = UUID.randomUUID().toString();
        this.proxyToObjectId = new ConcurrentHashMap<>();
        this.serializationStrategy = SerializationStrategyFactory.getStrategy(format);

        logger.info("JRemoteClient created for {}:{} (clientId: {}, format: {})",
                   host, port, clientId, format);
    }

    /**
     * Create a new remote instance using no-arg constructor.
     */
    public <T> T create(Class<T> interfaceClass) {
        return create(interfaceClass, new Object[0]);
    }

    /**
     * Create a new remote instance with constructor arguments.
     */
    public <T> T create(Class<T> interfaceClass, Object... args) {
        if (closed) {
            throw new IllegalStateException("Client is closed");
        }
        if (interfaceClass == null) {
            throw new IllegalArgumentException("Interface class cannot be null");
        }
        if (!interfaceClass.isInterface()) {
            throw new IllegalArgumentException(
                interfaceClass.getName() + " is not an interface");
        }

        Socket socket = null;
        try {
            socket = connectionPool.acquire();
            var writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            var reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Determine constructor parameter types
            Class<?>[] paramTypes = null;
            if (args != null && args.length > 0) {
                paramTypes = Arrays.stream(args)
                    .map(arg -> arg == null ? Object.class : arg.getClass())
                    .toArray(Class<?>[]::new);
            }

            // Send CREATE_INSTANCE request
            RemoteInvocation createRequest = RemoteInvocation.createInstance(
                clientId,
                interfaceClass.getName(),
                paramTypes,
                args
            );

            String requestData = serializationStrategy.serialize(createRequest);
            logger.debug("Sending CREATE_INSTANCE request: {}", requestData);

            writer.write((char) serializationStrategy.getFormat().getMarker());
            writer.write(requestData);
            writer.newLine();
            writer.flush();

            // Receive objectId response
            String responseLine = reader.readLine();
            if (responseLine == null || responseLine.isEmpty()) {
                throw new RemoteException(
                    "ConnectionException",
                    "Connection closed while creating instance",
                    new StackTraceElement[0]
                );
            }

            // Strip format marker and deserialize
            String responseData = responseLine.substring(1);
            logger.debug("Received response: {}", responseData);

            RemoteResponse response = serializationStrategy.deserialize(responseData, RemoteResponse.class);

            if (!response.isSuccess()) {
                throw response.error();
            }

            String objectId = (String) response.result();

            // Create proxy with this objectId
            T proxy = createProxy(objectId, interfaceClass);

            // Track for cleanup
            proxyToObjectId.put(proxy, objectId);

            logger.info("Created remote instance of {} with objectId {}",
                       interfaceClass.getName(), objectId);

            return proxy;

        } catch (RemoteException e) {
            logger.error("Remote exception while creating instance", e);
            if (socket != null) {
                closeQuietly(socket);
                socket = null;  // Don't return to pool
            }
            throw e;
        } catch (Exception e) {
            logger.error("Error creating remote instance", e);
            if (socket != null) {
                closeQuietly(socket);
                socket = null;  // Don't return to pool
            }
            throw new RemoteException(
                e.getClass().getName(),
                "Failed to create remote instance: " + e.getMessage(),
                e.getStackTrace()
            );
        } finally {
            if (socket != null) {
                connectionPool.release(socket);
            }
        }
    }

    /**
     * Destroy a remote instance.
     * The proxy should not be used after this call.
     */
    public void destroy(Object proxy) {
        if (closed) {
            logger.warn("Attempting to destroy proxy after client closed");
            return;
        }

        String objectId = proxyToObjectId.remove(proxy);
        if (objectId == null) {
            throw new IllegalArgumentException(
                "Proxy not managed by this client or already destroyed");
        }

        Socket socket = null;
        try {
            socket = connectionPool.acquire();
            var writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            var reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Send DESTROY_INSTANCE request
            RemoteInvocation destroyRequest = RemoteInvocation.destroyInstance(clientId, objectId);

            String requestData = serializationStrategy.serialize(destroyRequest);
            logger.debug("Sending DESTROY_INSTANCE request: {}", requestData);

            writer.write((char) serializationStrategy.getFormat().getMarker());
            writer.write(requestData);
            writer.newLine();
            writer.flush();

            // Read response
            String responseLine = reader.readLine();
            if (responseLine != null && !responseLine.isEmpty()) {
                String responseData = responseLine.substring(1);
                RemoteResponse response = serializationStrategy.deserialize(responseData, RemoteResponse.class);
                if (!response.isSuccess()) {
                    logger.warn("Failed to destroy instance {}: {}",
                               objectId, response.error().getMessage());
                }
            }

            logger.info("Destroyed remote instance {}", objectId);

        } catch (Exception e) {
            logger.warn("Error destroying instance " + objectId, e);
            if (socket != null) {
                closeQuietly(socket);
                socket = null;
            }
        } finally {
            if (socket != null) {
                connectionPool.release(socket);
            }
        }
    }

    /**
     * Close the client and destroy all remote instances.
     */
    @Override
    public void close() {
        if (closed) {
            return;
        }

        closed = true;
        logger.info("Closing JRemoteClient (clientId: {})", clientId);

        // Destroy all tracked instances
        Set<String> instanceIds = new HashSet<>(proxyToObjectId.values());
        for (String objectId : instanceIds) {
            try {
                Socket socket = connectionPool.acquire();
                var writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

                RemoteInvocation destroyRequest = RemoteInvocation.destroyInstance(clientId, objectId);
                String requestData = serializationStrategy.serialize(destroyRequest);

                writer.write((char) serializationStrategy.getFormat().getMarker());
                writer.write(requestData);
                writer.newLine();
                writer.flush();

                connectionPool.release(socket);

                logger.debug("Destroyed instance {} during close", objectId);

            } catch (Exception e) {
                logger.warn("Failed to destroy instance " + objectId + " during close", e);
            }
        }

        proxyToObjectId.clear();
        connectionPool.close();

        logger.info("JRemoteClient closed");
    }

    /**
     * Create a dynamic proxy for a remote service instance.
     */
    private <T> T createProxy(String objectId, Class<T> interfaceClass) {
        InvocationHandler handler = new RemoteInvocationHandler(objectId, interfaceClass);

        @SuppressWarnings("unchecked")
        T proxy = (T) Proxy.newProxyInstance(
            interfaceClass.getClassLoader(),
            new Class<?>[] { interfaceClass },
            handler
        );

        return proxy;
    }

    /**
     * InvocationHandler for proxying method calls to remote service.
     */
    private class RemoteInvocationHandler implements InvocationHandler {
        private final String objectId;
        private final Class<?> interfaceClass;

        public RemoteInvocationHandler(String objectId, Class<?> interfaceClass) {
            this.objectId = objectId;
            this.interfaceClass = interfaceClass;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            // Handle Object methods locally
            if (method.getDeclaringClass() == Object.class) {
                return handleObjectMethod(proxy, method, args);
            }

            Socket socket = null;
            try {
                socket = connectionPool.acquire();
                var writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
                var reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                // Create method call request
                RemoteInvocation invocation = RemoteInvocation.methodCall(
                    objectId,
                    method.getName(),
                    method.getParameterTypes(),
                    args
                );

                String requestData = serializationStrategy.serialize(invocation);
                logger.debug("Sending method call: {}", requestData);

                writer.write((char) serializationStrategy.getFormat().getMarker());
                writer.write(requestData);
                writer.newLine();
                writer.flush();

                // Read response
                String responseLine = reader.readLine();
                if (responseLine == null || responseLine.isEmpty()) {
                    throw new RemoteException(
                        "ConnectionException",
                        "Connection closed by server",
                        new StackTraceElement[0]
                    );
                }

                // Strip format marker and deserialize
                String responseData = responseLine.substring(1);
                logger.debug("Received response: {}", responseData);

                RemoteResponse response = serializationStrategy.deserialize(responseData, RemoteResponse.class);

                if (response.isSuccess()) {
                    return response.result();
                } else {
                    throw response.error();
                }

            } catch (RemoteException e) {
                logger.error("Remote exception during method call: {}", method.getName(), e);
                if (socket != null) {
                    closeQuietly(socket);
                    socket = null;
                }
                throw e;
            } catch (Exception e) {
                logger.error("Error during remote method call: {}", method.getName(), e);
                if (socket != null) {
                    closeQuietly(socket);
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

        private Object handleObjectMethod(Object proxy, Method method, Object[] args) {
            return switch (method.getName()) {
                case "toString" -> String.format(
                    "%s proxy [objectId=%s, clientId=%s]",
                    interfaceClass.getSimpleName(),
                    objectId,
                    clientId
                );
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> throw new UnsupportedOperationException(
                    "Unsupported Object method: " + method.getName()
                );
            };
        }
    }

    private void closeQuietly(Socket socket) {
        if (socket != null) {
            try {
                socket.close();
            } catch (Exception e) {
                logger.debug("Error closing socket", e);
            }
        }
    }
}
