package org.eu.de.stuntmock.unit;

import org.eu.de.stuntmock.internal.Scope;

/** Helpers for Stunt's own tests. */
final class TestSupport {

    private TestSupport() {
    }

    /**
     * A test that deliberately provokes a fail-fast error would be failed again by the end-of-test check; call
     * this after asserting the error to keep the test green.
     */
    static void clearRecordedFailures() {
        Scope.testScope().failures().clear();
    }
}
