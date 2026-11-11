package org.openbrod.stuntmock.unit.fixtures;

import static org.openbrod.stuntmock.Stunt.dump;
import static org.openbrod.stuntmock.Stunt.when;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.openbrod.stuntmock.StubPartially;
import org.openbrod.stuntmock.StuntExtension;
import org.openbrod.stuntmock.fakes.Tools;
import org.openbrod.stuntmock.time.FrozenClock;

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
