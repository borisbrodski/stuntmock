package org.eu.stuntmock.unit;

import static org.eu.stuntmock.Stunt.*;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.eu.stuntmock.Stub;
import org.eu.stuntmock.StubPartially;
import org.eu.stuntmock.StuntExtension;
import org.eu.stuntmock.fakes.Greeter;
import org.eu.stuntmock.fakes.Tools;

/**
 * The per-method lifecycle (JUnit's default): static fields and a static {@code @BeforeAll} declare and map at
 * class level, every test gets a fresh copy of those chains; instance fields and {@code @BeforeEach} are per test.
 */
@ExtendWith(StuntExtension.class)
class LifecycleTest {

    @Stub
    static Tools tools;

    @StubPartially
    Greeter greeter;

    @BeforeAll
    static void classLevelDefaults() {
        assertNotNull(tools, "static handle is bound before @BeforeAll");
        when(() -> Tools.version()).thenReturn("class-level");
        when(() -> Tools.doubled(arg.anyInt())).thenReturn(1, 2);
        verify(() -> Tools.tripled(arg.anyInt())).thenReturn(3);   // every test must call this once
    }

    @BeforeEach
    void perTestDefaults() {
        assertNotNull(greeter, "instance handle is bound before @BeforeEach");
        when(() -> greeter.getGreeting()).thenReturn("each");
    }

    @Test
    void firstTestSeesClassLevelAndPerTestDefaults() {
        assertEquals("class-level", Tools.version());
        assertEquals("each", new Greeter().getGreeting());
        assertEquals(1, Tools.doubled(9));
        assertEquals(2, Tools.doubled(9));
        assertEquals(3, Tools.tripled(9));
    }

    /** The sequence starts over: class-level chains are templates, instantiated per test. */
    @Test
    void secondTestGetsFreshSequenceCounters() {
        assertEquals(1, Tools.doubled(9));
        assertEquals(2, Tools.doubled(9));
        assertEquals(3, Tools.tripled(9));
    }

    /** A test overrides a class-level default; the override is gone in the next test. */
    @Test
    void testCanOverrideClassLevelDefault() {
        when(() -> Tools.version()).thenReturn("overridden");
        assertEquals("overridden", Tools.version());
        assertEquals(3, Tools.tripled(9));
    }

    @Test
    void overrideDoesNotLeakIntoNextTest() {
        assertEquals("class-level", Tools.version());
        assertEquals(3, Tools.tripled(9));
    }
}
