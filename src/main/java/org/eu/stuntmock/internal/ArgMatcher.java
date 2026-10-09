package org.eu.stuntmock.internal;

import java.util.Objects;
import java.util.function.Predicate;

import org.eu.stuntmock.Captor;

/** One argument matcher of a mapped call. Internal. */
public abstract class ArgMatcher {

    /** The type this matcher accepts, if it restricts one; used to pick among overloads. May be {@code null}. */
    private final Class< ? > type;

    protected ArgMatcher(Class< ? > type) {
        this.type = type;
    }

    public abstract boolean matches(Object actual);

    public abstract String describe();

    public Class< ? > type() {
        return type;
    }

    @Override
    public String toString() {
        return describe();
    }

    // ---------------------------------------------------------------- factories

    public static ArgMatcher any(Class< ? > type) {
        return new ArgMatcher(type) {
            @Override
            public boolean matches(Object actual) {
                return type == null || actual == null || Types.box(type).isInstance(actual);
            }

            @Override
            public String describe() {
                return type == null ? "any()" : "any(" + type.getSimpleName() + ")";
            }
        };
    }

    public static ArgMatcher eq(Object expected) {
        return new ArgMatcher(expected == null ? null : expected.getClass()) {
            @Override
            public boolean matches(Object actual) {
                return Objects.deepEquals(expected, actual);
            }

            @Override
            public String describe() {
                return Types.format(expected);
            }
        };
    }

    public static ArgMatcher same(Object expected) {
        return new ArgMatcher(expected == null ? null : expected.getClass()) {
            @Override
            public boolean matches(Object actual) {
                return expected == actual;
            }

            @Override
            public String describe() {
                return "same(" + Types.format(expected) + ")";
            }
        };
    }

    public static ArgMatcher isNull() {
        return new ArgMatcher(null) {
            @Override
            public boolean matches(Object actual) {
                return actual == null;
            }

            @Override
            public String describe() {
                return "isNull()";
            }
        };
    }

    public static ArgMatcher notNull() {
        return new ArgMatcher(null) {
            @Override
            public boolean matches(Object actual) {
                return actual != null;
            }

            @Override
            public String describe() {
                return "notNull()";
            }
        };
    }

    public static < T > ArgMatcher argThat(Class< ? > type, Predicate< T > predicate, String description) {
        return new ArgMatcher(type) {
            @Override
            @SuppressWarnings("unchecked")
            public boolean matches(Object actual) {
                if (actual == null && type != null && type.isPrimitive()) {
                    return false;
                }
                try {
                    return predicate.test((T) actual);
                }
                catch (ClassCastException e) {
                    return false;
                }
            }

            @Override
            public String describe() {
                return description;
            }
        };
    }

    public static ArgMatcher capturing(Captor< ? > captor) {
        return new ArgMatcher(captor.type()) {
            @Override
            public boolean matches(Object actual) {
                return actual == null || captor.type().isInstance(actual);
            }

            @Override
            public String describe() {
                return captor.toString();
            }

            @Override
            public void onMatched(Object actual) {
                captor.record(actual);
            }
        };
    }

    /** Hook invoked once a whole call matched, for captors. */
    public void onMatched(Object actual) {
    }
}
