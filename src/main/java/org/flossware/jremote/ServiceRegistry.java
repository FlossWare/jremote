package org.flossware.jremote;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Thread-safe registry for managing multiple service implementations.
 * Supports builder-style registration of services with unique IDs.
 */
public class ServiceRegistry {
    private static final Logger logger = LoggerFactory.getLogger(ServiceRegistry.class);
    private final Map<String, ServiceDescriptor> services = new ConcurrentHashMap<>();

    /**
     * Register a service with a custom ID.
     */
    public ServiceRegistry register(String id, Class<?> serviceInterface, Object implementation) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Service ID cannot be null or blank");
        }
        if (serviceInterface == null) {
            throw new IllegalArgumentException("Service interface cannot be null");
        }
        if (implementation == null) {
            throw new IllegalArgumentException("Implementation cannot be null");
        }
        if (services.containsKey(id)) {
            throw new IllegalArgumentException("Service with ID '" + id + "' is already registered");
        }

        Set<MethodSignature> allowedMethods = buildAllowedMethods(serviceInterface);
        ServiceDescriptor descriptor = new ServiceDescriptor(
            id,
            serviceInterface,
            implementation,
            allowedMethods
        );

        services.put(id, descriptor);
        logger.info("Registered service '{}' for interface {}", id, serviceInterface.getName());

        return this;
    }

    /**
     * Register a service with an auto-generated UUID.
     */
    public String registerWithGeneratedId(Class<?> serviceInterface, Object implementation) {
        String id = generateId();
        register(id, serviceInterface, implementation);
        return id;
    }

    /**
     * Get a registered service by ID.
     */
    public ServiceDescriptor get(String id) {
        return services.get(id);
    }

    /**
     * Check if a service is registered.
     */
    public boolean contains(String id) {
        return services.containsKey(id);
    }

    /**
     * Get the number of registered services.
     */
    public int size() {
        return services.size();
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
