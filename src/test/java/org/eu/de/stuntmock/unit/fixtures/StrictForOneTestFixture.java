package org.eu.de.stuntmock.unit.fixtures;

import static org.eu.de.stuntmock.Stunt.dump;
import static org.eu.de.stuntmock.Stunt.verify;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;

import org.eu.de.stuntmock.StubPartially;
import org.eu.de.stuntmock.StuntExtension;
import org.eu.de.stuntmock.Verify;

/**
 * Not a test of the suite: a class declared at class level is made strict for one test only by repeating the
 * policy with {@code @Verify} on that method; the next test is lenient again. Both tests must pass.
 */
@ExtendWith(StuntExtension.class)
@StubPartially(Account.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class StrictForOneTestFixture {

    @Test
    @Order(1)
    @StubPartially(Account.class)
    @Verify(Account.class)
    void strictHere() {
        Account k = new Account();
        verify(() -> k.book(1L));
        k.book(1L);
        assertTrue(dump().contains("@StubPartially @Verify " + Account.class.getName()), dump());
    }

    @Test
    @Order(2)
    void lenientAgain() {
        Account k = new Account();
        assertEquals(100L, k.getBalance());               // unmapped and allowed again
        assertFalse(dump().contains("@Verify"), dump());
    }
}
