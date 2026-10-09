package org.eu.stuntmock.time;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Pins the frozen clock of {@link FrozenClock} to a moment. On the test class it applies to every test; on a
 * test method it overrides the class-level annotation component by component. A component left at {@code -1}
 * keeps the value of the frozen base moment.
 *
 * <pre>{@code
 * @PretendRunningAt(year = 2000, month = 6)     // class level: every test runs in June 2000
 * class MillenniumTest {
 *
 *     @Test
 *     @PretendRunningAt(year = 2030)             // this test: year 2030, still June
 *     void methodOverridesClass() { ... }
 * }
 * }</pre>
 */
@Documented
@Retention(RUNTIME)
@Target({METHOD, TYPE})
public @interface PretendRunningAt {

    int year() default -1;

    int month() default -1;

    int day() default -1;

    int hour() default -1;

    int minute() default -1;
}
