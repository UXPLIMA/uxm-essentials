package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Placeholder contexts in which every seam is wired and every text it answers is the same {@code text}: a name, a
 * display name, a prefix, an item's name and lore. Written once over the seams' own types, so a seam or a key added
 * later is covered without this class changing. Numbers answer 1, yes-or-no answers yes, and a type it cannot make
 * answers nothing, which leaves the key it feeds out of the check rather than failing it.
 */
final class EverySeam {

    private final String text;

    private EverySeam(String text) {
        this.text = text;
    }

    /** Contexts with every seam answering {@code text} wherever it answers text. */
    static PlaceholderContexts answering(String text) {
        EverySeam fakes = new EverySeam(text);
        PlaceholderContexts.Builder builder = PlaceholderContexts.builder();
        for (Method method : PlaceholderContexts.Builder.class.getMethods()) {
            if (method.getDeclaringClass() != PlaceholderContexts.Builder.class
                    || method.getParameterCount() != 1
                    || !method.getParameterTypes()[0].isInterface()) {
                continue;
            }
            try {
                method.invoke(builder, fakes.seam(method.getParameterTypes()[0]));
            } catch (ReflectiveOperationException unwired) {
                throw new IllegalStateException("could not wire " + method.getName(), unwired);
            }
        }
        return builder.build();
    }

    private Object seam(Class<?> type) {
        InvocationHandler answers = (proxy, method, arguments) -> {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "equals" -> proxy == arguments[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    default -> type.getSimpleName() + " answering " + text;
                };
            }
            // A default answers only when no value can be made, so a seam's own text is what a key reads.
            Object made = value(method.getGenericReturnType());
            if (made == null && method.isDefault()) {
                return InvocationHandler.invokeDefault(proxy, method, arguments);
            }
            return made;
        };
        return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, answers);
    }

    private @Nullable Object value(Type type) {
        if (type instanceof ParameterizedType generic) {
            Class<?> raw = (Class<?>) generic.getRawType();
            Type inner = generic.getActualTypeArguments()[0];
            if (raw == Optional.class) {
                return Optional.ofNullable(value(inner));
            }
            if (raw == List.class || raw == Collection.class) {
                Object one = value(inner);
                return one == null ? List.of() : List.of(one);
            }
            if (raw == Set.class) {
                Object one = value(inner);
                return one == null ? Set.of() : Set.of(one);
            }
            if (raw == Map.class) {
                return Map.of();
            }
            return null;
        }
        if (!(type instanceof Class<?> plain)) {
            return null;
        }
        if (plain == String.class || plain == CharSequence.class) {
            return text;
        }
        if (plain == boolean.class || plain == Boolean.class) {
            return true;
        }
        if (plain == int.class || plain == Integer.class) {
            return 1;
        }
        if (plain == long.class || plain == Long.class) {
            return 1L;
        }
        if (plain == double.class || plain == Double.class) {
            return 1.0;
        }
        if (plain == float.class || plain == Float.class) {
            return 1.0f;
        }
        if (plain == OptionalInt.class) {
            return OptionalInt.of(1);
        }
        if (plain == OptionalLong.class) {
            return OptionalLong.of(1);
        }
        if (plain == OptionalDouble.class) {
            return OptionalDouble.of(1);
        }
        if (plain == UUID.class) {
            return new UUID(0L, 1L);
        }
        if (plain == Duration.class) {
            return Duration.ofSeconds(1);
        }
        if (plain == Instant.class) {
            return Instant.EPOCH;
        }
        if (plain == BigDecimal.class) {
            return BigDecimal.ONE;
        }
        if (plain.isEnum()) {
            Object[] constants = plain.getEnumConstants();
            return constants.length == 0 ? null : constants[0];
        }
        if (plain.isRecord()) {
            return record(plain);
        }
        if (plain == double[].class) {
            return new double[] {1.0, 1.0, 1.0};
        }
        if (plain == int[].class) {
            return new int[] {1, 1, 1};
        }
        if (plain == long[].class) {
            return new long[] {1L, 1L, 1L};
        }
        if (plain.isInterface() && !plain.isSealed()) {
            return seam(plain);
        }
        return null;
    }

    private @Nullable Object record(Class<?> type) {
        RecordComponent[] components = type.getRecordComponents();
        Class<?>[] types = new Class<?>[components.length];
        Object[] values = new Object[components.length];
        for (int i = 0; i < components.length; i++) {
            types[i] = components[i].getType();
            values[i] = value(components[i].getGenericType());
            if (values[i] == null) {
                return null;
            }
        }
        try {
            return type.getDeclaredConstructor(types).newInstance(values);
        } catch (ReflectiveOperationException | RuntimeException refused) {
            return null;
        }
    }
}
