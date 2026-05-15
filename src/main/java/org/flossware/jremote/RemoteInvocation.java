package org.flossware.jremote;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.Arrays;

/**
 * Encapsulates a remote invocation request.
 * Supports method calls, instance creation, and instance destruction.
 */
public record RemoteInvocation(
    RequestType requestType,
    String objectId,
    String interfaceClassName,
    String methodName,
    String[] parameterTypeNames,
    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
    Object[] args
) {
    /**
     * Type of remote request.
     */
    public enum RequestType {
        /** Regular method invocation */
        METHOD_CALL,
        /** Create new remote instance */
        CREATE_INSTANCE,
        /** Destroy remote instance */
        DESTROY_INSTANCE
    }

    /**
     * Full constructor with explicit request type.
     */
    public RemoteInvocation {
        // Default requestType to METHOD_CALL if null (JSON deserialization compatibility)
        if (requestType == null) {
            requestType = RequestType.METHOD_CALL;
        }
    }

    /**
     * Create a METHOD_CALL request.
     */
    public static RemoteInvocation methodCall(
            String objectId,
            String methodName,
            Class<?>[] parameterTypes,
            Object[] args) {
        return new RemoteInvocation(
            RequestType.METHOD_CALL,
            objectId,
            null,
            methodName,
            parameterTypes == null ? new String[0] :
                Arrays.stream(parameterTypes)
                    .map(Class::getName)
                    .toArray(String[]::new),
            args
        );
    }

    /**
     * Create a CREATE_INSTANCE request.
     */
    public static RemoteInvocation createInstance(
            String clientId,
            String interfaceClassName,
            Class<?>[] constructorParamTypes,
            Object[] constructorArgs) {
        return new RemoteInvocation(
            RequestType.CREATE_INSTANCE,
            clientId,  // Reuse objectId field for clientId
            interfaceClassName,
            null,
            constructorParamTypes == null ? new String[0] :
                Arrays.stream(constructorParamTypes)
                    .map(Class::getName)
                    .toArray(String[]::new),
            constructorArgs
        );
    }

    /**
     * Create a DESTROY_INSTANCE request.
     */
    public static RemoteInvocation destroyInstance(String clientId, String objectIdToDestroy) {
        return new RemoteInvocation(
            RequestType.DESTROY_INSTANCE,
            objectIdToDestroy,
            clientId,  // Reuse interfaceClassName field for clientId
            null,
            new String[0],
            new Object[0]
        );
    }

    /**
     * Get parameter types as Class[] array.
     * Reconstructs from String[] names.
     */
    public Class<?>[] getParameterTypes() throws ClassNotFoundException {
        if (parameterTypeNames == null || parameterTypeNames.length == 0) {
            return new Class<?>[0];
        }
        Class<?>[] types = new Class<?>[parameterTypeNames.length];
        for (int i = 0; i < parameterTypeNames.length; i++) {
            types[i] = loadClass(parameterTypeNames[i]);
        }
        return types;
    }

    private Class<?> loadClass(String name) throws ClassNotFoundException {
        return switch (name) {
            case "boolean" -> boolean.class;
            case "byte" -> byte.class;
            case "char" -> char.class;
            case "short" -> short.class;
            case "int" -> int.class;
            case "long" -> long.class;
            case "float" -> float.class;
            case "double" -> double.class;
            case "void" -> void.class;
            default -> Class.forName(name);
        };
    }
}
