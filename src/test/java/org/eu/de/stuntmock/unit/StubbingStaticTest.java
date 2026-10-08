package org.eu.de.stuntmock.unit;

import static org.eu.de.stuntmock.Stunt.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.eu.de.stuntmock.Stub;
import org.eu.de.stuntmock.StubPartially;
import org.eu.de.stuntmock.StuntExtension;
import org.eu.de.stuntmock.fakes.Helper;
import org.eu.de.stuntmock.fakes.Tools;

/** Stubbing static methods: full and partial, defaults, self-calls, and reaching a second class. */
@ExtendWith(StuntExtension.class)
@Stub(Tools.class)
@StubPartially(Helper.class)
class StubbingStaticTest {

    @BeforeEach
    void reset() {
        Tools.counter = 0;
    }

    @Test
    void mappedStaticReturnsStub() {
        when(() -> Tools.version()).thenReturn("1.0");
        assertEquals("1.0", Tools.version());
    }

    /** Every primitive and reference return type has a default on a {@code @Stub} class. */
    @Test
    void unmappedStaticReturnsDefaults() {
        assertNull(Tools.version());
        assertEquals(0, Tools.doubled(3));
        assertEquals(0L, Tools.total(1, 2));
        assertFalse(Tools.enabled());
        assertEquals(List.of(), Tools.names());
    }

    /** Arguments given as plain values are compared with equals. */
    @Test
    void plainArgumentsSelectTheCall() {
        when(() -> Tools.doubled(2)).thenReturn(40);
        assertEquals(40, Tools.doubled(2));
        assertEquals(0, Tools.doubled(3));
    }

    @Test
    void matchersSelectTheCall() {
        when(() -> Tools.doubled(arg.intThat(i -> i > 10))).thenReturn(-1);
        when(() -> Tools.describe(arg.anyString(), arg.eq(2))).thenReturn("two");
        assertEquals(-1, Tools.doubled(11));
        assertEquals(0, Tools.doubled(9));
        assertEquals("two", Tools.describe("x", 2));
        assertNull(Tools.describe("x", 3));
    }

    /** On a partial class, unmapped statics run for real and mapped ones do not. */
    @Test
    void partialStaticMixesRealAndStubbed() {
        assertEquals("real-help", Helper.help());
        when(() -> Helper.help()).thenReturn("stubbed");
        assertEquals("stubbed", Helper.help());
    }

    /** A static of a {@code @Stub} class reaching a {@code @StubPartially} class: each class keeps its mode. */
    @Test
    void reachingAnotherDeclaredClassAppliesItsMode() {
        when(() -> Tools.fromHelper()).thenCallOriginal();
        assertEquals("real-help", Tools.fromHelper());
        when(() -> Helper.help()).thenReturn("stubbed");
        assertEquals("stubbed", Tools.fromHelper());
    }

    /** {@code thenCallOriginal} lets one method of a {@code @Stub} class through. */
    @Test
    void callOriginalOnStubClass() {
        when(() -> Tools.doubled(arg.anyInt())).thenCallOriginal();
        assertEquals(6, Tools.doubled(3));
        assertEquals(0, Tools.tripled(3));
    }
}
