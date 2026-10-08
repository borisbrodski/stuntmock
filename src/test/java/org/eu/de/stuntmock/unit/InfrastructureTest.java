package org.eu.de.stuntmock.unit;

import static org.eu.de.stuntmock.Stunt.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.eu.de.stuntmock.StubPartially;
import org.eu.de.stuntmock.StuntExtension;
import org.eu.de.stuntmock.UnexpectedCallError;
import org.eu.de.stuntmock.fakes.Tools;
import org.eu.de.stuntmock.time.FrozenClock;

/**
 * Dumps stay readable when an extension such as {@link FrozenClock} declares infrastructure: its declarations,
 * chains and calls collapse to one line per label, unless a failure concerns them or the dump is filtered to
 * their class. Runs of identical calls are compacted.
 */
@ExtendWith({StuntExtension.class, FrozenClock.class})
@StubPartially(Tools.class)
class InfrastructureTest {

    private static int count(String text, String part) {
        int n = 0;
        for (int i = text.indexOf(part); i >= 0; i = text.indexOf(part, i + part.length())) {
            n++;
        }
        return n;
    }

    /** Five declarations and fourteen chains of the frozen clock become one summary line. */
    @Test
    void infrastructureCollapsesToOneSummaryLine() {
        for (int i = 0; i < 300; i++) {
            LocalDate.now();
        }
        String dump = dump();
        assertTrue(dump.contains("[FrozenClock] @StubPartially ZonedDateTime, @StubPartially LocalDate,"), dump);
        assertTrue(dump.contains("14 chain(s), "), dump);
        assertTrue(dump.contains("call(s); Stunt.dump(ZonedDateTime.class) for details"), dump);
        assertFalse(dump.contains("when(LocalDate.now())"), dump);
        assertEquals(0, count(dump, "LocalDate.now() ->"), dump);
    }

    /** Filtering the dump to one of the classes shows its chains and its calls, compacted. */
    @Test
    void filteredDumpShowsTheInfrastructureInFull() {
        for (int i = 0; i < 150; i++) {
            LocalDate.now();
        }
        String dump = dump(LocalDate.class);
        assertTrue(dump.contains("when(LocalDate.now())"), dump);
        assertTrue(dump.contains("[FrozenClock]"), dump);
        assertEquals(1, count(dump, "LocalDate.now() ->"), dump);
        assertTrue(dump.contains("(x150, calls "), dump);
    }

    /** The trace keeps a bounded number of entries per class, so a noisy class cannot crowd out the others. */
    @Test
    void traceLimitIsPerClass() {
        for (int i = 0; i < 300; i++) {
            LocalDate.now();
        }
        Tools.version();
        String dump = dump(LocalDate.class);
        assertTrue(dump.contains("(x200, calls "), dump);
        assertTrue(dump.contains("... 100 more on LocalDate (trace limit)"), dump);
        assertTrue(dump().contains("Tools.version() -> original (unmapped)"), dump());
    }

    /** A failure about another class keeps the infrastructure collapsed. */
    @Test
    void failureAboutAnotherClassKeepsInfrastructureCollapsed() {
        verify(() -> Tools.version()).never();
        LocalDate.now();
        UnexpectedCallError e = assertThrows(UnexpectedCallError.class, Tools::version);
        assertTrue(e.getMessage().contains("[FrozenClock]"), e.getMessage());
        assertFalse(e.getMessage().contains("when(ZonedDateTime.now())"), e.getMessage());
        assertEquals(0, count(e.getMessage(), "LocalDate.now() ->"), e.getMessage());
        TestSupport.clearRecordedFailures();
    }

    /** A failure about an infrastructure class reveals that label in full. */
    @Test
    void failureAboutAnInfrastructureClassRevealsIt() {
        verify(() -> LocalDate.now()).never();          // the test's own chain: never collapsed
        UnexpectedCallError e = assertThrows(UnexpectedCallError.class, LocalDate::now);
        assertTrue(e.getMessage().contains("verify(LocalDate.now())"), e.getMessage());
        assertTrue(e.getMessage().contains("when(ZonedDateTime.now())"), e.getMessage());
        assertFalse(e.getMessage().contains("[FrozenClock] @StubPartially"), e.getMessage());
        TestSupport.clearRecordedFailures();
    }

    /** A test's own mapping on an infrastructure class is not infrastructure and shows in every dump. */
    @Test
    void ownMappingOnInfrastructureClassShowsInFull() {
        when(() -> LocalDate.now()).thenReturn(LocalDate.of(2000, 1, 1));
        LocalDate.now();
        LocalDate.now();
        String dump = dump();
        assertTrue(dump.contains("when(LocalDate.now())"), dump);
        assertTrue(dump.contains("[FrozenClock]"), dump);
        assertTrue(dump.contains("(x2, calls "), dump);
    }

    /** Compaction is independent of infrastructure: repeated calls on any class collapse to one line. */
    @Test
    void repeatedCallsAreCompacted() {
        for (int i = 0; i < 5; i++) {
            Tools.version();
        }
        Tools.doubled(1);
        String dump = dump();
        assertEquals(1, count(dump, "Tools.version() ->"), dump);
        assertTrue(dump.contains("(x5, calls "), dump);
        assertTrue(dump.contains("Tools.doubled(1) ->"), dump);
    }
}
