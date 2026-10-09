package org.eu.stuntmock.unit;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import java.util.List;

import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

/**
 * Runs a fixture test class through the JUnit launcher on its own thread (Stunt scopes are thread-local) and
 * returns the failures, so that a test can assert how the extension rejects a wrong declaration.
 */
final class Launch {

    private Launch() {
    }

    static List< TestExecutionSummary.Failure > failuresOf(Class< ? >... fixtures) {
        return summaryOf(fixtures).getFailures();
    }

    /** Runs the fixtures, in order, on one fresh thread and returns the summary. */
    static TestExecutionSummary summaryOf(Class< ? >... fixtures) {
        SummaryGeneratingListener listener = new SummaryGeneratingListener();
        Thread thread = new Thread(() -> {
            LauncherDiscoveryRequestBuilder builder = LauncherDiscoveryRequestBuilder.request();
            for (Class< ? > fixture : fixtures) {
                builder.selectors(selectClass(fixture));
            }
            LauncherDiscoveryRequest request = builder.build();
            Launcher launcher = LauncherFactory.create();
            launcher.execute(request, listener);
        }, "stunt-fixture-launcher");
        thread.start();
        try {
            thread.join();
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
        return listener.getSummary();
    }

    /** The first failure's exception, or {@code null} if the fixture passed. */
    static Throwable firstFailure(Class< ? > fixture) {
        List< TestExecutionSummary.Failure > failures = failuresOf(fixture);
        return failures.isEmpty() ? null : failures.get(0).getException();
    }
}
