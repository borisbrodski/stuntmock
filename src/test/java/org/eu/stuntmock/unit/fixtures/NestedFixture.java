package org.eu.stuntmock.unit.fixtures;

import static org.eu.stuntmock.Stunt.verify;
import static org.eu.stuntmock.Stunt.when;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.eu.stuntmock.StubPartially;
import org.eu.stuntmock.StuntExtension;
import org.eu.stuntmock.fakes.Greeter;
import org.eu.stuntmock.fakes.Tools;
import org.eu.stuntmock.time.FrozenClock;

/**
 * Not a test of the suite: {@code @Nested} classes open a class scope inside the enclosing class's scope and
 * see its declarations; leaving the nested class returns to the enclosing scope.
 */
@ExtendWith({StuntExtension.class, FrozenClock.class})
@StubPartially(Tools.class)
public class NestedFixture {

    static java.time.ZonedDateTime outerClock;

    @org.junit.jupiter.api.BeforeAll
    static void rememberTheClock() {
        outerClock = FrozenClock.now();
    }

    @Test
    void outer() {
        when(() -> Tools.version()).thenReturn("outer");
        assertEquals("outer", Tools.version());
    }

    @Nested
    @StubPartially(Greeter.class)
    class Inner {

        @Test
        void seesTheEnclosingDeclaration() {
            assertEquals(true, FrozenClock.now().isAfter(outerClock.minusSeconds(1)));  // its own frozen clock
            when(() -> Tools.version()).thenReturn("inner");          // Tools: declared by the enclosing class
            verify(Greeter.class, g -> g.greet("x")).thenReturn("hi"); // Greeter: declared here
            assertEquals("inner", Tools.version());
            assertEquals("hi", new Greeter().greet("x"));
        }
    }

    @Test
    void outerAgain() {
        assertEquals("real-version", Tools.version());                 // real: the inner mapping is gone
        assertEquals(outerClock, FrozenClock.now());                   // the enclosing clock, restored if Inner ran first
    }
}
