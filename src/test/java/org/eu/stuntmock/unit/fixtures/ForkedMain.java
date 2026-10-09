package org.eu.stuntmock.unit.fixtures;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

/** Runs one test class in a fresh JVM, for tests of once-per-JVM behaviour; exit code 0 means all tests passed. */
public final class ForkedMain {

    private ForkedMain() {
    }

    public static void main(String[] args) throws Exception {
        SummaryGeneratingListener listener = new SummaryGeneratingListener();
        LauncherFactory.create().execute(LauncherDiscoveryRequestBuilder.request()
            .selectors(selectClass(Class.forName(args[0]))).build(), listener);
        TestExecutionSummary summary = listener.getSummary();
        for (TestExecutionSummary.Failure failure : summary.getFailures()) {
            System.err.println("FAILURE " + failure.getTestIdentifier().getDisplayName() + ": " + failure.getException());
        }
        System.exit(summary.getTotalFailureCount() == 0 && summary.getTestsSucceededCount() > 0 ? 0 : 1);
    }
}
