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
import org.eu.de.stuntmock.fakes.Counter;
import org.eu.de.stuntmock.fakes.Greeter;
import org.eu.de.stuntmock.fakes.Tools;

/**
 * {@code when(Greeter::getGreeting)} without the class: an unbound method reference names its class itself, so
 * the class parameter is redundant for instance methods with a unique name. Everything else still needs the
 * class or a lambda, and says so.
 */
@ExtendWith(StuntExtension.class)
@StubPartially({Greeter.class, Counter.class, Tools.class})
class UnboundReferenceTest {

    @Test
    void unboundReferenceMapsEveryInstance() {
        when(Greeter::getGreeting).thenReturn("stubbed");
        assertEquals("stubbed", new Greeter("real").getGreeting());
        assertEquals("stubbed", new Greeter().getGreeting());
    }

    @Test
    void unboundReferenceVerifies() {
        verify(Counter::next).thenReturn(7).times(2);
        Counter counter = new Counter();
        assertEquals(7, counter.next());
        assertEquals(7, counter.next());
    }

    /** Equivalent to the class form; both map the same method of every instance. */
    @Test
    void equalsTheClassForm() {
        when(Greeter.class, Greeter::getGreeting).thenReturn("a");
        when(Greeter::getGreeting).thenReturn("b");
        assertEquals("b", new Greeter().getGreeting()); // newest wins
    }

    /** A lambda cannot type its parameter without the class; the message says which form to use. */
    @Test
    void lambdaWithoutClassIsRejected() {
        StuntException e = assertThrows(StuntException.class, () -> when(it -> it.toString()));
        assertTrue(e.getMessage().contains("when(Foo.class, it -> it.method(...))"), e.getMessage());
    }

    /** A static method with a parameter compiles as CallOn but has no receiver to map; use a lambda. */
    @Test
    void staticWithParameterIsRejected() {
        StuntException e = assertThrows(StuntException.class, () -> when(Tools::doubled));
        assertTrue(e.getMessage().contains("when(() -> Foo.doubled(arg.any()))"), e.getMessage());
    }

    /** A bound reference with a parameter would have to guess the argument; use a lambda with matchers. */
    @Test
    void boundWithParameterIsRejected() {
        Greeter greeter = new Greeter();
        StuntException e = assertThrows(StuntException.class, () -> when(greeter::greet));
        assertTrue(e.getMessage().contains("when(() -> x.greet(arg.any()))"), e.getMessage());
    }
}
