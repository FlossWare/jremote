package org.flossware.jremote;

import java.util.Set;

/**
 * Describes a registered service with its interface, implementation, and allowed methods.
 * Immutable value object used by ServiceRegistry.
 */
public record ServiceDescriptor(
    String id,
    Class<?> serviceInterface,
    Object implementation,
    Set<MethodSignature> allowedMethods
) {
    public ServiceDescriptor {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Service ID cannot be null or blank");
        }
        if (serviceInterface == null) {
            throw new IllegalArgumentException("Service interface cannot be null");
        }
        if (allowedMethods == null) {
            throw new IllegalArgumentException("Allowed methods cannot be null");
        }
        if (implementation == null) {
            throw new IllegalArgumentException("Implementation cannot be null");
        }
        if (!serviceInterface.isInterface()) {
            throw new IllegalArgumentException(
                serviceInterface.getName() + " is not an interface"
            );
        }
        if (!serviceInterface.isInstance(implementation)) {
            throw new IllegalArgumentException(
                "Implementation does not implement " + serviceInterface.getName()
            );
        }
        if (allowedMethods.isEmpty()) {
            throw new IllegalArgumentException("Allowed methods cannot be empty");
        }
    }
}
