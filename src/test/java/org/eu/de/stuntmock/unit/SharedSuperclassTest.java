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
import org.eu.de.stuntmock.UndeclaredClassException;

/**
 * Two declared subclasses of one superclass that declares the method. The declaration a mapping belongs to
 * must come from the receiver, not from the class that declares the method: {@code b::myMethod} is about
 * {@code B}, although {@code myMethod} is declared in {@code Abstract}. Regression for a bug where the first
 * declaration relating to {@code Abstract} ({@code A}) was picked and {@code b} was then rejected as not an
 * instance of {@code A}.
 */
@ExtendWith(StuntExtension.class)
class SharedSuperclassTest {

    static class Abstract {
        int calls;

        void myMethod() {
            calls++;
        }

        static String shared() {
            return "real";
        }
    }

    static class A extends Abstract {
    }

    static class B extends Abstract {
    }

    static class C extends Abstract {
    }

    /** The reported case, verbatim. */
    @Test
    @StubPartially(A.class)
    @StubPartially(B.class)
    void boundReferenceOnTheSecondDeclaredSubclass() {
        var b = new B();
        verify(b::myMethod);
        b.myMethod();
    }

    /** The same with a closure; the receiver's static type is {@code B}, so it is unambiguous anyway. */
    @Test
    @StubPartially(A.class)
    @StubPartially(B.class)
    void closureOnTheSecondDeclaredSubclass() {
        var b = new B();
        verify(() -> b.myMethod()).thenDoNothing();
        b.myMethod();
        assertEquals(0, b.calls);
    }

    /** Both declarations get their own chains; each instance is counted against its own class. */
    @Test
    @StubPartially(A.class)
    @StubPartially(B.class)
    void eachSubclassKeepsItsOwnMapping() {
        var a = new A();
        var b = new B();
        verify(a::myMethod).thenDoNothing();
        verify(b::myMethod).times(2);
        a.myMethod();
        b.myMethod();
        b.myMethod();
        assertEquals(0, a.calls);
        assertEquals(2, b.calls);
    }

    /** A receiver of an undeclared sibling is reported as undeclared, naming its own class. */
    @Test
    @StubPartially(A.class)
    @StubPartially(B.class)
    void undeclaredSiblingIsReported() {
        var c = new C();
        UndeclaredClassException e = assertThrows(UndeclaredClassException.class, () -> verify(c::myMethod));
        assertTrue(e.getMessage().contains("SharedSuperclassTest$C"), e.getMessage());
    }

    /** {@code Abstract::myMethod} could mean A or B; Stunt refuses to guess and names the class form. */
    @Test
    @StubPartially(A.class)
    @StubPartially(B.class)
    void unboundReferenceToTheSharedMethodIsAmbiguous() {
        StuntException e = assertThrows(StuntException.class, () -> verify(Abstract::myMethod));
        assertTrue(e.getMessage().contains("ambiguous"), e.getMessage());
        assertTrue(e.getMessage().contains("A.class, Abstract::myMethod"), e.getMessage());
    }

    /** Naming the class resolves the ambiguity and maps every instance of that class only. */
    @Test
    @StubPartially(A.class)
    @StubPartially(B.class)
    void classFormResolvesTheAmbiguity() {
        verify(B.class, Abstract::myMethod).thenDoNothing();
        var a = new A();
        var b = new B();
        a.myMethod();
        b.myMethod();
        assertEquals(1, a.calls);
        assertEquals(0, b.calls);
    }

    /** Declaring the superclass itself covers every subclass, declared or not. */
    @Test
    @StubPartially(Abstract.class)
    void declaringTheSuperclassCoversAllSubclasses() {
        when(Abstract::myMethod).thenDoNothing();
        var a = new A();
        var c = new C();
        a.myMethod();
        c.myMethod();
        assertEquals(0, a.calls + c.calls);
    }

    /** A static of the shared superclass belongs to whichever declaration claims it first, consistently. */
    @Test
    @StubPartially(A.class)
    @StubPartially(B.class)
    void staticOfSharedSuperclassIsMappedConsistently() {
        when(() -> Abstract.shared()).thenReturn("stubbed");
        assertEquals("stubbed", Abstract.shared());
        assertEquals("stubbed", A.shared());
    }
}
