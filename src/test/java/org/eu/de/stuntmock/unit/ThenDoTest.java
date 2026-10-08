package org.eu.de.stuntmock.unit;

import static org.eu.de.stuntmock.Stunt.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.eu.de.stuntmock.Invocation;
import org.eu.de.stuntmock.Stub;
import org.eu.de.stuntmock.StubPartially;
import org.eu.de.stuntmock.StuntException;
import org.eu.de.stuntmock.StuntExtension;
import org.eu.de.stuntmock.fakes.Counter;
import org.eu.de.stuntmock.fakes.Greeter;
import org.eu.de.stuntmock.fakes.Tools;

/**
 * {@code thenDo}: the three closure shapes (no parameters, the method's parameters with boxed types,
 * {@code (Invocation inv)}), value and void bodies, calling the original from a closure, and the definition-time
 * checks of arity and types.
 */
@ExtendWith(StuntExtension.class)
@StubPartially({Greeter.class, Counter.class})
@Stub(Tools.class)
class ThenDoTest {

    private final List< String > log = new ArrayList<>();

    /** {@code () -> value} */
    @Test
    void noParameterValueClosure() {
        int[] calls = {0};
        when(() -> Tools.version()).thenDo(() -> "v" + ++calls[0]);
        assertEquals("v1", Tools.version());
        assertEquals("v2", Tools.version());
    }

    /** {@code () -> { side effect }} on a void method, no {@code return null} needed. */
    @Test
    void noParameterVoidClosure() {
        Greeter g = new Greeter();
        when(() -> g.setGreeting(arg.anyString())).thenDo(() -> log.add("set called"));
        g.setGreeting("x");
        assertEquals(List.of("set called"), log);
        assertEquals("Hello", g.getGreeting());
    }

    /** The method's parameters, explicitly typed with boxed types. */
    @Test
    void typedParameterClosure() {
        when(Greeter.class, g -> g.add(arg.anyInt(), arg.anyInt())).thenDo((Integer a, Integer b) -> a * b);
        when(() -> Tools.describe(arg.anyString(), arg.anyInt())).thenDo((String name, Integer n) -> {
            log.add(name + "/" + n);
        });
        assertEquals(12, new Greeter().add(3, 4));
        assertNull(Tools.describe("x", 2)); // void body on a non-void method: default value
        assertEquals(List.of("x/2"), log);
    }

    /** {@code (Invocation inv)}: receiver, method, args, original. */
    @Test
    void invocationClosure() {
        Greeter g = new Greeter("Hey");
        when(() -> g.greet(arg.anyString())).thenDo((Invocation inv) ->
            inv.method().getName() + ":" + inv.<String>arg(0) + ":" + ((Greeter) inv.receiver()).getGreeting());
        assertEquals("greet:Bob:Hey", g.greet("Bob"));
    }

    /** {@code inv.callOriginal()} runs the real method and lets the closure adjust the result. */
    @Test
    void callOriginalFromClosure() {
        when(Counter.class, c -> c.next()).thenDo((Invocation inv) -> (Integer) inv.callOriginal() + 100);
        Counter counter = new Counter();
        assertEquals(101, counter.next());
        assertEquals(102, counter.next());
        assertEquals(2, counter.value()); // the real state advanced twice
    }

    /** A closure may call other mapped methods; they are dispatched normally. */
    @Test
    void closureCallingOtherMappedMethods() {
        when(() -> Tools.doubled(arg.anyInt())).thenReturn(5);
        when(() -> Tools.version()).thenDo(() -> "v" + Tools.doubled(1));
        assertEquals("v5", Tools.version());
    }

    /** A closure may throw, checked or not. */
    @Test
    void closureMayThrow() {
        when(() -> Tools.version()).thenDo(() -> {
            throw new java.io.IOException("from closure");
        });
        java.io.IOException e = assertThrows(java.io.IOException.class, Tools::version);
        assertEquals("from closure", e.getMessage());
    }

    /** Wrong arity is reported on the {@code thenDo} line. */
    @Test
    void wrongArityIsRejected() {
        StuntException e = assertThrows(StuntException.class,
            () -> when(() -> Tools.describe(arg.anyString(), arg.anyInt())).thenDo((String only) -> only));
        assertTrue(e.getMessage().contains("takes 1 parameter(s) but Tools.describe(String, int) has 2"),
            e.getMessage());
    }

    /** A wrong parameter type is reported with the boxed type to use. */
    @Test
    void wrongParameterTypeIsRejected() {
        StuntException e = assertThrows(StuntException.class,
            () -> when(() -> Tools.doubled(arg.anyInt())).thenDo((String s) -> 1));
        assertTrue(e.getMessage().contains("closure parameter 1 is String but Tools.doubled(int) takes int"),
            e.getMessage());
        assertTrue(e.getMessage().contains("declare it as Integer"), e.getMessage());
    }

    /** A returned value is checked against the return type when the call happens. */
    @Test
    void returnedValueIsTypeChecked() {
        when(() -> Tools.doubled(arg.anyInt())).thenDo(() -> "not an int");
        StuntException e = assertThrows(StuntException.class, () -> Tools.doubled(1));
        assertTrue(e.getMessage().contains("cannot be returned from a method returning int"), e.getMessage());
    }
}
