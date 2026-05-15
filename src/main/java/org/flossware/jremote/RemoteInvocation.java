package org.flossware.jremote;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.Arrays;

/**
 * Encapsulates a method call for remote execution.
 * objectId identifies which service instance to invoke on (null for single-service mode).
 */
public record RemoteInvocation(
    String objectId,
    String methodName,
    String[] parameterTypeNames,
    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
    Object[] args
) {
    public RemoteInvocation(String objectId, String methodName, Class<?>[] parameterTypes, Object[] args) {
        this(
            objectId,
            methodName,
            parameterTypes == null ? new String[0] :
                Arrays.stream(parameterTypes)
                    .map(Class::getName)
                    .toArray(String[]::new),
            args
        );
    }

    // Backward-compatible constructor for single-service mode
    public RemoteInvocation(String methodName, Class<?>[] parameterTypes, Object[] args) {
        this(null, methodName, parameterTypes, args);
    }

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