package org.eu.de.stuntmock.unit;

import static org.eu.de.stuntmock.time.FrozenClock.now;
import static org.eu.de.stuntmock.time.FrozenClock.setNow;
import static org.eu.de.stuntmock.time.FrozenClock.today;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.eu.de.stuntmock.StuntException;
import org.eu.de.stuntmock.StuntExtension;
import org.eu.de.stuntmock.time.RealClock;
import org.eu.de.stuntmock.time.FrozenClock;
import org.eu.de.stuntmock.time.PretendRunningAt;

/**
 * {@link FrozenClock}: the clock is frozen once per class <em>before</em> {@code @BeforeAll} runs, so the moment
 * captured there equals every {@code now()} the tests observe; the {@code Thread.sleep(10)} cannot move it.
 * {@code now()} on every {@code java.time} type, with or without an explicit {@code Clock} or {@code ZoneId},
 * resolves from that single frozen moment. The nested classes cover the class-level {@link PretendRunningAt}
 * and the opt-out.
 */
@ExtendWith({StuntExtension.class, FrozenClock.class})
class FrozenClockTest {

    static ZonedDateTime frozen;
    static Clock clock = Clock.systemUTC();

    @BeforeAll
    static void captureTheFrozenMoment() throws InterruptedException {
        frozen = ZonedDateTime.now();
        Thread.sleep(10);
    }

    /** A zone whose offset differs from the system zone's, so that a conversion visibly changes the hour. */
    private static ZoneId otherZone() {
        ZoneOffset systemOffset = frozen.getOffset();
        return systemOffset.equals(ZoneOffset.ofHoursMinutes(5, 30)) ? ZoneId.of("UTC") : ZoneId.of("Asia/Kolkata");
    }

    // ---------------------------------------------------------------- ZonedDateTime

    @Test
    void zonedDateTimeNowIsFrozen() {
        assertEquals(frozen, ZonedDateTime.now());
        assertEquals(frozen, now());
    }

    @Test
    void zonedDateTimeNowWithClockIsFrozen() {
        assertEquals(frozen, ZonedDateTime.now(clock));
    }

    @Test
    void zonedDateTimeNowInAnotherZoneIsTheSameInstant() {
        ZonedDateTime result = ZonedDateTime.now(otherZone());
        assertEquals(otherZone(), result.getZone());
        assertNotEquals(frozen.getHour(), result.getHour());
        assertEquals(frozen.toInstant(), result.toInstant());
    }

    // ---------------------------------------------------------------- LocalTime

    @Test
    void localTimeNowIsFrozen() {
        assertEquals(frozen.toLocalTime(), LocalTime.now());
    }

    @Test
    void localTimeNowWithClockIsFrozen() {
        assertEquals(frozen.toLocalTime(), LocalTime.now(clock));
    }

    @Test
    void localTimeNowInAnotherZoneIsConverted() {
        LocalTime result = LocalTime.now(otherZone());
        assertNotEquals(frozen.getHour(), result.getHour());
        assertEquals(frozen.withZoneSameInstant(otherZone()).toLocalTime(), result);
    }

    // ---------------------------------------------------------------- Instant

    @Test
    void instantNowIsFrozen() {
        assertEquals(frozen.toInstant(), Instant.now());
    }

    @Test
    void instantNowWithClockIsFrozen() {
        assertEquals(frozen.toInstant(), Instant.now(Clock.systemUTC()));
    }

    // ---------------------------------------------------------------- LocalDate

    @Test
    void localDateNowIsFrozen() {
        assertEquals(frozen.toLocalDate(), LocalDate.now());
        assertEquals(frozen.toLocalDate(), today());
    }

    @Test
    void localDateNowWithClockIsFrozen() {
        assertEquals(frozen.toLocalDate(), LocalDate.now(clock));
    }

    @Test
    void localDateNowInAnotherZoneIsConverted() {
        assertEquals(frozen.withZoneSameInstant(otherZone()).toLocalDate(), LocalDate.now(otherZone()));
    }

    // ---------------------------------------------------------------- LocalDateTime

    @Test
    void localDateTimeNowIsFrozen() {
        assertEquals(frozen.toLocalDateTime(), LocalDateTime.now());
    }

    @Test
    void localDateTimeNowWithClockIsFrozen() {
        assertEquals(frozen.toLocalDateTime(), LocalDateTime.now(clock));
    }

    @Test
    void localDateTimeNowInAnotherZoneIsConverted() {
        LocalDateTime result = LocalDateTime.now(otherZone());
        assertNotEquals(frozen.getHour(), result.getHour());
        assertEquals(frozen.withZoneSameInstant(otherZone()).toLocalDateTime(), result);
    }

    // ---------------------------------------------------------------- the rest of java.time is real

    @Test
    void otherMethodsOfTheFrozenClassesAreReal() {
        assertEquals(LocalDate.of(2000, 6, 16), LocalDate.of(2000, 6, 15).plusDays(1));
        assertEquals(frozen.plusHours(1).toInstant(), ZonedDateTime.now().plusHours(1).toInstant());
    }

    // ---------------------------------------------------------------- method-level @PretendRunningAt

    @Test
    @PretendRunningAt(year = 2022, day = 10)
    void pretendOverridesOnlyTheGivenComponents() {
        assertEquals(2022, ZonedDateTime.now().getYear());
        assertEquals(10, ZonedDateTime.now().getDayOfMonth());
        assertEquals(frozen.getMonthValue(), ZonedDateTime.now().getMonthValue());
        assertEquals(frozen.getHour(), ZonedDateTime.now().getHour());
    }

    @Test
    @PretendRunningAt(year = 2022, month = 2, day = 10, hour = 13, minute = 33)
    void pretendOverridesEveryComponent() {
        assertEquals(2022, ZonedDateTime.now().getYear());
        assertEquals(2, ZonedDateTime.now().getMonthValue());
        assertEquals(10, ZonedDateTime.now().getDayOfMonth());
        assertEquals(13, ZonedDateTime.now().getHour());
        assertEquals(33, ZonedDateTime.now().getMinute());
    }

    /** A pretended month shorter than the base day of month clamps the day instead of failing. */
    @Test
    @PretendRunningAt(month = 2)
    void pretendClampsTheDayToTheMonth() {
        assertEquals(2, ZonedDateTime.now().getMonthValue());
        assertTrue(ZonedDateTime.now().getDayOfMonth() <= 29);
    }

    // ---------------------------------------------------------------- setNow

    @Test
    void setNowWithDateMovesToNoon() {
        setNow(2025, 12, 24);
        assertEquals(LocalDate.of(2025, 12, 24), today());
        assertEquals(LocalDate.of(2025, 12, 24), LocalDate.now());
        assertEquals(12, now().getHour());
        assertEquals(12, LocalTime.now().getHour());
    }

    @Test
    void setNowWithTimeMovesToThatMoment() {
        setNow(2025, 12, 24, 15, 30, 45);
        assertEquals(LocalDate.of(2025, 12, 24), today());
        assertEquals(15, now().getHour());
        assertEquals(30, now().getMinute());
        assertEquals(45, now().getSecond());
        assertEquals(now().toInstant(), Instant.now());
    }

    /** {@code setNow} lasts for the current test only: this test still sees the class base moment. */
    @Test
    void setNowDoesNotLeakIntoOtherTests() {
        assertEquals(frozen, ZonedDateTime.now());
    }

    // ---------------------------------------------------------------- class-level @PretendRunningAt

    /**
     * The class pins June 2000 for every test; a method-level annotation overrides component by component.
     * Components neither level specifies keep the frozen base moment.
     */
    @ExtendWith({StuntExtension.class, FrozenClock.class})
    @PretendRunningAt(year = 2000, month = 6)
    static class ClassLevelPretendTest {

        @Test
        void inheritsClassLevel() {
            assertEquals(2000, ZonedDateTime.now().getYear());
            assertEquals(6, ZonedDateTime.now().getMonthValue());
            assertEquals(2000, LocalDate.now().getYear());
        }

        @Test
        @PretendRunningAt(year = 2030)
        void methodOverridesTheYearAndKeepsTheClassMonth() {
            assertEquals(2030, ZonedDateTime.now().getYear());
            assertEquals(6, ZonedDateTime.now().getMonthValue());
        }

        @Test
        @PretendRunningAt(year = 1999, month = 12, day = 31, hour = 23, minute = 59)
        void methodOverridesEveryComponent() {
            assertEquals(1999, ZonedDateTime.now().getYear());
            assertEquals(12, ZonedDateTime.now().getMonthValue());
            assertEquals(31, ZonedDateTime.now().getDayOfMonth());
            assertEquals(23, ZonedDateTime.now().getHour());
            assertEquals(59, ZonedDateTime.now().getMinute());
        }
    }

    // ---------------------------------------------------------------- opt-out

    /** {@link RealClock} keeps the real, advancing clock; the pretend has no effect. */
    @ExtendWith({StuntExtension.class, FrozenClock.class})
    @RealClock
    @PretendRunningAt(year = 1990)
    static class RealClockTest {

        @Test
        void realClockAdvances() throws InterruptedException {
            Instant first = Instant.now();
            Thread.sleep(10);
            Instant second = Instant.now();
            assertTrue(second.isAfter(first), "expected the real clock to advance, but it appears frozen");
            assertNotEquals(1990, LocalDate.now().getYear());
        }

        @Test
        void frozenClockApiIsUnavailable() {
            StuntException e = assertThrows(StuntException.class, FrozenClock::now);
            assertTrue(e.getMessage().contains("RealClock"), e.getMessage());
        }
    }
}
