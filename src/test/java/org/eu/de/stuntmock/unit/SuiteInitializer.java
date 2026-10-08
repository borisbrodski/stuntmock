package org.eu.de.stuntmock.unit;

import static org.eu.de.stuntmock.Stunt.stubPartially;
import static org.eu.de.stuntmock.Stunt.when;

import java.util.concurrent.atomic.AtomicInteger;

import org.eu.de.stuntmock.StuntInitializer;
import org.eu.de.stuntmock.StuntSettings;
import org.eu.de.stuntmock.fakes.Registry;

/**
 * The suite's project setup, discovered through {@code META-INF/services}: runs once before the first test
 * class, registers defaults every test class starts with. {@code InitializationTest} checks both.
 */
public final class SuiteInitializer implements StuntInitializer {

    static final AtomicInteger RUNS = new AtomicInteger();

    @Override
    public void initialize() {
        RUNS.incrementAndGet();
        StuntSettings.defaults("suite", () -> {
            stubPartially(Registry.class);
            when(() -> Registry.active()).thenReturn(true);
        });
    }
}
