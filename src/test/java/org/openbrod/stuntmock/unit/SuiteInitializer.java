package org.openbrod.stuntmock.unit;

import static org.openbrod.stuntmock.Stunt.stubPartially;
import static org.openbrod.stuntmock.Stunt.when;

import java.util.concurrent.atomic.AtomicInteger;

import org.openbrod.stuntmock.StuntInitializer;
import org.openbrod.stuntmock.StuntSettings;
import org.openbrod.stuntmock.fakes.Registry;

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
