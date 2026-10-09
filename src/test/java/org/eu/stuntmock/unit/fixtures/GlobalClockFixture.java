package org.eu.stuntmock.unit.fixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.eu.stuntmock.StuntExtension;
import org.eu.stuntmock.time.FrozenClock;
import org.eu.stuntmock.time.PretendRunningAt;

/**
 * Not a test of the suite: run with {@code StuntSettings.freezeClock(true)} by {@code InitializationTest}.
 * No {@code FrozenClock} registered here, yet the clock is frozen; registering it as well does not freeze twice.
 */
@ExtendWith(StuntExtension.class)
@PretendRunningAt(year = 1999)
public class GlobalClockFixture {

    @Test
    void frozenWithoutRegisteringTheExtension() throws InterruptedException {
        ZonedDateTime first = ZonedDateTime.now();
        Thread.sleep(5);
        assertEquals(first, ZonedDateTime.now());
        assertEquals(1999, LocalDate.now().getYear());
        assertEquals(first, FrozenClock.now());
    }

    @ExtendWith({StuntExtension.class, FrozenClock.class})
    public static class AlsoRegisteredExplicitly {

        @Test
        void frozenOnce() throws InterruptedException {
            ZonedDateTime first = ZonedDateTime.now();
            Thread.sleep(5);
            assertEquals(first, ZonedDateTime.now());
            assertTrue(FrozenClock.now().isAfter(ZonedDateTime.now().minusSeconds(1)));
        }
    }
}
