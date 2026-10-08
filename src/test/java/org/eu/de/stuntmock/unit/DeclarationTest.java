package org.eu.de.stuntmock.unit;

import static org.eu.de.stuntmock.Stunt.*;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.eu.de.stuntmock.Stub;
import org.eu.de.stuntmock.StubPartially;
import org.eu.de.stuntmock.StuntException;
import org.eu.de.stuntmock.StuntExtension;
import org.eu.de.stuntmock.UndeclaredClassException;
import org.eu.de.stuntmock.Verify;
import org.eu.de.stuntmock.fakes.Counter;
import org.eu.de.stuntmock.fakes.Greeter;
import org.eu.de.stuntmock.fakes.Helper;
import org.eu.de.stuntmock.fakes.Tools;

/**
 * How classes enter a test's scope: the three annotations in their four placements, the three statements, the
 * handle they produce, and the errors for undeclared classes and conflicting modes.
 */
@ExtendWith(StuntExtension.class)
@Stub(Tools.class)
@StubPartially(Helper.class)
class DeclarationTest {

    /** A class-level {@code @Stub} declares the class for every test; unmapped statics return defaults. */
    @Test
    void classLevelStubAppliesToEveryTest() {
        assertNull(Tools.version());
        assertEquals(0, Tools.doubled(21));
    }

    /** A class-level {@code @StubPartially} leaves the real code running. */
    @Test
    void classLevelPartialRunsRealCode() {
        assertEquals("real-help", Helper.help());
    }

    /** A method-level annotation declares the class for this test only. */
    @Test
    @Stub(Greeter.class)
    void methodLevelStubAppliesToThisTest() {
        assertNull(new Greeter().greet("x"));
    }

    /** ... and is gone in the next test: an undeclared class is left alone and cannot be mapped. */
    @Test
    void undeclaredClassIsRealAndCannotBeMapped() {
        assertEquals("Hello, x!", new Greeter().greet("x"));
        UndeclaredClassException e = assertThrows(UndeclaredClassException.class,
            () -> when(() -> new Greeter().greet("x")).thenReturn("y"));
        assertTrue(e.getMessage().contains("Greeter is not declared"), e.getMessage());
        assertTrue(e.getMessage().contains("stub(Greeter.class)"), e.getMessage());
    }

    /** A parameter annotation declares the class and passes the handle in. */
    @Test
    void parameterDeclaresAndHandsOverTheHandle(@Stub Greeter greeter) {
        when(() -> greeter.greet("Bob")).thenReturn("Hi Bob");
        assertEquals("Hi Bob", new Greeter().greet("Bob"));   // any instance, not just the handle
        assertNull(new Greeter().greet("Ann"));
    }

    /** The statement forms declare from that line on and return the handle. */
    @Test
    void statementsDeclareAndReturnHandles() {
        Greeter partial = stubPartially(Greeter.class);
        Counter verified = verify(stubPartially(Counter.class));
        assertNotNull(partial);
        assertNotNull(verified);
        assertEquals("Hello, x!", new Greeter().greet("x"));
        when(() -> partial.greet("Bob")).thenReturn("Hi Bob");
        assertEquals("Hi Bob", new Greeter().greet("Bob"));
    }

    /** Declaring the same class again in the same mode is a no-op returning the same handle. */
    @Test
    void redeclaringSameModeReturnsSameHandle() {
        Greeter first = stub(Greeter.class);
        Greeter second = stub(Greeter.class);
        assertSame(first, second);
        assertSame(Tools.class, Tools.class);
    }

    /** Declaring a class in a second mode is an error that names both declarations. */
    @Test
    void conflictingModesAreRejected() {
        StuntException e = assertThrows(StuntException.class, () -> stubPartially(Tools.class));
        assertTrue(e.getMessage().contains("already declared as @Stub"), e.getMessage());
        assertTrue(e.getMessage().contains("@StubPartially"), e.getMessage());
    }

    /** Handles are only meant for closures, but they print and compare sanely. */
    @Test
    void handlesHaveIdentityToStringHashCodeEquals() {
        Greeter handle = stubPartially(Greeter.class);
        assertTrue(handle.toString().startsWith("handle of Greeter@"), handle.toString());
        assertEquals(System.identityHashCode(handle), handle.hashCode());
        assertEquals(handle, handle);
        assertNotEquals(handle, new Greeter());
    }

    /** {@code @Verify} on a class makes every unmapped call fail. */
    @Test
    @StubPartially(Counter.class)
    @Verify(Counter.class)
    void verifyDeclarationFailsUnmappedCalls() {
        Counter counter = new Counter();
        AssertionError e = assertThrows(AssertionError.class, counter::next);
        assertTrue(e.getMessage().contains("Unexpected call"), e.getMessage());
        assertTrue(e.getMessage().contains("@Verify"), e.getMessage());
        // the recorded failure is rethrown at the end of the test; clear it here to keep this test green
        TestSupport.clearRecordedFailures();
    }
}
