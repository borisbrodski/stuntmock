package org.eu.stuntmock.unit.fixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.eu.stuntmock.StuntExtension;
import org.eu.stuntmock.time.FrozenClock;

/**
 * Not a test of the suite: a {@code static} nested test class is an ordinary top-level class to JUnit, not a
 * {@code @Nested} one, and a build tool may run it before its lexically enclosing class. Freezing the clock in
 * the nested class must leave nothing behind that stops the enclosing class from freezing its own.
 */
@ExtendWith({StuntExtension.class, FrozenClock.class})
public class StaticNestedClockFixture {

    @Test
    void outerIsFrozen() {
        assertEquals(FrozenClock.now(), ZonedDateTime.now());
    }

    @ExtendWith({StuntExtension.class, FrozenClock.class})
    public static class Standalone {

        @Test
        void standaloneIsFrozen() {
            assertEquals(FrozenClock.now(), ZonedDateTime.now());
        }
    }
}
