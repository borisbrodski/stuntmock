package org.eu.de.stuntmock.unit;

import static org.eu.de.stuntmock.Stunt.*;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.eu.de.stuntmock.StubPartially;
import org.eu.de.stuntmock.StuntException;
import org.eu.de.stuntmock.StuntExtension;
import org.eu.de.stuntmock.fakes.Greeter;

/** Private methods are mapped by name, with the same matcher rules; overloads are selected by argument types. */
@ExtendWith(StuntExtension.class)
@StubPartially(Greeter.class)
class StubbingPrivateTest {

    @Test
    void privateMethodByNameWithPlainValue() {
        when(Greeter.class, "privateGreeting", "Bob").thenReturn("private Bob");
        Greeter g = new Greeter();
        assertEquals("private Bob", g.greet("Bob"));
        assertEquals("Hello, Ann!", g.greet("Ann"));
    }

    @Test
    void privateMethodByNameWithMatcher() {
        when(Greeter.class, "privateGreeting", arg.startsWith("B")).thenReturn("B-name");
        assertEquals("B-name", new Greeter().greet("Bill"));
        assertEquals("Hello, Ann!", new Greeter().greet("Ann"));
    }

    @Test
    void privateMethodCanBeVerified() {
        verify(Greeter.class, "privateGreeting", arg.anyString()).thenCallOriginal().times(2);
        Greeter g = new Greeter();
        g.greet("a");
        g.greet("b");
    }

    /** Public overloads are selected by the argument type when mapped by name. */
    @Test
    void overloadsAreSelectedByArgumentType() {
        when(Greeter.class, "pick", arg.any(Integer.class)).thenReturn("int-overload");
        when(Greeter.class, "pick", "x", 1).thenReturn("two-arg-overload");
        Greeter g = new Greeter();
        assertEquals("int-overload", g.pick(5));
        assertEquals("string:s", g.pick("s"));
        assertEquals("two-arg-overload", g.pick("x", 1));
    }

    @Test
    void ambiguousOverloadIsRejectedWithCandidates() {
        StuntException e = assertThrows(StuntException.class, () -> when(Greeter.class, "pick", arg.any()));
        assertTrue(e.getMessage().contains("2 overloads"), e.getMessage());
        assertTrue(e.getMessage().contains("Greeter.pick(String)"), e.getMessage());
        assertTrue(e.getMessage().contains("Greeter.pick(Integer)"), e.getMessage());
    }

    @Test
    void unknownMethodNameIsRejected() {
        StuntException e = assertThrows(StuntException.class, () -> when(Greeter.class, "nope"));
        assertTrue(e.getMessage().contains("no method nope"), e.getMessage());
    }
}
