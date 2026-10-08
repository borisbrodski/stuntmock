package org.eu.de.stuntmock.time;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Opts a test class out of {@link FrozenClock}: its tests see the real, advancing system clock, and any
 * {@link PretendRunningAt} on the class or its methods has no effect.
 */
@Documented
@Retention(RUNTIME)
@Target(TYPE)
public @interface RealClock {
}
