package org.flossware.jremote;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.lang.reflect.Method;
import java.util.function.Supplier;

/**
 * Server for hosting remote services via factory-based instance creation.
 * Supports multiple instances per interface with connection keep-alive.
 */
public class JRemoteServer {
    private static final Logger logger = LoggerFactory.getLogger(JRemoteServer.class);

    private final ServiceRegistry registry;

    /**
     * Create server with a service registry.
     */
    public JRemoteServer(ServiceRegistry registry) {
        if (registry == null) {
            throw new IllegalArgumentException("ServiceRegistry cannot be null");
        }
        if (registry.factoryCount() == 0) {
            throw new IllegalArgumentException("ServiceRegistry must contain at least one factory");
        }

        this.registry = registry;
        logger.info("JRemoteServer initialized with {} factory(ies)", registry.factoryCount());
    }

    /**
     * Create a builder for registering service factories.
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
            logger.info("jremote Server started on port {} with {} factory(ies)",
                       port, registry.factoryCount());

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
        String clientAddress = client.getRemoteSocketAddress().toString();

        try {
            reader = new BufferedReader(new InputStreamReader(client.getInputStream()));
            writer = new BufferedWriter(new OutputStreamWriter(client.getOutputStream()));

            // Keep-alive: process multiple requests on same connection
            while (!Thread.currentThread().isInterrupted()) {
                String requestLine = reader.readLine();
                if (requestLine == null || requestLine.isEmpty()) {
                    logger.debug("Client closed connection: {}", clientAddress);
                    break;
                }

                try {
                    // Auto-detect format from first character (format marker)
                    char formatMarker = requestLine.charAt(0);
                    SerializationFormat format = SerializationFormat.fromMarker((byte) formatMarker);
                    SerializationStrategy strategy = SerializationStrategyFactory.getStrategy(format);

                    // Strip format marker and deserialize
                    String requestData = requestLine.substring(1);
                    logger.debug("Received {} request from {}: {}", format, clientAddress, requestData);

                    RemoteInvocation invocation = strategy.deserialize(requestData, RemoteInvocation.class);

                    processInvocation(invocation, writer, strategy);

                } catch (Exception e) {
                    logger.error("Error processing request from {}", clientAddress, e);
                    // Use JSON for error response (fallback)
                    SerializationStrategy jsonStrategy = SerializationStrategyFactory.getStrategy(SerializationFormat.JSON);
                    sendErrorResponse(writer, e, jsonStrategy);
                }
            }

        } catch (Exception e) {
            logger.error("Error handling connection from {}", clientAddress, e);
        } finally {
            closeQuietly(reader);
            closeQuietly(writer);
            closeQuietly(client);
        }
    }

    private void processInvocation(RemoteInvocation invocation, BufferedWriter writer,
                                   SerializationStrategy strategy) throws Exception {
        switch (invocation.requestType()) {
            case CREATE_INSTANCE -> handleCreateInstance(invocation, writer, strategy);
            case DESTROY_INSTANCE -> handleDestroyInstance(invocation, writer, strategy);
            case METHOD_CALL -> handleMethodCall(invocation, writer, strategy);
        }
    }

    private void handleCreateInstance(RemoteInvocation invocation, BufferedWriter writer,
                                      SerializationStrategy strategy) throws Exception {
        String interfaceName = invocation.interfaceClassName();
        String clientId = invocation.objectId();  // Reused for clientId

        try {
            // Get constructor parameter types
            Class<?>[] paramTypes = invocation.getParameterTypes();

            // Create instance via registry
            String objectId = registry.createInstance(
                interfaceName,
                clientId,
                paramTypes,
                invocation.args()
            );

            // Send success response with objectId
            RemoteResponse response = RemoteResponse.success(objectId, String.class);
            writer.write((char) strategy.getFormat().getMarker());
            writer.write(strategy.serialize(response));
            writer.newLine();
            writer.flush();

            logger.info("Created instance of {} with objectId {} for client {}",
                       interfaceName, objectId, clientId);

        } catch (Exception e) {
            logger.error("Failed to create instance of {}", interfaceName, e);
            sendErrorResponse(writer, e, strategy);
        }
    }

    private void handleDestroyInstance(RemoteInvocation invocation, BufferedWriter writer,
                                       SerializationStrategy strategy) throws Exception {
        String objectId = invocation.objectId();
        String clientId = invocation.interfaceClassName();  // Reused for clientId

        try {
            registry.destroyInstance(objectId, clientId);

            // Send success response
            RemoteResponse response = RemoteResponse.success(null, Void.class);
            writer.write((char) strategy.getFormat().getMarker());
            writer.write(strategy.serialize(response));
            writer.newLine();
            writer.flush();

            logger.info("Destroyed instance {} for client {}", objectId, clientId);

        } catch (Exception e) {
            logger.error("Failed to destroy instance {}", objectId, e);
            sendErrorResponse(writer, e, strategy);
        }
    }

    private void handleMethodCall(RemoteInvocation invocation, BufferedWriter writer,
                                  SerializationStrategy strategy) throws Exception {
        // Get object ID
        String objectId = invocation.objectId();
        if (objectId == null || objectId.isBlank()) {
            logger.warn("METHOD_CALL with null/blank objectId");
            RemoteResponse errorResponse = RemoteResponse.failure(
                new RemoteException(
                    "IllegalArgumentException",
                    "Object ID cannot be null or blank",
                    new StackTraceElement[0]
                )
            );
            writer.write((char) strategy.getFormat().getMarker());
            writer.write(strategy.serialize(errorResponse));
            writer.newLine();
            writer.flush();
            return;
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
            writer.write((char) strategy.getFormat().getMarker());
            writer.write(strategy.serialize(errorResponse));
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

            writer.write((char) strategy.getFormat().getMarker());
            writer.write(strategy.serialize(errorResponse));
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
        writer.write((char) strategy.getFormat().getMarker());
        writer.write(strategy.serialize(response));
        writer.newLine();
        writer.flush();

        logger.debug("Successfully processed method: {} on service {}", invocation.methodName(), objectId);
    }

    private void sendErrorResponse(BufferedWriter writer, Exception e, SerializationStrategy strategy) {
        try {
            Throwable actualException = e;
            if (e instanceof java.lang.reflect.InvocationTargetException ite) {
                actualException = ite.getCause() != null ? ite.getCause() : e;
            }

            RemoteResponse errorResponse = RemoteResponse.failure(
                RemoteException.fromThrowable(actualException)
            );
            writer.write((char) strategy.getFormat().getMarker());
            writer.write(strategy.serialize(errorResponse));
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
     * Builder for creating JRemoteServer with service factories.
     */
    public static class Builder {
        private final ServiceRegistry registry = new ServiceRegistry();

        /**
         * Register a factory using reflection-based instantiation.
         */
        public <T> Builder registerFactory(Class<T> interfaceClass, Class<? extends T> implClass) {
            registry.registerFactory(interfaceClass, implClass);
            return this;
        }

        /**
         * Register a custom factory using a Supplier.
         */
        public <T> Builder registerFactory(Class<T> interfaceClass, Supplier<T> factory) {
            registry.registerFactory(interfaceClass, factory);
            return this;
        }

        /**
         * Build the server.
         */
        public JRemoteServer build() {
            return new JRemoteServer(registry);
        }
    }
}
