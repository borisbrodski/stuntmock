package org.eu.de.stuntmock.unit;

import static org.eu.de.stuntmock.Stunt.*;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.eu.de.stuntmock.StubPartially;
import org.eu.de.stuntmock.StuntExtension;
import org.eu.de.stuntmock.fakes.Tools;

/** Partial stubbing of statics: real code by default, mapped calls replaced, self-calls dispatched. */
@ExtendWith(StuntExtension.class)
@StubPartially(Tools.class)
class StubbingPartialStaticTest {

    @BeforeEach
    void reset() {
        Tools.counter = 0;
    }

    @Test
    void unmappedStaticRunsForReal() {
        assertEquals("real-version", Tools.version());
        assertEquals(6, Tools.doubled(3));
    }

    @Test
    void mappedStaticIsReplaced() {
        when(() -> Tools.doubled(arg.anyInt())).thenReturn(100);
        assertEquals(100, Tools.doubled(3));
        assertEquals(9, Tools.tripled(3));
    }

    /** A void static mapped with {@code thenDoNothing} skips its body. */
    @Test
    void voidStaticCanBeSkipped() {
        Tools.counter = 5;
        when(() -> Tools.reset()).thenDoNothing();
        Tools.reset();
        assertEquals(5, Tools.counter);
    }

    /** {@code sixfold} calls {@code doubled} and {@code tripled} internally: inner calls are dispatched too. */
    @Test
    void selfCallsAreDispatched() {
        when(() -> Tools.tripled(arg.anyInt())).thenReturn(10);
        assertEquals(20, Tools.sixfold(1));
        verify(() -> Tools.doubled(10)).thenCallOriginal();
        assertEquals(20, Tools.sixfold(1));
    }

    /** Stateful static: the real code keeps running between stubbed calls. */
    @Test
    void realStateSurvivesAroundMappedCalls() {
        assertEquals(1, Tools.count());
        when(() -> Tools.count()).thenReturn(99).times(1);
        assertEquals(99, Tools.count());
        assertEquals(2, Tools.count()); // stub exhausted: real code again, counter untouched by the stub
    }
}
