package org.eu.de.stuntmock.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

import org.eu.de.stuntmock.unit.fixtures.BrokenDeclarationFixture;
import org.eu.de.stuntmock.unit.fixtures.HealthyFixture;
import org.eu.de.stuntmock.unit.fixtures.NestedFixture;
import org.eu.de.stuntmock.unit.fixtures.StaticNestedClockFixture;

/**
 * Scopes never leak from one test class into the next: a class whose {@code beforeAll} fails leaves nothing
 * behind, a stale scope that somehow survived is closed with a warning when the next class starts, and
 * {@code @Nested} classes nest their scopes. Run through the launcher on a fresh thread, class after class.
 */
class ScopeRecoveryTest {

    /** The broken class fails once; the healthy class that follows on the same thread is untouched by it. */
    @Test
    void aFailedBeforeAllLeavesNoScopeBehind() {
        TestExecutionSummary summary = Launch.summaryOf(BrokenDeclarationFixture.class, HealthyFixture.class);
        assertEquals(1, summary.getTotalFailureCount(), describe(summary));
        assertTrue(String.valueOf(summary.getFailures().get(0).getException()).contains("PriceService"), describe(summary));
        assertEquals(1, summary.getTestsSucceededCount(), describe(summary));
    }

    @Test
    void nestedClassesNestTheirScopes() {
        assertNull(Launch.firstFailure(NestedFixture.class), String.valueOf(Launch.firstFailure(NestedFixture.class)));
    }

    /**
     * A {@code static} nested class is a top-level class to JUnit; Gradle runs it before its enclosing class.
     * Its frozen clock must not be mistaken for the enclosing class's when that one starts afterwards.
     */
    @Test
    void aStaticNestedClassRunFirstLeavesNoFrozenClockBehind() {
        TestExecutionSummary summary = Launch.summaryOf(StaticNestedClockFixture.Standalone.class, StaticNestedClockFixture.class);
        assertEquals(0, summary.getTotalFailureCount(), describe(summary));
        assertEquals(2, summary.getTestsSucceededCount(), describe(summary));
    }

    private static String describe(TestExecutionSummary summary) {
        StringBuilder sb = new StringBuilder();
        for (TestExecutionSummary.Failure f : summary.getFailures()) {
            sb.append(f.getTestIdentifier().getDisplayName()).append(" -> ").append(f.getException()).append('\n');
        }
        return sb.toString();
    }
}
