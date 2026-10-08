package org.eu.de.stuntmock.unit;

import static org.eu.de.stuntmock.Stunt.*;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.eu.de.stuntmock.StubPartially;
import org.eu.de.stuntmock.StuntExtension;
import org.eu.de.stuntmock.fakes.AbstractGreeter;
import org.eu.de.stuntmock.fakes.Greeter;
import org.eu.de.stuntmock.fakes.Greeting;

/**
 * Declaring a class covers the methods it inherits: instance methods of the superclass, default methods of its
 * interfaces, and inherited statics. Calls are attributed to the declared class by the receiver.
 */
@ExtendWith(StuntExtension.class)
@StubPartially(Greeter.class)
class InheritanceTest {

    @Test
    void inheritedInstanceMethodIsIntercepted() {
        when(Greeter.class, g -> g.inherited()).thenReturn("stubbed");
        assertEquals("stubbed", new Greeter().inherited());
    }

    @Test
    void interfaceDefaultMethodIsIntercepted() {
        when(Greeter.class, g -> g.politely("Bob")).thenReturn("stubbed");
        assertEquals("stubbed", new Greeter().politely("Bob"));
        assertEquals("Dear Hello, Ann!", new Greeter().politely("Ann"));
    }

    /** A default method calling an overridden method: the inner call is dispatched on the same receiver. */
    @Test
    void defaultMethodReachesOverriddenMethod() {
        when(Greeter.class, g -> g.greet("Bob")).thenReturn("Yo Bob");
        assertEquals("Dear Yo Bob", new Greeter().politely("Bob"));
    }

    @Test
    void inheritedStaticIsIntercepted() {
        when(() -> AbstractGreeter.staticInherited()).thenReturn("stubbed");
        assertEquals("stubbed", AbstractGreeter.staticInherited());
    }

    /** The interface type can be used in the closure when the receiver is a declared class instance. */
    @Test
    void closureThroughInterfaceType() {
        Greeting g = new Greeter();
        when(() -> g.greet("Bob")).thenReturn("via interface");
        assertEquals("via interface", g.greet("Bob"));
    }
}
