package org.flossware.jremote;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.util.function.Supplier;

/**
 * Factory for creating remote service instances.
 * Supports both reflection-based instantiation and custom factory functions.
 */
public class InstanceFactory<T> {
    private static final Logger logger = LoggerFactory.getLogger(InstanceFactory.class);

    private final Class<T> interfaceClass;
    private final Class<? extends T> implClass;
    private final Supplier<T> customFactory;

    /**
     * Create a reflection-based factory.
     * Supports both no-arg and parameterized constructors.
     */
    public InstanceFactory(Class<T> interfaceClass, Class<? extends T> implClass) {
        if (interfaceClass == null) {
            throw new IllegalArgumentException("Interface class cannot be null");
        }
        if (implClass == null) {
            throw new IllegalArgumentException("Implementation class cannot be null");
        }
        if (!interfaceClass.isInterface()) {
            throw new IllegalArgumentException(interfaceClass.getName() + " is not an interface");
        }
        if (!interfaceClass.isAssignableFrom(implClass)) {
            throw new IllegalArgumentException(
                implClass.getName() + " does not implement " + interfaceClass.getName());
        }

        this.interfaceClass = interfaceClass;
        this.implClass = implClass;
        this.customFactory = null;

        logger.debug("Created reflection-based factory for {} -> {}",
                    interfaceClass.getName(), implClass.getName());
    }

    /**
     * Create a custom factory using a Supplier.
     * Does not support constructor arguments.
     */
    public InstanceFactory(Class<T> interfaceClass, Supplier<T> factory) {
        if (interfaceClass == null) {
            throw new IllegalArgumentException("Interface class cannot be null");
        }
        if (factory == null) {
            throw new IllegalArgumentException("Factory supplier cannot be null");
        }
        if (!interfaceClass.isInterface()) {
            throw new IllegalArgumentException(interfaceClass.getName() + " is not an interface");
        }

        this.interfaceClass = interfaceClass;
        this.implClass = null;
        this.customFactory = factory;

        logger.debug("Created custom factory for {}", interfaceClass.getName());
    }

    /**
     * Create an instance using the no-arg constructor.
     */
    public T createInstance() {
        if (customFactory != null) {
            logger.debug("Creating instance via custom factory");
            return customFactory.get();
        }

        try {
            logger.debug("Creating instance of {} via reflection (no-arg constructor)",
                        implClass.getName());
            Constructor<? extends T> constructor = implClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(
                "No-arg constructor not found for " + implClass.getName() +
                ". Use create() with constructor arguments or a custom factory.", e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to instantiate " + implClass.getName(), e);
        }
    }

    /**
     * Create an instance using a parameterized constructor.
     * Only works with reflection-based factories.
     */
    public T createInstance(Class<?>[] paramTypes, Object[] args) {
        if (customFactory != null) {
            throw new UnsupportedOperationException(
                "Custom factories do not support constructor arguments. " +
                "Use createInstance() without arguments or switch to a reflection-based factory.");
        }

        if (paramTypes == null) {
            paramTypes = new Class<?>[0];
        }
        if (args == null) {
            args = new Object[0];
        }
        if (paramTypes.length != args.length) {
            throw new IllegalArgumentException(
                "Parameter types count (" + paramTypes.length +
                ") does not match arguments count (" + args.length + ")");
        }

        try {
            logger.debug("Creating instance of {} via reflection (parameterized constructor)",
                        implClass.getName());
            Constructor<? extends T> constructor = implClass.getDeclaredConstructor(paramTypes);
            constructor.setAccessible(true);
            return constructor.newInstance(args);
        } catch (NoSuchMethodException e) {
            StringBuilder sig = new StringBuilder(implClass.getName()).append("(");
            for (int i = 0; i < paramTypes.length; i++) {
                if (i > 0) sig.append(", ");
                sig.append(paramTypes[i].getSimpleName());
            }
            sig.append(")");
            throw new RuntimeException(
                "Constructor not found: " + sig +
                ". Check that the implementation class has a matching constructor.", e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to instantiate " + implClass.getName(), e);
        }
    }

    /**
     * Get the service interface class.
     */
    public Class<T> getInterfaceClass() {
        return interfaceClass;
    }
}
