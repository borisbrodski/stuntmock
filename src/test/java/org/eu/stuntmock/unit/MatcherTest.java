package org.eu.stuntmock.unit;

import static org.eu.stuntmock.Stunt.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.eu.stuntmock.Captor;
import org.eu.stuntmock.Stub;
import org.eu.stuntmock.StuntException;
import org.eu.stuntmock.StuntExtension;
import org.eu.stuntmock.fakes.Greeter;
import org.eu.stuntmock.fakes.PriceService;
import org.eu.stuntmock.fakes.Tools;

/** The matcher set, the all-or-nothing rule, and captors. */
@ExtendWith(StuntExtension.class)
@Stub({Tools.class, Greeter.class})
class MatcherTest {

    @Test
    void anyMatchesEverythingIncludingNull() {
        when(Greeter.class, g -> g.greet(arg.any())).thenReturn("x");
        assertEquals("x", new Greeter().greet(null));
        assertEquals("x", new Greeter().greet("a"));
    }

    @Test
    void typedAnySelectsOverloadAndType() {
        when(Greeter.class, g -> g.pick(arg.any(String.class))).thenReturn("s");
        when(Greeter.class, g -> g.pick(arg.any(Integer.class))).thenReturn("i");
        assertEquals("s", new Greeter().pick("a"));
        assertEquals("i", new Greeter().pick(1));
    }

    @Test
    void primitiveAnyForPrimitiveParameters() {
        when(() -> Tools.total(arg.anyLong(), arg.anyLong())).thenReturn(9L);
        when(() -> Tools.doubled(arg.anyInt())).thenReturn(1);
        assertEquals(9L, Tools.total(1, 2));
        assertEquals(1, Tools.doubled(5));
    }

    @Test
    void eqSameIsNullNotNull() {
        String shared = "shared";
        when(Greeter.class, g -> g.pick(arg.eq("v"))).thenReturn("eq");
        when(Greeter.class, g -> g.pick(arg.same(shared))).thenReturn("same");
        when(Greeter.class, g -> g.pick(arg.isNull(), arg.anyInt())).thenReturn("null");
        when(Greeter.class, g -> g.pick(arg.notNull(), arg.eq(7))).thenReturn("notnull");
        Greeter g = new Greeter();
        assertEquals("eq", g.pick("v"));
        assertEquals("same", g.pick(shared));
        assertNull(g.pick(new String("shared")));
        assertEquals("null", g.pick(null, 1));
        assertEquals("notnull", g.pick("a", 7));
    }

    /** The universal matcher with an explicitly typed lambda, and its typed variant. */
    @Test
    void argThatWithTypedLambda() {
        when(Greeter.class, g -> g.greet(arg.argThat((String s) -> s.length() > 3))).thenReturn("long");
        when(() -> Tools.describe(arg.argThat(String.class, s -> s.isEmpty()), arg.anyInt())).thenReturn("empty");
        assertEquals("long", new Greeter().greet("Robert"));
        assertNull(new Greeter().greet("Bob"));
        assertEquals("empty", Tools.describe("", 1));
    }

    @Test
    void stringMatchers() {
        when(Greeter.class, g -> g.greet(arg.startsWith("A"))).thenReturn("a");
        when(Greeter.class, g -> g.greet(arg.endsWith("z"))).thenReturn("z");
        when(Greeter.class, g -> g.greet(arg.contains("mid"))).thenReturn("m");
        when(Greeter.class, g -> g.greet(arg.matches("\\d+"))).thenReturn("d");
        Greeter g = new Greeter();
        assertEquals("a", g.greet("Ann"));
        assertEquals("z", g.greet("Fritz"));
        assertEquals("m", g.greet("a-mid-b"));
        assertEquals("d", g.greet("123"));
    }

    @Test
    void collectionMatchers() {
        PriceService repo = stubProxy(PriceService.class); // proxy handle, instance scoped
        assertNotNull(repo);
        when(() -> Tools.names()).thenReturn(List.of("a"));
        assertEquals(List.of("a"), Tools.names());
    }

    /** Plain values and matchers cannot be mixed; the message says what to do. */
    @Test
    void mixingValuesAndMatchersIsRejected() {
        StuntException e = assertThrows(StuntException.class,
            () -> when(() -> Tools.describe(arg.anyString(), 3)).thenReturn("x"));
        assertTrue(e.getMessage().contains("1 matcher(s) for 2 argument(s)"), e.getMessage());
        assertTrue(e.getMessage().contains("wrap plain values in arg.eq(...)"), e.getMessage());
    }

    /** A matcher whose type cannot fit the parameter is rejected (javac catches it in closures, Stunt by name). */
    @Test
    void incompatibleMatcherTypeIsRejected() {
        StuntException e = assertThrows(StuntException.class,
            () -> when(Greeter.class, "greet", arg.any(Integer.class)));
        assertTrue(e.getMessage().contains("no overload accepts the given arguments"), e.getMessage());
    }

    /** Captors record the argument of every matching call. */
    @Test
    void captorRecordsArguments() {
        Captor< String > names = arg.captor(String.class);
        when(Greeter.class, g -> g.greet(names.capture())).thenReturn("hi");
        Greeter g = new Greeter();
        g.greet("Ann");
        g.greet("Bob");
        assertEquals("Bob", names.getValue());
        assertEquals(List.of("Ann", "Bob"), names.getValues());
    }

    @Test
    void captorWithoutCallsIsAnError() {
        Captor< String > names = arg.captor(String.class);
        StuntException e = assertThrows(StuntException.class, names::getValue);
        assertTrue(e.getMessage().contains("No argument captured"), e.getMessage());
    }
}
