package org.flossware.jremote;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Thread-safe registry for managing service factories and instances.
 * Supports factory-based instance creation with client ownership tracking.
 */
public class ServiceRegistry {
    private static final Logger logger = LoggerFactory.getLogger(ServiceRegistry.class);

    private final Map<String, ServiceDescriptor> instances = new ConcurrentHashMap<>();
    private final Map<String, InstanceFactory<?>> factories = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> clientInstances = new ConcurrentHashMap<>();

    /**
     * Register a factory using reflection-based instantiation.
     * Supports both no-arg and parameterized constructors.
     */
    public <T> ServiceRegistry registerFactory(Class<T> interfaceClass, Class<? extends T> implClass) {
        if (interfaceClass == null) {
            throw new IllegalArgumentException("Interface class cannot be null");
        }
        if (implClass == null) {
            throw new IllegalArgumentException("Implementation class cannot be null");
        }

        String interfaceName = interfaceClass.getName();
        if (factories.containsKey(interfaceName)) {
            throw new IllegalArgumentException(
                "Factory for interface '" + interfaceName + "' is already registered");
        }

        InstanceFactory<T> factory = new InstanceFactory<>(interfaceClass, implClass);
        factories.put(interfaceName, factory);

        logger.info("Registered factory for interface {} -> {}",
                   interfaceName, implClass.getName());

        return this;
    }

    /**
     * Register a custom factory using a Supplier.
     * Does not support constructor arguments.
     */
    public <T> ServiceRegistry registerFactory(Class<T> interfaceClass, Supplier<T> factorySupplier) {
        if (interfaceClass == null) {
            throw new IllegalArgumentException("Interface class cannot be null");
        }
        if (factorySupplier == null) {
            throw new IllegalArgumentException("Factory supplier cannot be null");
        }

        String interfaceName = interfaceClass.getName();
        if (factories.containsKey(interfaceName)) {
            throw new IllegalArgumentException(
                "Factory for interface '" + interfaceName + "' is already registered");
        }

        InstanceFactory<T> factory = new InstanceFactory<>(interfaceClass, factorySupplier);
        factories.put(interfaceName, factory);

        logger.info("Registered custom factory for interface {}", interfaceName);

        return this;
    }

    /**
     * Create a new instance of a registered service.
     * Returns the generated objectId for the instance.
     */
    public String createInstance(String interfaceName, String clientId,
                                 Class<?>[] paramTypes, Object[] args) {
        if (interfaceName == null || interfaceName.isBlank()) {
            throw new IllegalArgumentException("Interface name cannot be null or blank");
        }
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalArgumentException("Client ID cannot be null or blank");
        }

        InstanceFactory<?> factory = factories.get(interfaceName);
        if (factory == null) {
            throw new IllegalArgumentException(
                "No factory registered for interface: " + interfaceName);
        }

        // Create instance (with or without constructor args)
        Object instance;
        if (paramTypes == null || paramTypes.length == 0) {
            instance = factory.createInstance();
        } else {
            instance = factory.createInstance(paramTypes, args);
        }

        // Generate unique objectId
        String objectId = generateId();

        // Build allowed methods from interface
        Set<MethodSignature> allowedMethods = buildAllowedMethods(factory.getInterfaceClass());

        // Register as service instance
        ServiceDescriptor descriptor = new ServiceDescriptor(
            objectId,
            factory.getInterfaceClass(),
            instance,
            allowedMethods
        );
        instances.put(objectId, descriptor);

        // Track client ownership
        clientInstances.computeIfAbsent(clientId, k -> ConcurrentHashMap.newKeySet())
                      .add(objectId);

        logger.info("Created instance of {} with objectId {} for client {}",
                   interfaceName, objectId, clientId);

        return objectId;
    }

    /**
     * Destroy a service instance.
     * Validates client ownership before destruction.
     */
    public void destroyInstance(String objectId, String clientId) {
        if (objectId == null || objectId.isBlank()) {
            throw new IllegalArgumentException("Object ID cannot be null or blank");
        }
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalArgumentException("Client ID cannot be null or blank");
        }

        // Validate ownership
        Set<String> ownedInstances = clientInstances.get(clientId);
        if (ownedInstances == null || !ownedInstances.contains(objectId)) {
            throw new SecurityException(
                "Client " + clientId + " does not own instance " + objectId);
        }

        // Remove from registry
        ServiceDescriptor removed = instances.remove(objectId);
        ownedInstances.remove(objectId);

        if (removed != null) {
            logger.info("Destroyed instance {} (interface: {}) for client {}",
                       objectId, removed.serviceInterface().getName(), clientId);
        } else {
            logger.warn("Attempted to destroy non-existent instance: {}", objectId);
        }
    }

    /**
     * Clean up all instances owned by a client.
     * Typically called when client disconnects.
     */
    public void cleanupClient(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            return;
        }

        Set<String> ownedInstances = clientInstances.remove(clientId);
        if (ownedInstances != null && !ownedInstances.isEmpty()) {
            logger.info("Cleaning up {} instance(s) for client {}",
                       ownedInstances.size(), clientId);
            ownedInstances.forEach(instances::remove);
        }
    }

    /**
     * Get a registered service instance by ID.
     */
    public ServiceDescriptor get(String objectId) {
        return instances.get(objectId);
    }

    /**
     * Check if an instance is registered.
     */
    public boolean contains(String objectId) {
        return instances.containsKey(objectId);
    }

    /**
     * Get the number of registered instances.
     */
    public int size() {
        return instances.size();
    }

    /**
     * Get the number of registered factories.
     */
    public int factoryCount() {
        return factories.size();
    }

    /**
     * Generate a unique service ID.
     */
    public String generateId() {
        return UUID.randomUUID().toString();
    }

    private Set<MethodSignature> buildAllowedMethods(Class<?> serviceInterface) {
        return Arrays.stream(serviceInterface.getMethods())
            .map(MethodSignature::new)
            .collect(Collectors.toSet());
    }
}
