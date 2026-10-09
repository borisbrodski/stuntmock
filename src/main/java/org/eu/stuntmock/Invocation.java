package org.eu.stuntmock;

import java.lang.reflect.Method;

/**
 * Everything a {@code thenDo((Invocation inv) -> ...)} closure may look at. The typed-parameter closure forms
 * cover most cases; use this one for the receiver, the original result, or variable-arity handling.
 */
public interface Invocation {

    /** The receiver of the call, or {@code null} for a static method. */
    Object receiver();

    /** The intercepted method. */
    Method method();

    /** The call arguments, boxed. */
    Object[] args();

    /** The n-th argument, cast for convenience. */
    < T > T arg(int index);

    /** Runs the original method body with the intercepted arguments and returns its result. */
    Object callOriginal() throws Throwable;
}
