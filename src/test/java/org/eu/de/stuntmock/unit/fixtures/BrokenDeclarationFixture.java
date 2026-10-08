package org.eu.de.stuntmock.unit.fixtures;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.eu.de.stuntmock.Stub;
import org.eu.de.stuntmock.StuntExtension;
import org.eu.de.stuntmock.fakes.PriceService;

/** Not a test of the suite: its class-level declaration fails (interface without resolver), so beforeAll throws. */
@ExtendWith(StuntExtension.class)
@Stub(PriceService.class)
public class BrokenDeclarationFixture {

    @Test
    void neverRuns() {
    }
}
