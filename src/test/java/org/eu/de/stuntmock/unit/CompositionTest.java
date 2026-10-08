package org.eu.de.stuntmock.unit;

import static org.eu.de.stuntmock.Stunt.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.eu.de.stuntmock.WithStunt;
import org.eu.de.stuntmock.Stub;
import org.eu.de.stuntmock.StubPartially;
import org.eu.de.stuntmock.StuntException;
import org.eu.de.stuntmock.StuntExtension;
import org.eu.de.stuntmock.UnexpectedCallError;
import org.eu.de.stuntmock.Verify;
import org.eu.de.stuntmock.unit.fixtures.AuditFixture;
import org.eu.de.stuntmock.unit.fixtures.VerifyAloneFixture;
import org.eu.de.stuntmock.unit.fixtures.PolicyAtClassLevelFixture;
import org.eu.de.stuntmock.unit.fixtures.StrictForOneTestFixture;

/**
 * A declaration answers two questions: what a mapped call without terminal gets (the policy, {@code @Stub} =
 * default value, {@code @StubPartially} = real code) and whether an unmapped call is allowed ({@code @Verify}
 * makes it fail). The two compose: {@code @Stub @Verify} is a strict mock, {@code @StubPartially @Verify} a
 * strict audit. {@code @Verify} alone is rejected everywhere, so that a reader never has to guess. Within either
 * composition, single methods are overridden with {@code thenReturn}/{@code thenDoNothing} (replace) or
 * {@code thenCallOriginal} (let through).
 */
@ExtendWith(StuntExtension.class)
class CompositionTest implements WithStunt {

    static class Account {
        long balance = 100;
        String note;

        long getBalance() {
            return balance;
        }

        void book(long betrag) {
            balance += betrag;
        }

        void setNote(String note) {
            this.note = note;
        }

        static String currency() {
            return "EUR";
        }
    }

    // ---------------------------------------------------------------- the four cells

    /** {@code @Stub} alone: defaults, unmapped calls allowed. */
    @Test
    @Stub(Account.class)
    void stub() {
        Account k = new Account();
        assertEquals(0L, k.getBalance());
        k.book(5);                                   // allowed, skipped
        assertEquals(100L, k.balance);
    }

    /** {@code @StubPartially} alone: real code, unmapped calls allowed. */
    @Test
    @StubPartially(Account.class)
    void stubPartially() {
        Account k = new Account();
        assertEquals(100L, k.getBalance());
        k.book(5);
        assertEquals(105L, k.balance);
    }

    /** {@code @Stub @Verify}: strict mock. Mapped calls without terminal answer the default value. */
    @Test
    @Stub(Account.class)
    @Verify(Account.class)
    void strictMock() {
        verify(Account.class, k -> k.book(anyLong()));
        Account k = new Account();
        k.book(5);
        assertEquals(100L, k.balance);                   // the body was skipped: a stub
        assertThrows(UnexpectedCallError.class, k::getBalance);
        TestSupport.clearRecordedFailures();
    }

    /** {@code @StubPartially @Verify}: strict audit. Mapped calls without terminal run the real code. */
    @Test
    @StubPartially(Account.class)
    @Verify(Account.class)
    void strictAudit() {
        verify(Account.class, k -> k.book(anyLong()));
        Account k = new Account();
        k.book(5);
        assertEquals(105L, k.balance);                   // the body ran: an audit
        assertThrows(UnexpectedCallError.class, k::getBalance);
        TestSupport.clearRecordedFailures();
    }

    // ---------------------------------------------------------------- per-method overrides

    /** On a strict mock, one method is let through with {@code thenCallOriginal()}. */
    @Test
    void strictMockLetsAMethodThrough() {
        Account k = verify(stub(new Account()));
        when(k::getBalance).thenCallOriginal().anyTimes();
        verify(() -> k.book(anyLong()));
        assertEquals(100L, k.getBalance());              // real
        k.book(5);
        assertEquals(100L, k.balance);                   // still a stub
    }

    /** On a strict audit, one method is replaced with {@code thenReturn}/{@code thenDoNothing}. */
    @Test
    void strictAuditReplacesAMethod() {
        Account k = verify(stubPartially(new Account()));
        when(k::getBalance).thenReturn(7L).anyTimes();
        verify(() -> k.book(anyLong())).thenDoNothing();
        when(() -> k.setNote(anyString())).anyTimes();
        assertEquals(7L, k.getBalance());                // replaced
        k.book(5);
        assertEquals(100L, k.balance);                   // suppressed
        k.setNote("n");
        assertEquals("n", k.note);                     // real, allowed
    }

    // ---------------------------------------------------------------- the programmatic forms

    @Test
    void programmaticCompositionOnClasses() {
        Account strictMock = verify(stub(Account.class));
        assertTrue(dump().contains("@Stub @Verify " + Account.class.getName()), dump());
        when(() -> strictMock.getBalance()).thenReturn(1L);
        assertEquals(1L, new Account().getBalance());
        assertThrows(UnexpectedCallError.class, () -> new Account().book(1));
        TestSupport.clearRecordedFailures();
    }

    @Test
    void programmaticCompositionOnObjects() {
        Account a = verify(stub(new Account()));
        Account b = verify(stubPartially(new Account()));
        Account c = new Account();
        when(a::getBalance).anyTimes();
        when(b::getBalance).anyTimes();
        assertEquals(0L, a.getBalance());                // stub policy
        assertEquals(100L, b.getBalance());              // partial policy
        assertEquals(100L, c.getBalance());              // undeclared: real
        assertThrows(UnexpectedCallError.class, () -> a.book(1));
        assertThrows(UnexpectedCallError.class, () -> b.book(1));
        TestSupport.clearRecordedFailures();
    }

    /** Statics follow the class declaration, never an object's. */
    @Test
    void staticsFollowTheClassDeclaration() {
        verify(stub(Account.class));
        when(() -> Account.currency()).thenReturn("CHF");
        assertEquals("CHF", Account.currency());
        Account audited = verify(stubPartially(new Account()));
        assertEquals("CHF", Account.currency());         // the object's declaration does not touch statics
        assertFalse(dump().contains("UNEXPECTED"), dump());
        assertNull(audited.note);
    }

    // ---------------------------------------------------------------- the four verbs and the four annotations

    /** {@code verify} alone is the strict mock, {@code audit} the strict audit; the composed forms stay valid. */
    @Test
    void shortVerbsEqualTheComposedForms() {
        Account mock = verify(Account.class);
        assertTrue(dump().contains("@Stub @Verify " + Account.class.getName()), dump());
        Account audited = audit(new Account());
        assertTrue(dump().contains("@StubPartially @Verify instance"), dump());
        when(mock::getBalance).anyTimes();
        when(audited::getBalance).anyTimes();
        assertEquals(0L, new Account().getBalance());        // strict mock of the class: default value
        assertEquals(100L, audited.getBalance());            // strict audit of the object: real
    }

    /** {@code audit} on a class already declared as a stub is a contradiction and says so. */
    @Test
    void auditOnAStubIsRejected() {
        stub(Account.class);
        StuntException e = assertThrows(StuntException.class, () -> audit(Account.class));
        assertTrue(e.getMessage().contains("already declared as @Stub"), e.getMessage());
        assertTrue(e.getMessage().contains("Use verify(...) to make a stub strict"), e.getMessage());
    }

    /** {@code verify} on a partial mock keeps the real-code policy: it is the composed strict audit. */
    @Test
    void verifyOnAPartialKeepsThePolicy() {
        Account k = verify(stubPartially(new Account()));
        when(k::getBalance).anyTimes();
        assertEquals(100L, k.getBalance());
    }

    @Test
    void verifyAnnotationAloneIsAStrictMock() {
        assertNull(Launch.firstFailure(VerifyAloneFixture.class), String.valueOf(Launch.firstFailure(VerifyAloneFixture.class)));
    }

    @Test
    void auditAnnotationIsAStrictAudit() {
        assertNull(Launch.firstFailure(AuditFixture.class), String.valueOf(Launch.firstFailure(AuditFixture.class)));
    }

    /** {@code @Verify} on a method next to a class-level {@code @StubPartially}: a policy conflict, say {@code @Audit}. */
    @Test
    void verifyOnMethodAgainstClassLevelPartialIsAConflict() {
        Throwable e = Launch.firstFailure(PolicyAtClassLevelFixture.class);
        assertTrue(e != null && e.getMessage().contains("already declared as @StubPartially"), String.valueOf(e));
    }

    /** Repeating the policy with @Verify on one method makes a class-level declaration strict for that test only. */
    @Test
    void strictForOneTestOnly() {
        assertNull(Launch.firstFailure(StrictForOneTestFixture.class));
    }
}
