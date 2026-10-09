package org.eu.stuntmock.unit;

import java.lang.reflect.Field;

/** Reads a private field for assertions that must not go through an intercepted getter. */
final class Deencapsulate {

    private Deencapsulate() {
    }

    static Object field(Object target, String name) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        }
        catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
