package org.flossware.jremote;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.lang.reflect.Method;

/**
 * Server for hosting remote services.
 * Supports multiple services via ServiceRegistry and connection keep-alive.
 */
public class JRemoteServer {
    private static final Logger logger = LoggerFactory.getLogger(JRemoteServer.class);
    private static final ObjectMapper objectMapper = new ObjectMapper()
        .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final String DEFAULT_SERVICE_ID = "default";

    private final ServiceRegistry registry;

    /**
     * Create server with a service registry.
     */
    public JRemoteServer(ServiceRegistry registry) {
        if (registry == null) {
            throw new IllegalArgumentException("ServiceRegistry cannot be null");
        }
        if (registry.size() == 0) {
            throw new IllegalArgumentException("ServiceRegistry must contain at least one service");
        }

        this.registry = registry;
        logger.info("JRemoteServer initialized with {} service(s)", registry.size());
    }

    /**
     * Create server for a single service (backward compatibility).
     * @deprecated Use {@link #builder()} for multi-service support or new JRemoteServer(registry) for single service.
     */
    @Deprecated
    public JRemoteServer(Class<?> serviceInterface, Object implementation) {
        logger.warn("Using deprecated constructor - consider using builder() for multi-service support");

        ServiceRegistry singleServiceRegistry = new ServiceRegistry();
        singleServiceRegistry.register(DEFAULT_SERVICE_ID, serviceInterface, implementation);
        this.registry = singleServiceRegistry;

        logger.info("JRemoteServer initialized for interface: {} (single-service mode)",
                    serviceInterface.getName());
    }

    /**
     * Create server with auto-detected interface (backward compatibility).
     * @deprecated Use {@link #builder()} instead.
     */
    @Deprecated
    public JRemoteServer(Object implementation) {
        logger.warn("Using deprecated single-argument constructor - consider using builder()");

        Class<?>[] interfaces = implementation.getClass().getInterfaces();
        if (interfaces.length == 0) {
            throw new IllegalArgumentException(
                "Implementation must implement at least one interface when using " +
                "single-argument constructor. Use JRemoteServer(Class<?>, Object) instead."
            );
        }

        ServiceRegistry singleServiceRegistry = new ServiceRegistry();
        singleServiceRegistry.register(DEFAULT_SERVICE_ID, interfaces[0], implementation);
        this.registry = singleServiceRegistry;

        logger.warn("Auto-detected interface: {}", interfaces[0].getName());
    }

    /**
     * Create a builder for registering multiple services.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Start the server on the specified port.
     * Handles multiple requests per connection for keep-alive support.
     */
    public void start(int port) {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            logger.info("JRemote Server started on port {} with {} service(s)",
                       port, registry.size());

            while (!Thread.currentThread().isInterrupted()) {
                Socket client = serverSocket.accept();
                logger.debug("Accepted connection from {}", client.getRemoteSocketAddress());

                Thread.ofVirtual().start(() -> handleConnection(client));
            }
        } catch (Exception e) {
            logger.error("Server error on port {}", port, e);
            throw new RuntimeException("Server failed to start", e);
        }
    }

    private void handleConnection(Socket client) {
        BufferedWriter writer = null;
        BufferedReader reader = null;

        try {
            reader = new BufferedReader(new InputStreamReader(client.getInputStream()));
            writer = new BufferedWriter(new OutputStreamWriter(client.getOutputStream()));

            // Keep-alive: process multiple requests on same connection
            while (!Thread.currentThread().isInterrupted()) {
                String requestJson = reader.readLine();
                if (requestJson == null) {
                    logger.debug("Client closed connection");
                    break;
                }

                logger.debug("Received request: {}", requestJson);

                try {
                    RemoteInvocation invocation = objectMapper.readValue(
                        requestJson,
                        RemoteInvocation.class
                    );

                    processInvocation(invocation, writer);

                } catch (Exception e) {
                    logger.error("Error processing request", e);
                    sendErrorResponse(writer, e);
                }
            }

        } catch (Exception e) {
            logger.error("Error handling connection", e);
        } finally {
            closeQuietly(reader);
            closeQuietly(writer);
            closeQuietly(client);
        }
    }

    private void processInvocation(RemoteInvocation invocation, BufferedWriter writer) throws Exception {
        // Get object ID (use default for backward compatibility)
        String objectId = invocation.objectId();
        if (objectId == null || objectId.isBlank()) {
            objectId = DEFAULT_SERVICE_ID;
            logger.debug("No objectId provided, using default service");
        }

        // Lookup service
        ServiceDescriptor service = registry.get(objectId);
        if (service == null) {
            logger.warn("Service not found: {}", objectId);
            RemoteResponse errorResponse = RemoteResponse.failure(
                new RemoteException(
                    "ServiceNotFoundException",
                    "Service with ID '" + objectId + "' not found",
                    new StackTraceElement[0]
                )
            );
            writer.write(objectMapper.writeValueAsString(errorResponse));
            writer.newLine();
            writer.flush();
            return;
        }

        // Validate method
        Class<?>[] paramTypes = invocation.getParameterTypes();
        MethodSignature requestedMethod = new MethodSignature(invocation.methodName(), paramTypes);

        if (!service.allowedMethods().contains(requestedMethod)) {
            logger.warn("Rejected unauthorized method call: {} on service {}",
                       invocation.methodName(), objectId);

            RemoteResponse errorResponse = RemoteResponse.failure(
                new RemoteException(
                    "SecurityException",
                    "Method " + invocation.methodName() + " is not declared in interface " +
                    service.serviceInterface().getName(),
                    new StackTraceElement[0]
                )
            );

            writer.write(objectMapper.writeValueAsString(errorResponse));
            writer.newLine();
            writer.flush();
            return;
        }

        // Invoke method
        Method method = service.serviceInterface().getMethod(
            invocation.methodName(),
            paramTypes
        );

        Object result = method.invoke(service.implementation(), invocation.args());

        // Send success response
        RemoteResponse response = RemoteResponse.success(result, method.getReturnType());
        writer.write(objectMapper.writeValueAsString(response));
        writer.newLine();
        writer.flush();

        logger.debug("Successfully processed method: {} on service {}", invocation.methodName(), objectId);
    }

    private void sendErrorResponse(BufferedWriter writer, Exception e) {
        try {
            Throwable actualException = e;
            if (e instanceof java.lang.reflect.InvocationTargetException ite) {
                actualException = ite.getCause() != null ? ite.getCause() : e;
            }

            RemoteResponse errorResponse = RemoteResponse.failure(
                RemoteException.fromThrowable(actualException)
            );
            writer.write(objectMapper.writeValueAsString(errorResponse));
            writer.newLine();
            writer.flush();
        } catch (Exception writeError) {
            logger.error("Failed to send error response to client", writeError);
        }
    }

    private void closeQuietly(AutoCloseable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception e) {
                logger.debug("Error closing resource", e);
            }
        }
    }

    /**
     * Builder for creating JRemoteServer with multiple services.
     */
    public static class Builder {
        private final ServiceRegistry registry = new ServiceRegistry();

        /**
         * Register a service with a custom ID.
         */
        public Builder register(String id, Class<?> serviceInterface, Object implementation) {
            registry.register(id, serviceInterface, implementation);
            return this;
        }

        /**
         * Register a service with an auto-generated ID.
         * Returns the generated ID.
         */
        public String registerWithGeneratedId(Class<?> serviceInterface, Object implementation) {
            return registry.registerWithGeneratedId(serviceInterface, implementation);
        }

        /**
         * Build the server.
         */
        public JRemoteServer build() {
            return new JRemoteServer(registry);
        }
    }
}
