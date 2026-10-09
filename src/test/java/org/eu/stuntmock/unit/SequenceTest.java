package org.eu.stuntmock.unit;

import static org.eu.stuntmock.Stunt.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.eu.stuntmock.Stub;
import org.eu.stuntmock.StubPartially;
import org.eu.stuntmock.StuntException;
import org.eu.stuntmock.StuntExtension;
import org.eu.stuntmock.UnexpectedCallError;
import org.eu.stuntmock.fakes.Greeter;
import org.eu.stuntmock.fakes.Helper;
import org.eu.stuntmock.fakes.Tools;

/**
 * Sequences: every terminal is one step, a count applies to the step before it, a step answers until its maximum,
 * and what happens after the last step differs between {@code when} (fall back to the mode) and {@code verify}
 * (fail). Also which chain wins when several match.
 */
@ExtendWith(StuntExtension.class)
@StubPartially(Tools.class)
@Stub(Helper.class)
class SequenceTest {

    @BeforeEach
    void reset() {
        Tools.counter = 0;
    }

    /** The last step of a {@code when} answers forever. */
    @Test
    void whenLastStepRepeats() {
        when(() -> Tools.version()).thenReturn("a");
        assertEquals("a", Tools.version());
        assertEquals("a", Tools.version());
    }

    /** Steps without a count answer once each, the last one forever. */
    @Test
    void thenReturnWithSeveralValuesStepsThroughThem() {
        when(() -> Tools.version()).thenReturn("a", "b", "c");
        assertEquals("a", Tools.version());
        assertEquals("b", Tools.version());
        assertEquals("c", Tools.version());
        assertEquals("c", Tools.version());
    }

    /** The example from the design: 2x, 1x, 3x, then the mode's default (here: the real method). */
    @Test
    void perStepCountsInWhenThenFallBackToMode() {
        when(() -> Tools.version())
            .thenReturn("test").times(2)
            .thenThrow(new IllegalStateException("third"))
            .thenReturn("test2").times(3);
        assertEquals("test", Tools.version());
        assertEquals("test", Tools.version());
        assertEquals("third", assertThrows(IllegalStateException.class, Tools::version).getMessage());
        assertEquals("test2", Tools.version());
        assertEquals("test2", Tools.version());
        assertEquals("test2", Tools.version());
        assertEquals("real-version", Tools.version());
    }

    /** The same sequence in a {@code verify}: the seventh call fails at the call site. */
    @Test
    void perStepCountsInVerifyThenFail() {
        verify(() -> Tools.version())
            .thenReturn("test").times(2)
            .thenThrow(new IllegalStateException("third"))
            .thenReturn("test2").times(3);
        Tools.version();
        Tools.version();
        assertThrows(IllegalStateException.class, Tools::version);
        Tools.version();
        Tools.version();
        Tools.version();
        UnexpectedCallError e = assertThrows(UnexpectedCallError.class, Tools::version);
        assertTrue(e.getMessage().contains("already satisfied"), e.getMessage());
        TestSupport.clearRecordedFailures();
    }

    /** {@code thenReturns} answers one value per call; on a collection the length is known. */
    @Test
    void thenReturnsFromCollection() {
        when(() -> Tools.doubled(arg.anyInt())).thenReturns(List.of(10, 20)).thenReturn(30);
        assertEquals(10, Tools.doubled(1));
        assertEquals(20, Tools.doubled(1));
        assertEquals(30, Tools.doubled(1));
        assertEquals(30, Tools.doubled(1));
    }

    /** A lazy iterable answers until it is exhausted, then the next step or the mode takes over. */
    @Test
    void thenReturnsFromLazyIterable() {
        Iterable< Integer > lazy = () -> Stream.of(7, 8).iterator();
        when(() -> Tools.doubled(arg.anyInt())).thenReturns(lazy);
        assertEquals(7, Tools.doubled(1));
        assertEquals(8, Tools.doubled(1));
        assertEquals(2, Tools.doubled(1)); // exhausted: real code
    }

    /** {@code thenReturns} values are checked against the return type when they are used. */
    @Test
    void thenReturnsValuesAreTypeChecked() {
        when(() -> Tools.doubled(arg.anyInt())).thenReturns(List.of("not an int"));
        StuntException e = assertThrows(StuntException.class, () -> Tools.doubled(1));
        assertTrue(e.getMessage().contains("cannot be returned from a method returning int"), e.getMessage());
    }

    /** {@code maxTimes} bounds a step without asserting; {@code times} on {@code when} does the same. */
    @Test
    void maxTimesBoundsAStep() {
        when(() -> Tools.version()).thenReturn("a").maxTimes(1).thenReturn("b").times(1);
        assertEquals("a", Tools.version());
        assertEquals("b", Tools.version());
        assertEquals("real-version", Tools.version());
    }

    /** The newest chain that is not exhausted wins; an exhausted one falls through to older chains. */
    @Test
    void newestNonExhaustedChainWins() {
        when(() -> Tools.doubled(arg.anyInt())).thenReturn(1);
        when(() -> Tools.doubled(5)).thenReturn(2).times(1);
        assertEquals(2, Tools.doubled(5));
        assertEquals(1, Tools.doubled(5)); // specific chain exhausted, general chain answers
        assertEquals(1, Tools.doubled(6));
    }

    /**
     * An exhausted {@code verify} chain fails the next call even if an older {@code when} would match: its count
     * is an assertion. (An exhausted {@code when} defers to older chains instead, see above.)
     */
    @Test
    void exhaustedVerifyFailsEvenIfAnOlderWhenMatches() {
        when(() -> Helper.help()).thenReturn("general");
        verify(() -> Helper.help()).thenReturn("once").times(1);
        assertEquals("once", Helper.help());
        UnexpectedCallError e = assertThrows(UnexpectedCallError.class, Helper::help);
        assertTrue(e.getMessage().contains("already satisfied"), e.getMessage());
        TestSupport.clearRecordedFailures();
    }

    /** A {@code when} declared after a {@code verify} is newer and answers first; once exhausted it defers to the verify. */
    @Test
    void newerWhenAnswersBeforeAnOlderVerify() {
        verify(() -> Helper.help()).thenReturn("once");
        when(() -> Helper.help()).thenReturn("general").times(1);
        assertEquals("general", Helper.help());
        assertEquals("once", Helper.help()); // the when is exhausted, the older verify answers and is satisfied
    }

    /** A step with only a minimum has no upper bound; a step after it can never run. */
    @Test
    void unreachableStepIsRejected() {
        StuntException e = assertThrows(StuntException.class,
            () -> verify(() -> Tools.version()).thenReturn("a").minTimes(1).thenReturn("b"));
        assertTrue(e.getMessage().contains("can never be reached"), e.getMessage());
        Tools.version(); // the chain up to minTimes(1) is registered and expects this call
    }

    // ---------------------------------------------------------------- a count before its terminal

    /**
     * A count written before the terminal completes that step instead of starting a second one:
     * {@code anyTimes().thenCallOriginal()} reads the same as {@code thenCallOriginal().anyTimes()}.
     * Regression: this used to be rejected as an unreachable step 2.
     */
    @Test
    void countBeforeTerminalCompletesTheStep() {
        Greeter greeter = audit(new Greeter("real"));
        when(greeter, mtd.getters()).anyTimes().thenCallOriginal();
        assertEquals("real", greeter.getGreeting());
        assertEquals("real", greeter.getGreeting());
        assertTrue(dump().contains("thenCallOriginal() [2/*]"), dump());
    }

    @Test
    void countBeforeTerminalOnVerify() {
        verify(() -> Helper.help()).times(2).thenReturn("twice");
        assertEquals("twice", Helper.help());
        assertEquals("twice", Helper.help());
        assertThrows(UnexpectedCallError.class, Helper::help);
        TestSupport.clearRecordedFailures();
    }

    /** Both orders describe the same chain. */
    @Test
    void bothOrdersAreEquivalent() {
        when(() -> Helper.help()).thenReturn("a").times(1);
        when(() -> Helper.help()).times(1).thenReturn("a");
        String dump = dump();
        int first = dump.indexOf("thenReturn(\"a\") [0 of 0..1]");
        int second = dump.indexOf("thenReturn(\"a\") [0 of 0..1]", first + 1);
        assertTrue(first >= 0 && second > first, dump);
    }

    /** The sequence rules stay: a second count on the completed step, and a step after an unbounded one, are rejected. */
    @Test
    void sequenceRulesStillApplyAfterACompletedStep() {
        StuntException second = assertThrows(StuntException.class,
            () -> when(() -> Helper.help()).anyTimes().thenReturn("x").times(2));
        assertTrue(second.getMessage().contains("already given") || second.getMessage().contains("one count"),
            second.getMessage());
        StuntException unreachable = assertThrows(StuntException.class,
            () -> when(() -> Helper.help()).thenReturn("a").anyTimes().thenReturn("b"));
        assertTrue(unreachable.getMessage().contains("can never be reached"), unreachable.getMessage());
    }
}
