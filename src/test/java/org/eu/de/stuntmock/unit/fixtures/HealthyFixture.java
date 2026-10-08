package org.eu.de.stuntmock.unit.fixtures;

import static org.eu.de.stuntmock.Stunt.dump;
import static org.eu.de.stuntmock.Stunt.when;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.eu.de.stuntmock.StubPartially;
import org.eu.de.stuntmock.StuntExtension;
import org.eu.de.stuntmock.fakes.Tools;
import org.eu.de.stuntmock.time.FrozenClock;

/** Not a test of the suite: an ordinary class that must pass after a broken one ran on the same thread. */
@ExtendWith({StuntExtension.class, FrozenClock.class})
@StubPartially(Tools.class)
public class HealthyFixture {

    @Test
    void runsCleanly() {
        when(() -> Tools.version()).thenReturn("fresh");
        assertEquals("fresh", Tools.version());
        assertFalse(dump().contains("BrokenDeclarationFixture"), dump());
        assertFalse(dump().contains("PriceService"), dump());
    }
}
