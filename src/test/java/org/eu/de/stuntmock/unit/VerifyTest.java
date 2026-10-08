package org.eu.de.stuntmock.unit;

import static org.eu.de.stuntmock.Stunt.*;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.eu.de.stuntmock.MissingCallError;
import org.eu.de.stuntmock.StubPartially;
import org.eu.de.stuntmock.StuntException;
import org.eu.de.stuntmock.StuntExtension;
import org.eu.de.stuntmock.UnexpectedCallError;
import org.eu.de.stuntmock.Verify;
import org.eu.de.stuntmock.fakes.Counter;
import org.eu.de.stuntmock.fakes.Greeter;
import org.eu.de.stuntmock.fakes.Tools;
import org.eu.de.stuntmock.internal.Scope;

/**
 * {@code verify}: declared before the production code runs, checked at the call (too many) and at the end of the
 * test (too few). Counts: default {@code times(1)}, {@code times}, {@code minTimes}, {@code maxTimes},
 * {@code never}. The end-of-test check is exercised directly through the scope so that a red outcome can be
 * asserted inside a green test.
 */
@ExtendWith(StuntExtension.class)
@StubPartially({Tools.class, Greeter.class})
class VerifyTest {

    /** {@code verify} without count expects exactly one call; satisfied here. */
    @Test
    void defaultIsExactlyOnce() {
        verify(() -> Tools.doubled(2)).thenReturn(4);
        assertEquals(4, Tools.doubled(2));
    }

    /** A second call beyond {@code times(1)} fails at the call, with the caller in the stack trace. */
    @Test
    void tooManyCallsFailAtTheCall() {
        verify(() -> Tools.version()).thenReturn("v");
        Tools.version();
        UnexpectedCallError e = assertThrows(UnexpectedCallError.class, Tools::version);
        assertTrue(e.getMessage().contains("verify(Tools.version())"), e.getMessage());
        assertTrue(e.getMessage().contains("is already satisfied"), e.getMessage());
        assertTrue(e.getMessage().contains("(VerifyTest.java:"), e.getMessage());
        TestSupport.clearRecordedFailures();
    }

    /** Too few calls are reported by the end-of-test check with the declaration line. */
    @Test
    void tooFewCallsFailAtTheEnd() {
        verify(() -> Tools.doubled(arg.anyInt())).thenReturn(0).times(2);
        Tools.doubled(1);
        MissingCallError e = assertThrows(MissingCallError.class, () -> Scope.testScope().verifyAll());
        assertTrue(e.getMessage().contains("expected 2 calls"), e.getMessage());
        assertTrue(e.getMessage().contains("got 1"), e.getMessage());
        assertTrue(e.getMessage().contains("(VerifyTest.java:"), e.getMessage());
        Tools.doubled(1); // satisfy it so the real end-of-test check passes
    }

    @Test
    void minTimesAndMaxTimes() {
        verify(() -> Tools.doubled(arg.anyInt())).thenReturn(0).minTimes(2);
        verify(() -> Tools.tripled(arg.anyInt())).thenReturn(0).maxTimes(2);
        Tools.doubled(1);
        Tools.doubled(1);
        Tools.doubled(1); // minTimes has no upper bound
        Tools.tripled(1);
        assertDoesNotThrow(() -> Scope.testScope().verifyAll());
        Tools.tripled(1);
        assertThrows(UnexpectedCallError.class, () -> Tools.tripled(1));
        TestSupport.clearRecordedFailures();
    }

    /** {@code never()} fails the first call. */
    @Test
    void neverFailsTheFirstCall() {
        verify(() -> Tools.version()).never();
        UnexpectedCallError e = assertThrows(UnexpectedCallError.class, Tools::version);
        assertTrue(e.getMessage().contains("never()"), e.getMessage());
        TestSupport.clearRecordedFailures();
    }

    /** {@code verify} without a terminal keeps the mode's behaviour and only counts. */
    @Test
    void verifyWithoutTerminalKeepsRealBehaviour() {
        verify(() -> Tools.doubled(arg.anyInt())).times(2);
        assertEquals(2, Tools.doubled(1));
        assertEquals(4, Tools.doubled(2));
    }

    /** Fail-fast errors are {@link Error}s, so production code catching {@code Exception} cannot hide them. */
    @Test
    void failFastSurvivesCatchException() {
        verify(() -> Tools.version()).never();
        Greeter g = new Greeter();
        assertThrows(UnexpectedCallError.class, () -> g.guarded(Tools::version));
        TestSupport.clearRecordedFailures();
    }

    /** ... and even if production code swallows {@code Throwable}, the recorded failure resurfaces at the end. */
    @Test
    void swallowedFailFastResurfacesAtTheEnd() {
        verify(() -> Tools.version()).never();
        try {
            Tools.version();
        }
        catch (Throwable swallowed) {
            // production code that catches everything
        }
        UnexpectedCallError e = assertThrows(UnexpectedCallError.class, () -> Scope.testScope().verifyAll());
        assertTrue(e.getMessage().contains("never()"), e.getMessage());
        TestSupport.clearRecordedFailures();
    }

    /** {@code @Verify} on a class plus {@code when} for the allowed calls: everything else fails. */
    @Test
    @StubPartially(Counter.class)
    @Verify(Counter.class)
    void verifyClassAllowsOnlyMappedCalls() {
        when(Counter.class, c -> c.next()).thenReturn(1);
        Counter counter = new Counter();
        assertEquals(1, counter.next());
        assertThrows(UnexpectedCallError.class, counter::value);
        TestSupport.clearRecordedFailures();
    }

    /** Count modifiers that assert are rejected on {@code when}. */
    @Test
    void assertingCountsAreRejectedOnWhen() {
        StuntException e1 = assertThrows(StuntException.class, () -> when(() -> Tools.version()).never());
        assertTrue(e1.getMessage().contains("when(...) does not assert"), e1.getMessage());
        StuntException e2 = assertThrows(StuntException.class, () -> when(() -> Tools.version()).minTimes(1));
        assertTrue(e2.getMessage().contains("when(...) does not assert"), e2.getMessage());
    }

    /** Two count modifiers in a row are an error. */
    @Test
    void doubleCountIsRejected() {
        StuntException e = assertThrows(StuntException.class,
            () -> verify(() -> Tools.version()).thenReturn("v").times(1).times(2));
        assertTrue(e.getMessage().contains("follows another count modifier"), e.getMessage());
        Tools.version();
    }

    /**
     * A {@code verify} without a terminal on a {@code @Verify} class allows the call and runs the real code:
     * {@code @Verify} is a strict audit. Regression: this used to be reported as an unmapped call, and for a while
     * it skipped the body.
     */
    @Test
    void verifyWithoutTerminalOnVerifyClassRunsTheOriginal() {
        Counter counter = new Counter();
        verify(stubPartially(Counter.class));
        verify(() -> counter.add(5));
        counter.add(5);
        assertEquals(5, Deencapsulate.field(counter, "value"));
    }

    /** {@code thenDoNothing()} verifies the call and skips the body. */
    @Test
    void verifyWithThenDoNothingOnVerifyClassSkipsTheBody() {
        Counter counter = new Counter();
        verify(stubPartially(Counter.class));
        verify(() -> counter.add(5)).thenDoNothing();
        counter.add(5);
        assertEquals(0, Deencapsulate.field(counter, "value"));
    }

    /** The same on a {@code @Stub} class: the default value, no failure. */
    @Test
    void verifyWithoutTerminalOnStubClassReturnsDefault() {
        Counter counter = stub(Counter.class);
        verify(() -> counter.next()).times(2);
        assertEquals(0, counter.next());
        assertEquals(0, counter.next());
    }
}
