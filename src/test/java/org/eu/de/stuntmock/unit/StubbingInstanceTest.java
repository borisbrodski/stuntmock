package org.eu.de.stuntmock.unit;

import static org.eu.de.stuntmock.Stunt.*;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.eu.de.stuntmock.Stub;
import org.eu.de.stuntmock.StubPartially;
import org.eu.de.stuntmock.StuntExtension;
import org.eu.de.stuntmock.fakes.Greeter;
import org.eu.de.stuntmock.fakes.Service;
import org.eu.de.stuntmock.fakes.Tools;

/**
 * Stubbing instance methods: instances the production code creates itself, the two scopes a closure can express
 * (a concrete receiver means that instance, a handle or {@code Type.class, it -> ...} means every instance), and
 * the state of partially stubbed objects.
 */
@ExtendWith(StuntExtension.class)
@StubPartially(Greeter.class)
class StubbingInstanceTest {

    /** The instance is created inside the code under test; the mapping still applies. */
    @Test
    @Stub(Tools.class)
    void freshInstancesInsideTheSutAreIntercepted() {
        when(Greeter.class, g -> g.greet("Bob")).thenReturn("Servus Bob");
        when(() -> Tools.version()).thenReturn("v");
        assertEquals("Servus Bob [v]", new Service().welcome("Bob"));
    }

    /** {@code Type.class, it -> ...} maps every instance. */
    @Test
    void classFormMapsEveryInstance() {
        when(Greeter.class, g -> g.getGreeting()).thenReturn("Moin");
        assertEquals("Moin", new Greeter().getGreeting());
        assertEquals("Moin", new Greeter("Hi").getGreeting());
    }

    /** A closure on a handle also maps every instance. */
    @Test
    void handleFormMapsEveryInstance(@StubPartially Greeter handle) {
        when(() -> handle.getGreeting()).thenReturn("Moin");
        assertEquals("Moin", new Greeter().getGreeting());
        assertEquals("Moin", new Greeter("Hi").getGreeting());
    }

    /** A closure on a real object maps that object only. */
    @Test
    void concreteReceiverMapsThatInstanceOnly() {
        Greeter one = new Greeter("One");
        Greeter two = new Greeter("Two");
        when(() -> one.getGreeting()).thenReturn("stubbed");
        assertEquals("stubbed", one.getGreeting());
        assertEquals("Two", two.getGreeting());
    }

    /** Real state keeps working next to mapped methods on a partial object. */
    @Test
    void partialObjectKeepsItsState() {
        Greeter g = new Greeter();
        when(() -> g.greet("Bob")).thenReturn("nope");
        g.setGreeting("Hey");
        assertEquals("Hey", g.getGreeting());
        assertEquals("nope", g.greet("Bob"));
        assertEquals("Hey, Ann!", g.greet("Ann"));
        assertEquals(java.util.List.of("Ann"), g.getLog()); // the real greet("Ann") logged, the stubbed one did not
    }

    /** Primitive parameters and return types work with the boxed matchers and values. */
    @Test
    void primitivesAreBoxedAndUnboxedTransparently() {
        when(Greeter.class, g -> g.add(arg.anyInt(), arg.eq(2))).thenReturn(100);
        when(Greeter.class, g -> g.id()).thenReturn(7);          // Integer widened to long
        when(Greeter.class, g -> g.isPolite()).thenReturn(false);
        Greeter g = new Greeter();
        assertEquals(100, g.add(1, 2));
        assertEquals(3, g.add(1, 2 + 0) == 100 ? 3 : g.add(2, 1));
        assertEquals(7L, g.id());
        assertFalse(g.isPolite());
    }

    /** A void method can be skipped or replaced. */
    @Test
    void voidMethods() {
        Greeter g = new Greeter();
        when(() -> g.setGreeting("ignored")).thenDoNothing();
        g.setGreeting("ignored");
        assertEquals("Hello", g.getGreeting());
        g.setGreeting("Servus");
        assertEquals("Servus", g.getGreeting());
    }

    /** A mapped exception surfaces at the call site. */
    @Test
    void thenThrowThrowsAtTheCall() {
        when(Greeter.class, g -> g.greet("boom")).thenThrow(new IllegalArgumentException("stubbed failure"));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> new Greeter().greet("boom"));
        assertEquals("stubbed failure", e.getMessage());
    }

    /** A {@code @Stub} class returns defaults for everything, including methods with side effects. */
    @Test
    @Stub(Service.class)
    void fullyStubbedClassHasNoBehaviour() {
        Service service = new Service();
        assertNull(service.welcome("x"));
        assertNull(service.load(1L));
    }

    /** A method reference to a parameterless method maps it without a lambda. */
    @Test
    void methodReferenceMapsParameterlessMethod() {
        Greeter g = new Greeter();
        when(g::getGreeting).thenReturn("bound");                  // bound reference: this instance only
        when(Greeter.class, Greeter::isPolite).thenReturn(false); // unbound reference: every instance
        assertEquals("bound", g.getGreeting());
        assertEquals("Hello", new Greeter().getGreeting());
        assertFalse(new Greeter().isPolite());
    }
}
