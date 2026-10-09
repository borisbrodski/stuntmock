package org.eu.stuntmock.internal;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** JVM method descriptors and lookup of methods by name and descriptor across a hierarchy. Internal. */
public final class Descriptors {

    private Descriptors() {
    }

    public static String of(Method method) {
        StringBuilder sb = new StringBuilder("(");
        for (Class< ? > p : method.getParameterTypes()) {
            sb.append(of(p));
        }
        return sb.append(')').append(of(method.getReturnType())).toString();
    }

    public static String of(Class< ? > type) {
        if (type == void.class) {
            return "V";
        }
        if (type.isPrimitive()) {
            if (type == int.class) {
                return "I";
            }
            if (type == long.class) {
                return "J";
            }
            if (type == boolean.class) {
                return "Z";
            }
            if (type == double.class) {
                return "D";
            }
            if (type == float.class) {
                return "F";
            }
            if (type == short.class) {
                return "S";
            }
            if (type == byte.class) {
                return "B";
            }
            return "C";
        }
        if (type.isArray()) {
            return "[" + of(type.getComponentType());
        }
        return "L" + type.getName().replace('.', '/') + ";";
    }

    /** Finds the method with the given name and descriptor in {@code type}, its superclasses or its interfaces. */
    public static Method find(Class< ? > type, String name, String descriptor) {
        for (Class< ? > c : linearize(type)) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals(name) && !m.isBridge() && of(m).equals(descriptor)) {
                    return m;
                }
            }
        }
        return null;
    }

    /** Every non-synthetic method of {@code type}, its superclasses and interfaces; overrides counted once. */
    public static List< Method > allMethods(Class< ? > type) {
        List< Method > result = new ArrayList<>();
        for (Class< ? > c : linearize(type)) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.isBridge() || m.isSynthetic()) {
                    continue;
                }
                boolean overridden = false;
                for (Method seen : result) {
                    if (seen.getName().equals(m.getName()) && of(seen).equals(of(m))) {
                        overridden = true;
                        break;
                    }
                }
                if (!overridden) {
                    result.add(m);
                }
            }
        }
        return result;
    }

    /** All non-bridge methods with the given name in {@code type}, its superclasses or its interfaces. */
    public static List< Method > findAll(Class< ? > type, String name) {
        List< Method > result = new ArrayList<>();
        for (Class< ? > c : linearize(type)) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals(name) && !m.isBridge() && !m.isSynthetic()) {
                    boolean overridden = false;
                    for (Method seen : result) {
                        if (of(seen).equals(of(m))) {
                            overridden = true;
                            break;
                        }
                    }
                    if (!overridden) {
                        result.add(m);
                    }
                }
            }
        }
        return result;
    }

    private static List< Class< ? > > linearize(Class< ? > type) {
        List< Class< ? > > result = new ArrayList<>();
        Class< ? > current = type;
        while (current != null) {
            result.add(current);
            for (Class< ? > iface : current.getInterfaces()) {
                addInterfaces(iface, result);
            }
            current = current.getSuperclass();
        }
        if (type.isInterface()) {
            result.add(Object.class);
        }
        return result;
    }

    private static void addInterfaces(Class< ? > iface, List< Class< ? > > result) {
        if (result.contains(iface)) {
            return;
        }
        result.add(iface);
        for (Class< ? > parent : iface.getInterfaces()) {
            addInterfaces(parent, result);
        }
    }

    /** Human readable form: {@code Greeter.setGreeting(String)}. */
    public static String pretty(Method method) {
        StringBuilder sb = new StringBuilder(method.getDeclaringClass().getSimpleName()).append('.')
            .append(method.getName()).append('(');
        Class< ? >[] params = method.getParameterTypes();
        for (int i = 0; i < params.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(params[i].getSimpleName());
        }
        return sb.append(')').toString();
    }
}
