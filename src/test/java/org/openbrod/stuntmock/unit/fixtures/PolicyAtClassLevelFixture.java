package org.openbrod.stuntmock.unit.fixtures;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.openbrod.stuntmock.StubPartially;
import org.openbrod.stuntmock.StuntExtension;
import org.openbrod.stuntmock.Verify;

/** Not a test of the suite: the policy sits on the class, the @Verify on the method; rejected. */
@ExtendWith(StuntExtension.class)
@StubPartially(Account.class)
public class PolicyAtClassLevelFixture {

    @Test
    @Verify(Account.class)
    void never() {
    }
}
