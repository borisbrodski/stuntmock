package org.eu.de.stuntmock;

import java.io.Serializable;

/**
 * A closure containing exactly one call to map: {@code when(() -> dao.findById(1L))}. The closure runs in
 * capture mode; the call itself is intercepted and never executed. Serializable so that Stunt can inspect the
 * lambda before running it.
 */
@FunctionalInterface
public interface Call extends Serializable {

    void call() throws Throwable;
}
