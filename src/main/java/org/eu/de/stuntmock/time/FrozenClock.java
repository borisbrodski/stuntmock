package org.eu.de.stuntmock.time;

import static org.eu.de.stuntmock.Stunt.arg;
import static org.eu.de.stuntmock.StuntSettings.infrastructure;
import static org.eu.de.stuntmock.Stunt.stubPartially;
import static org.eu.de.stuntmock.Stunt.when;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import org.eu.de.stuntmock.StuntException;
import org.eu.de.stuntmock.StuntExtension;
import org.eu.de.stuntmock.internal.Scope;

/**
 * Freezes the {@code java.time} clock for a test class. Every {@code now()} of {@link ZonedDateTime},
 * {@link LocalDate}, {@link LocalDateTime}, {@link LocalTime} and {@link Instant}, with or without a
 * {@link Clock} or {@link ZoneId} argument, derives from one pinned moment, so a test never observes the clock
 * advancing between two reads. Everything else on those classes stays real.
 *
 * <p>Two ways to turn it on: for every test class at once with {@code StuntSettings.freezeClock(true)} from an
 * initialization block (then {@code StuntExtension} drives it, nothing to register per class), or per class by
 * registering it after {@link StuntExtension}:
 * <pre>{@code
 * @ExtendWith({StuntExtension.class, FrozenClock.class})
 * class OrderTest { }
 * }</pre>
 * Off by default.
 *
 * <p>The base moment is the real time at the start of the class, truncated to milliseconds, with a class-level
 * {@link PretendRunningAt} applied. It is pinned before any {@code @BeforeAll} method runs, so a moment
 * captured there equals what the tests see. Every test starts from that base moment with its own
 * {@link PretendRunningAt} applied; {@link #setNow(int, int, int)} moves the clock within a test. A class
 * annotated with {@link RealClock} keeps the real clock.
 */
public final class FrozenClock implements BeforeAllCallback, BeforeEachCallback, AfterAllCallback {

    private static final ThreadLocal< ZonedDateTime > NOW = new ThreadLocal<>();
    private static final ThreadLocal< ZonedDateTime > BASE = new ThreadLocal<>();
    /** The test class whose clock is frozen on this thread, so that the global setting and an explicit registration do not freeze twice. */
    private static final ThreadLocal< Class< ? > > FROZEN_CLASS = new ThreadLocal<>();
    /** The enclosing classes' moments while a {@code @Nested} class runs: [now, base] pairs, innermost last. */
    private static final ThreadLocal< java.util.ArrayDeque< ZonedDateTime[] > > ENCLOSING =
        ThreadLocal.withInitial(java.util.ArrayDeque::new);

    // ---------------------------------------------------------------- test API

    /** The pinned moment. Only meaningful in a class that freezes the clock. */
    public static ZonedDateTime now() {
        ZonedDateTime now = NOW.get();
        if (now == null) {
            throw new StuntException("The clock is not frozen here: FrozenClock is not registered for this test class"
                + " or the class is annotated @RealClock");
        }
        return now;
    }

    /** {@link #now()} as a date. */
    public static LocalDate today() {
        return now().toLocalDate();
    }

    /** Moves the frozen clock to noon of the given day for the rest of the current test. */
    public static void setNow(int year, int month, int day) {
        setNow(year, month, day, 12, 0, 0);
    }

    /** Moves the frozen clock to the given moment (system zone) for the rest of the current test. */
    public static void setNow(int year, int month, int day, int hour, int minute, int second) {
        setNow(ZonedDateTime.of(year, month, day, hour, minute, second, 0, ZoneId.systemDefault()));
    }

    /** Moves the frozen clock to the given moment for the rest of the current test. */
    public static void setNow(ZonedDateTime moment) {
        now(); // fail early if the clock is not frozen
        NOW.set(moment);
    }

    // ---------------------------------------------------------------- lifecycle

    @Override
    public void beforeAll(ExtensionContext context) {
        Class< ? > testClass = context.getRequiredTestClass();
        if (testClass.isAnnotationPresent(RealClock.class) || FROZEN_CLASS.get() == testClass) {
            return; // opted out, or already frozen for this class (StuntSettings.freezeClock plus an explicit registration)
        }
        if (Scope.classScope() == null) {
            throw new StuntException("FrozenClock needs the Stunt class scope: register StuntExtension before it,"
                + " e.g. @ExtendWith({StuntExtension.class, FrozenClock.class})");
        }
        if (BASE.get() != null) {
            ENCLOSING.get().push(new ZonedDateTime[] {NOW.get(), BASE.get()}); // a @Nested class: keep the enclosing clock
        }
        ZonedDateTime base = realNowInMillis();
        PretendRunningAt classPretend = testClass.getAnnotation(PretendRunningAt.class);
        if (classPretend != null) {
            base = applyPretend(base, classPretend);
        }
        BASE.set(base);
        NOW.set(base);
        Class< ? > enclosingClass = FROZEN_CLASS.get();
        FROZEN_CLASS.set(testClass);
        try {
            freeze();
        }
        catch (RuntimeException | Error e) {
            NOW.remove();
            BASE.remove();
            FROZEN_CLASS.set(enclosingClass);
            throw e;
        }
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        ZonedDateTime base = BASE.get();
        if (base == null) {
            return;
        }
        Optional< Method > testMethod = context.getTestMethod();
        PretendRunningAt methodPretend = testMethod.map(m -> m.getAnnotation(PretendRunningAt.class)).orElse(null);
        NOW.set(methodPretend != null ? applyPretend(base, methodPretend) : base);
    }

    @Override
    public void afterAll(ExtensionContext context) {
        if (FROZEN_CLASS.get() != context.getRequiredTestClass()) {
            return; // not frozen for this class (opted out), or already restored by the other registration
        }
        FROZEN_CLASS.set(context.getRequiredTestClass().getEnclosingClass());
        java.util.ArrayDeque< ZonedDateTime[] > enclosing = ENCLOSING.get();
        if (!enclosing.isEmpty() && BASE.get() != null) {
            ZonedDateTime[] outer = enclosing.pop();     // back to the enclosing class's clock
            NOW.set(outer[0]);
            BASE.set(outer[1]);
            return;
        }
        NOW.remove();
        BASE.remove();
    }

    // ---------------------------------------------------------------- mapping

    /**
     * Declares the five {@code java.time} classes partially stubbed at class level and maps every {@code now},
     * labelled as infrastructure so that dumps collapse them to one line.
     */
    private static void freeze() {
        infrastructure("FrozenClock", FrozenClock::mapEveryNow);
    }

    private static void mapEveryNow() {
        stubPartially(ZonedDateTime.class);
        when(() -> ZonedDateTime.now()).thenDo(() -> frozenOrReal());
        when(() -> ZonedDateTime.now(arg.any(Clock.class))).thenDo(() -> frozenOrReal());
        when(() -> ZonedDateTime.now(arg.any(ZoneId.class))).thenDo((ZoneId zone) -> frozenOrReal().withZoneSameInstant(zone));

        stubPartially(LocalDate.class);
        when(() -> LocalDate.now()).thenDo(() -> frozenOrReal().toLocalDate());
        when(() -> LocalDate.now(arg.any(Clock.class))).thenDo(() -> frozenOrReal().toLocalDate());
        when(() -> LocalDate.now(arg.any(ZoneId.class))).thenDo((ZoneId zone) -> frozenOrReal().withZoneSameInstant(zone).toLocalDate());

        stubPartially(LocalDateTime.class);
        when(() -> LocalDateTime.now()).thenDo(() -> frozenOrReal().toLocalDateTime());
        when(() -> LocalDateTime.now(arg.any(Clock.class))).thenDo(() -> frozenOrReal().toLocalDateTime());
        when(() -> LocalDateTime.now(arg.any(ZoneId.class)))
            .thenDo((ZoneId zone) -> frozenOrReal().withZoneSameInstant(zone).toLocalDateTime());

        stubPartially(LocalTime.class);
        when(() -> LocalTime.now()).thenDo(() -> frozenOrReal().toLocalTime());
        when(() -> LocalTime.now(arg.any(Clock.class))).thenDo(() -> frozenOrReal().toLocalTime());
        when(() -> LocalTime.now(arg.any(ZoneId.class))).thenDo((ZoneId zone) -> frozenOrReal().withZoneSameInstant(zone).toLocalTime());

        stubPartially(Instant.class);
        when(() -> Instant.now()).thenDo(() -> frozenOrReal().toInstant());
        when(() -> Instant.now(arg.any(Clock.class))).thenDo(() -> frozenOrReal().toInstant());
    }

    /**
     * Real "now" at millisecond precision, the precision databases keep. Read from {@code System.currentTimeMillis},
     * which is never intercepted, so that a stale mapping of {@code ZonedDateTime.now()} from an earlier test
     * class can never feed the base moment.
     */
    private static ZonedDateTime realNowInMillis() {
        return ZonedDateTime.ofInstant(Instant.ofEpochMilli(System.currentTimeMillis()), ZoneId.systemDefault());
    }

    /** The frozen moment, or the real one when this thread's clock is not frozen (a stale mapping, a worker). */
    private static ZonedDateTime frozenOrReal() {
        ZonedDateTime now = NOW.get();
        return now != null ? now : realNowInMillis();
    }

    /** Overrides only the components the annotation specifies ({@code -1} keeps the base moment's value). */
    static ZonedDateTime applyPretend(ZonedDateTime now, PretendRunningAt at) {
        if (at.year() != -1 || at.month() != -1 || at.day() != -1) {
            int targetDay = at.day() != -1 ? at.day() : now.getDayOfMonth();
            now = now.withDayOfMonth(1); // avoid an invalid intermediate date, e.g. base on the 31st plus month = 2
            if (at.year() != -1) {
                now = now.withYear(at.year());
            }
            if (at.month() != -1) {
                now = now.withMonth(at.month());
            }
            now = now.withDayOfMonth(Math.min(targetDay, now.toLocalDate().lengthOfMonth()));
        }
        if (at.hour() != -1) {
            now = now.withHour(at.hour());
        }
        if (at.minute() != -1) {
            now = now.withMinute(at.minute());
        }
        return now;
    }
}
