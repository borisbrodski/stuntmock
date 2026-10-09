package org.eu.stuntmock.internal;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The classes whose method bodies can execute for an instance of a declared class: the class, its superclasses
 * and their interfaces (for default methods). JDK types are excluded unless the declared type itself is one, so
 * that declaring an entity never instruments {@code java.lang.Object} or {@code java.util.AbstractList}.
 * Internal.
 */
final class Hierarchy {

    private Hierarchy() {
    }

    static List< Class< ? > > of(Class< ? > type) {
        Set< Class< ? > > result = new LinkedHashSet<>();
        if (isJdk(type)) {
            result.add(type);
            return new ArrayList<>(result);
        }
        Class< ? > current = type;
        while (current != null && current != Object.class && !isJdk(current)) {
            result.add(current);
            for (Class< ? > iface : current.getInterfaces()) {
                addInterface(iface, result);
            }
            current = current.getSuperclass();
        }
        return new ArrayList<>(result);
    }

    private static void addInterface(Class< ? > iface, Set< Class< ? > > result) {
        if (isJdk(iface) || result.contains(iface)) {
            return;
        }
        result.add(iface);
        for (Class< ? > parent : iface.getInterfaces()) {
            addInterface(parent, result);
        }
    }

    static boolean isJdk(Class< ? > type) {
        String name = type.getName();
        return name.startsWith("java.") || name.startsWith("javax.") || name.startsWith("jdk.")
            || name.startsWith("sun.") || name.startsWith("com.sun.");
    }
}
