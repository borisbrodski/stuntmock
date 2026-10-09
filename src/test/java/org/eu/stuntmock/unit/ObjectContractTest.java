package org.eu.stuntmock.unit;

import static org.eu.stuntmock.Stunt.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.eu.stuntmock.MissingCallError;
import org.eu.stuntmock.Stub;
import org.eu.stuntmock.StubPartially;
import org.eu.stuntmock.StuntExtension;
import org.eu.stuntmock.UnexpectedCallError;
import org.eu.stuntmock.Verify;
import org.eu.stuntmock.internal.Scope;

/**
 * {@code toString()}, {@code hashCode()} and {@code equals(Object)} are never mocked implicitly, whatever the
 * mode: production code puts declared objects into sets and messages, and Stunt's own messages print them.
 * Mapping one of them explicitly still works. Regression for an {@code UnexpectedCallError} raised from
 * {@code toString()} while Stunt rendered a failure message for a {@code @Verify} class.
 */
@ExtendWith(StuntExtension.class)
class ObjectContractTest {

    static class Keyword {
        final String name;

        Keyword(String name) {
            this.name = name;
        }

        void rename(Keyword other) {
        }

        @Override
        public String toString() {
            return "Keyword[" + name + "]";
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Keyword s && s.name.equals(name);
        }
    }

    @Test
    @StubPartially(Keyword.class)
    @Verify(Keyword.class)
    void objectContractRunsOriginalOnVerifyClass() {
        Keyword a = new Keyword("a");
        Set< Keyword > set = new HashSet<>();
        set.add(a);
        assertTrue(set.contains(new Keyword("a")));      // overridden hashCode and equals ran
        assertEquals("Keyword[a]", "" + a);               // overridden toString ran
    }

    @Test
    @Stub(Keyword.class)
    void objectContractRunsOriginalOnStubClass() {
        Keyword a = new Keyword("a");
        assertEquals("a".hashCode(), a.hashCode());
        assertEquals("Keyword[a]", a.toString());
        assertTrue(a.equals(new Keyword("a")));
    }

    /** The reported case: a matcher holding a @StubPartially @Verify object is rendered in the missing-call message. */
    @Test
    @StubPartially(Keyword.class)
    @Verify(Keyword.class)
    void failureMessageRendersDeclaredObjects() {
        Keyword a = new Keyword("a");
        Keyword b = new Keyword("b");
        verify(() -> a.rename(b));
        MissingCallError e = assertThrows(MissingCallError.class, () -> Scope.testScope().verifyAll());
        assertTrue(e.getMessage().contains("rename(Keyword[b])"), e.getMessage());
        a.rename(b);                                        // satisfy the chain for the real end-of-test check
    }

    @Test
    @StubPartially(Keyword.class)
    @Verify(Keyword.class)
    void dumpRendersDeclaredObjects() {
        Keyword a = new Keyword("a");
        when(() -> a.rename(new Keyword("b"))).thenDoNothing();
        assertTrue(dump().contains("rename(Keyword[b])"), dump());
    }

    /** Explicit mappings of the contract methods still win. */
    @Test
    @StubPartially(Keyword.class)
    @Verify(Keyword.class)
    void explicitMappingOfContractMethodsWins() {
        Keyword a = new Keyword("a");
        when(a::hashCode).thenReturn(42);
        assertEquals(42, a.hashCode());
        assertEquals("Keyword[a]", a.toString());         // still original
    }

    @Test
    @StubPartially(Keyword.class)
    @Verify(Keyword.class)
    void contractMethodCanBeForbidden() {
        Keyword a = new Keyword("a");
        verify(a::toString).never();
        UnexpectedCallError e = assertThrows(UnexpectedCallError.class, a::toString);
        assertTrue(e.getMessage().contains("never()"), e.getMessage());
        TestSupport.clearRecordedFailures();
    }
}
