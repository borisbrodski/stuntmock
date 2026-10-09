package org.eu.stuntmock.internal;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Type helpers: boxing, default values, formatting. Internal. */
public final class Types {

    private Types() {
    }

    public static Class< ? > box(Class< ? > type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == int.class) {
            return Integer.class;
        }
        if (type == long.class) {
            return Long.class;
        }
        if (type == boolean.class) {
            return Boolean.class;
        }
        if (type == double.class) {
            return Double.class;
        }
        if (type == float.class) {
            return Float.class;
        }
        if (type == short.class) {
            return Short.class;
        }
        if (type == byte.class) {
            return Byte.class;
        }
        if (type == char.class) {
            return Character.class;
        }
        return Void.class;
    }

    /** The value an unmapped call on a {@code @Stub} class returns. */
    public static Object defaultValue(Class< ? > returnType) {
        if (returnType == void.class || returnType == Void.class) {
            return null;
        }
        if (returnType.isPrimitive()) {
            if (returnType == boolean.class) {
                return Boolean.FALSE;
            }
            if (returnType == int.class) {
                return 0;
            }
            if (returnType == long.class) {
                return 0L;
            }
            if (returnType == double.class) {
                return 0d;
            }
            if (returnType == float.class) {
                return 0f;
            }
            if (returnType == short.class) {
                return (short) 0;
            }
            if (returnType == byte.class) {
                return (byte) 0;
            }
            if (returnType == char.class) {
                return '\0';
            }
        }
        if (returnType == List.class || returnType == Collection.class || returnType == Iterable.class) {
            return Collections.emptyList();
        }
        if (returnType == Set.class) {
            return Collections.emptySet();
        }
        if (returnType == Map.class) {
            return Collections.emptyMap();
        }
        if (returnType == Optional.class) {
            return Optional.empty();
        }
        if (returnType == Stream.class) {
            return Stream.empty();
        }
        if (returnType.isArray()) {
            return java.lang.reflect.Array.newInstance(returnType.getComponentType(), 0);
        }
        return null;
    }

    /**
     * Coerces a stub result to the method's return type: numeric widening ({@code thenReturn(1)} on a
     * {@code long} method), or a {@code ClassCastException}-free type check with a readable message.
     */
    public static Object coerce(Object value, Class< ? > returnType, String what) {
        if (returnType == void.class) {
            throw new org.eu.stuntmock.StuntException(
                what + ": the method is void and cannot return " + format(value));
        }
        if (value == null) {
            if (returnType.isPrimitive()) {
                throw new org.eu.stuntmock.StuntException(
                    what + ": null cannot be returned from a method returning " + returnType.getName());
            }
            return null;
        }
        Class< ? > boxed = box(returnType);
        if (boxed.isInstance(value)) {
            return value;
        }
        if (value instanceof Number number && Number.class.isAssignableFrom(boxed)) {
            Object widened = widen(number, boxed);
            if (widened != null) {
                return widened;
            }
        }
        throw new org.eu.stuntmock.StuntException(what + ": " + format(value) + " of type "
            + value.getClass().getName() + " cannot be returned from a method returning " + returnType.getName());
    }

    private static Object widen(Number number, Class< ? > target) {
        boolean integral = number instanceof Integer || number instanceof Long || number instanceof Short
            || number instanceof Byte;
        if (target == Long.class && integral) {
            return number.longValue();
        }
        if (target == Integer.class && (number instanceof Short || number instanceof Byte)) {
            return number.intValue();
        }
        if (target == Short.class && number instanceof Byte) {
            return number.shortValue();
        }
        if (target == Double.class && (integral || number instanceof Float)) {
            return number.doubleValue();
        }
        if (target == Float.class && integral) {
            return number.floatValue();
        }
        return null;
    }

    /** Formats a value for messages; user {@code toString} implementations run outside of dispatch. */
    public static String format(Object value) {
        if (value == null) {
            return "null";
        }
        Scope.enterFramework();
        try {
            return doFormat(value);
        }
        finally {
            Scope.exitFramework();
        }
    }

    private static String doFormat(Object value) {
        if (value instanceof String s) {
            return '"' + s + '"';
        }
        if (value instanceof Character c) {
            return "'" + c + "'";
        }
        if (value instanceof Object[] array) {
            return Arrays.stream(array).map(Types::doFormat).collect(Collectors.joining(", ", "[", "]"));
        }
        if (value.getClass().isArray()) {
            return Arrays.toString(toObjectArray(value));
        }
        if (value instanceof Class< ? > c) {
            return c.getSimpleName() + ".class";
        }
        try {
            return String.valueOf(value);
        }
        catch (RuntimeException e) {
            return identity(value);
        }
    }

    public static String formatArgs(Object[] args) {
        if (args == null) {
            return "";
        }
        return Arrays.stream(args).map(Types::format).collect(Collectors.joining(", "));
    }

    public static String identity(Object value) {
        return value.getClass().getSimpleName() + "@" + Integer.toHexString(System.identityHashCode(value));
    }

    private static Object[] toObjectArray(Object array) {
        int length = java.lang.reflect.Array.getLength(array);
        Object[] result = new Object[length];
        for (int i = 0; i < length; i++) {
            result[i] = java.lang.reflect.Array.get(array, i);
        }
        return result;
    }
}
