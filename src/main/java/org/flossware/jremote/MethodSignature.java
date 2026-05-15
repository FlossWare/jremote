package org.flossware.jremote;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Represents a method signature for validation purposes.
 * Immutable value object that can be used as a map key.
 */
public class MethodSignature {
    private final String name;
    private final Set<String> parameterTypes;

    public MethodSignature(Method method) {
        this.name = method.getName();
        this.parameterTypes = Arrays.stream(method.getParameterTypes())
            .map(Class::getName)
            .collect(Collectors.toSet());
    }

    public MethodSignature(String name, Class<?>[] paramTypes) {
        this.name = name;
        this.parameterTypes = paramTypes == null ? Set.of() :
            Arrays.stream(paramTypes)
                .map(Class::getName)
                .collect(Collectors.toSet());
    }

    public String getName() {
        return name;
    }

    public Set<String> getParameterTypes() {
        return parameterTypes;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof MethodSignature other)) return false;
        return name.equals(other.name) &&
               parameterTypes.equals(other.parameterTypes);
    }

    @Override
    public int hashCode() {
        return name.hashCode() * 31 + parameterTypes.hashCode();
    }

    @Override
    public String toString() {
        return name + "(" + String.join(", ", parameterTypes) + ")";
    }
}
