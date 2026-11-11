package org.openbrod.stuntmock.unit.fixtures;

import static org.openbrod.stuntmock.Stunt.dump;
import static org.openbrod.stuntmock.Stunt.verify;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.openbrod.stuntmock.Audit;
import org.openbrod.stuntmock.StuntExtension;

/** Not a test of the suite: {@code @Audit}, on class, method and field, is a strict audit. */
@ExtendWith(StuntExtension.class)
@Audit(Account.class)
public class AuditFixture {

    @Audit VerifyAloneFixture.Other other;

    @Test
    void classLevel() {
        verify(Account.class, k -> k.book(1L));
        Account k = new Account();
        k.book(1L);
        assertEquals(101L, k.balance);                                           // real code ran
        assertTrue(dump().contains("@StubPartially @Verify " + Account.class.getName()), dump());
    }

    @Test
    void field() {
        verify(() -> other.touch());
        VerifyAloneFixture.Other o = new VerifyAloneFixture.Other();
        o.touch();
        assertEquals(1, o.touched);
    }
}
