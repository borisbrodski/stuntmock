package org.eu.stuntmock;

/**
 * A project's Stunt setup, discovered through {@code META-INF/services/org.eu.stuntmock.StuntInitializer}
 * and run once, right before the first test class opens, whatever the order in which classes are loaded. The
 * alternative to calling {@link StuntSettings#initialization(Runnable)} from a static initializer.
 *
 * <pre>{@code
 * public final class ProjectStuntInitializer implements StuntInitializer {
 *     public void initialize() {
 *         StuntSettings.addTypeResolver(type -> isDao(type) ? DAO.get(type) : null);
 *         StuntSettings.ignoreCallSites(WithStunt.class);
 *         StuntSettings.defaults("project", () -> {
 *             stubPartially(ServiceLocator.class);
 *             when(() -> ServiceLocator.get(any())).thenDo((Invocation inv) -> TestContainer.lookup(inv.arg(0)));
 *         });
 *     }
 * }
 * }</pre>
 */
public interface StuntInitializer {

    void initialize();
}
