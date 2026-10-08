package org.eu.de.stuntmock.unit.fixtures;

import static org.eu.de.stuntmock.Stunt.dump;
import static org.eu.de.stuntmock.Stunt.verify;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.eu.de.stuntmock.StuntExtension;
import org.eu.de.stuntmock.Verify;

/** Not a test of the suite: {@code @Verify} alone, on class, method and parameter, is a strict mock. */
@ExtendWith(StuntExtension.class)
@Verify(Account.class)
public class VerifyAloneFixture {

    @Test
    void classLevel() {
        verify(Account.class, k -> k.book(1L));
        Account k = new Account();
        k.book(1L);
        assertEquals(100L, k.balance);                                           // stub policy: body skipped
        assertTrue(dump().contains("@Stub @Verify " + Account.class.getName()), dump());
    }

    @Test
    void parameter(@Verify Other other) {
        verify(() -> other.touch());
        other.touch();
        assertEquals(0, other.touched);
    }

    public static class Other {
        int touched;

        void touch() {
            touched++;
        }
    }
}
