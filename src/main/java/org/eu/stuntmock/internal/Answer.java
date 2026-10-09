package org.eu.stuntmock.internal;

import java.util.Collection;

/** What a step does when it answers a call. Internal. */
public interface Answer {

    /** Marker result: run the original method body. */
    Object PROCEED = new Object() {
        @Override
        public String toString() {
            return "PROCEED";
        }
    };

    /** Marker result: the mode's default behaviour for an unmapped call. */
    Object MODE_DEFAULT = new Object() {
        @Override
        public String toString() {
            return "MODE_DEFAULT";
        }
    };

    /** Returns the value to return, {@link #PROCEED}, {@link #MODE_DEFAULT}, or throws. */
    Object answer(InvocationImpl invocation, Chain.Step step) throws Throwable;

    String describe();

    // ---------------------------------------------------------------- implementations

    Answer DEFAULT = new Answer() {
        @Override
        public Object answer(InvocationImpl invocation, Chain.Step step) {
            return MODE_DEFAULT;
        }

        @Override
        public String describe() {
            return "default behaviour";
        }
    };

    Answer PROCEED_ANSWER = new Answer() {
        @Override
        public Object answer(InvocationImpl invocation, Chain.Step step) {
            return PROCEED;
        }

        @Override
        public String describe() {
            return "thenCallOriginal()";
        }
    };

    Answer NOTHING = new Answer() {
        @Override
        public Object answer(InvocationImpl invocation, Chain.Step step) {
            return Types.defaultValue(invocation.method().getReturnType());
        }

        @Override
        public String describe() {
            return "thenDoNothing()";
        }
    };

    static Answer returning(Object value) {
        return new Answer() {
            @Override
            public Object answer(InvocationImpl invocation, Chain.Step step) {
                return value;
            }

            @Override
            public String describe() {
                return "thenReturn(" + Types.format(value) + ")";
            }
        };
    }

    static Answer throwing(Throwable throwable) {
        return new Answer() {
            @Override
            public Object answer(InvocationImpl invocation, Chain.Step step) throws Throwable {
                throw throwable;
            }

            @Override
            public String describe() {
                return "thenThrow(" + throwable.getClass().getSimpleName()
                    + (throwable.getMessage() == null ? "" : ": " + throwable.getMessage()) + ")";
            }
        };
    }

    static Answer sequence(Iterable< ? > values, Class< ? > returnType) {
        return new Sequence(values, returnType);
    }

    /** A {@code thenReturns(Iterable)} step: one value per call until the iterable is exhausted. */
    final class Sequence implements Answer {

        private final Iterable< ? > values;
        private final Class< ? > returnType;

        Sequence(Iterable< ? > values, Class< ? > returnType) {
            this.values = values;
            this.returnType = returnType;
        }

        Iterable< ? > values() {
            return values;
        }

        int size() {
            return values instanceof Collection< ? > c ? c.size() : -1;
        }

        @Override
        public Object answer(InvocationImpl invocation, Chain.Step step) {
            return Types.coerce(step.next(), returnType, "thenReturns(...)");
        }

        @Override
        public String describe() {
            return "thenReturns(" + (size() < 0 ? "lazy iterable" : Types.format(values)) + ")";
        }
    }

    /** A {@code thenDo(closure)} step. */
    final class Doing implements Answer {

        public interface Body {
            Object call(InvocationImpl invocation) throws Throwable;
        }

        private final Body body;
        private final String description;

        public Doing(Body body, String description) {
            this.body = body;
            this.description = description;
        }

        @Override
        public Object answer(InvocationImpl invocation, Chain.Step step) throws Throwable {
            return body.call(invocation);
        }

        @Override
        public String describe() {
            return description;
        }
    }
}
