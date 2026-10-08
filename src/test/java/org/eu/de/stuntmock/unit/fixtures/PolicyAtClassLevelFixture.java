package org.eu.de.stuntmock.unit.fixtures;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.eu.de.stuntmock.StubPartially;
import org.eu.de.stuntmock.StuntExtension;
import org.eu.de.stuntmock.Verify;

/** Not a test of the suite: the policy sits on the class, the @Verify on the method; rejected. */
@ExtendWith(StuntExtension.class)
@StubPartially(Account.class)
public class PolicyAtClassLevelFixture {

    @Test
    @Verify(Account.class)
    void never() {
    }
}
