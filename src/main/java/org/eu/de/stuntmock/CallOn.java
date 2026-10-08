package org.eu.de.stuntmock;

import java.io.Serializable;

/**
 * A closure receiving the handle of a declared class and containing exactly one call on it:
 * {@code when(Greeter.class, g -> g.greet(arg.any()))}. Maps the call for every instance of the class.
 *
 * @param <T> the declared class
 */
@FunctionalInterface
public interface CallOn< T > extends Serializable {

    void call(T handle) throws Throwable;
}
