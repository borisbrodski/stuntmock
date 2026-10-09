package org.eu.stuntmock.unit.fixtures;

import static org.eu.stuntmock.Stunt.dump;
import static org.eu.stuntmock.Stunt.stubPartially;
import static org.eu.stuntmock.Stunt.when;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.eu.stuntmock.StuntExtension;
import org.eu.stuntmock.StuntSettings;
import org.eu.stuntmock.fakes.Tools;

/**
 * Not a test of the suite: run in a fresh JVM by {@code InitializationTest}. A static initializer, the way a
 * shared base test class would do it, registers initialization that counts its runs and defaults every class
 * starts with; both tests see the defaults, the initialization ran once.
 */
@ExtendWith(StuntExtension.class)
public class StaticInitializationFixture {

    static int initializations;

    static {
        StuntSettings.initialization(() -> {
            initializations++;
            StuntSettings.defaults("fixture", () -> {
                stubPartially(Tools.class);
                when(() -> Tools.version()).thenReturn("from defaults");
            });
        });
    }

    @Test
    void defaultsApplyHere() {
        assertEquals("from defaults", Tools.version());
        assertTrue(dump().contains("[fixture] @StubPartially Tools"), dump());
    }

    @Test
    void andHereToo() {
        assertEquals("from defaults", Tools.version());
        assertEquals(1, initializations);
    }
}
