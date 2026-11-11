package org.openbrod.stuntmock.unit.fixtures;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.openbrod.stuntmock.Stub;
import org.openbrod.stuntmock.StuntExtension;
import org.openbrod.stuntmock.fakes.PriceService;

/** Not a test of the suite: its class-level declaration fails (interface without resolver), so beforeAll throws. */
@ExtendWith(StuntExtension.class)
@Stub(PriceService.class)
public class BrokenDeclarationFixture {

    @Test
    void neverRuns() {
    }
}
