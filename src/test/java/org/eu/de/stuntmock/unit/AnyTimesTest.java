package org.eu.de.stuntmock.unit;

import static org.eu.de.stuntmock.Stunt.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.eu.de.stuntmock.StubPartially;
import org.eu.de.stuntmock.StuntException;
import org.eu.de.stuntmock.StuntExtension;
import org.eu.de.stuntmock.UnexpectedCallError;
import org.eu.de.stuntmock.fakes.Counter;
import org.eu.de.stuntmock.internal.Scope;

/**
 * {@code anyTimes()}: the step answers any number of calls and asserts nothing, {@code minTimes(0)} without a
 * maximum. Its purpose is to allow calls on a {@code @Verify} object the test is not interested in, next to the
 * calls it verifies.
 */
@ExtendWith(StuntExtension.class)
class AnyTimesTest {

    static class MyClass {
        long id = 42;
        String text;
        String title;

        long getId() {
            return id;
        }

        void setText(String text) {
            this.text = text;
        }

        void setTitle(String title) {
            this.title = title;
        }
    }

    /** The requested shape: one verified call, one allowed call, everything else unexpected. */
    @Test
    void allowsACallOnAVerifiedInstance() {
        var m1 = new MyClass();
        var m2 = new MyClass();
        verify(stubPartially(m2));
        when(m2::getId).anyTimes();
        verify(() -> m2.setText(arg.any()));

        m1.setTitle("test");                       // not declared: real code
        m2.getId();
        m2.getId();
        m2.getId();                                // any number of times
        m2.setText("test");                        // expected, once
        assertEquals("test", m1.title);
        assertThrows(UnexpectedCallError.class, () -> m2.setTitle("test"));
        TestSupport.clearRecordedFailures();
    }

    /** Without a terminal the allowed call keeps the real behaviour: {@code @Verify} is a strict audit. */
    @Test
    void allowedCallWithoutTerminalRunsTheOriginal() {
        var m2 = verify(stubPartially(new MyClass()));
        when(m2::getId).anyTimes();
        assertEquals(42L, m2.getId());
    }

    /** {@code thenDoNothing()} is the way to allow a call and suppress its body. */
    @Test
    void allowedCallCanBeSuppressed() {
        var m2 = verify(stubPartially(new MyClass()));
        when(m2::getId).thenDoNothing().anyTimes();
        assertEquals(0L, m2.getId());
    }

    /** On a {@code verify} chain it removes the implicit {@code times(1)}: zero calls are fine too. */
    @Test
    void onVerifyItRemovesTheAssertion() {
        var m2 = verify(stubPartially(new MyClass()));
        verify(m2::getId).thenReturn(7L).anyTimes();
        Scope.testScope().verifyAll();             // no call yet: nothing missing
        assertEquals(7L, m2.getId());
        assertEquals(7L, m2.getId());
        Scope.testScope().verifyAll();             // two calls: still nothing to report
    }

    /** It equals {@code minTimes(0)} on both kinds. */
    @Test
    @StubPartially(Counter.class)
    void equalsMinTimesZero() {
        Counter counter = new Counter();
        when(counter::next).thenReturn(1).anyTimes();
        when(counter::value).thenReturn(2).minTimes(0);
        assertEquals(1, counter.next());
        assertEquals(1, counter.next());
        assertEquals(2, counter.value());
        assertTrue(dump().contains("thenReturn(1) [2/*]"), dump());
        assertTrue(dump().contains("thenReturn(2) [1/*]"), dump());
    }

    /** Like {@code minTimes}, it must be the last step: a step after it could never be reached. */
    @Test
    @StubPartially(Counter.class)
    void mustBeTheLastStep() {
        Counter counter = new Counter();
        StuntException e = assertThrows(StuntException.class,
            () -> when(counter::next).thenReturn(1).anyTimes().thenReturn(2));
        assertTrue(e.getMessage().contains("never be reached"), e.getMessage());
    }

    @Test
    @StubPartially(Counter.class)
    void oneCountPerStep() {
        Counter counter = new Counter();
        StuntException e = assertThrows(StuntException.class, () -> when(counter::next).times(2).anyTimes());
        assertTrue(e.getMessage().contains("follows another count modifier"), e.getMessage());
    }
}
