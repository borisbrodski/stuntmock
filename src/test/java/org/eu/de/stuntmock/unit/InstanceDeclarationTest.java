package org.eu.de.stuntmock.unit;

import static org.eu.de.stuntmock.Stunt.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.eu.de.stuntmock.Audit;
import org.eu.de.stuntmock.Stub;
import org.eu.de.stuntmock.StubPartially;
import org.eu.de.stuntmock.StuntException;
import org.eu.de.stuntmock.StuntExtension;
import org.eu.de.stuntmock.UnexpectedCallError;
import org.eu.de.stuntmock.Verify;

/**
 * Declaring one object instead of a class: {@code verify(m2)}, {@code stub(m2)}, {@code stubPartially(m2)}. Only
 * calls on that object are intercepted in the given mode; other instances of the class keep the class's own
 * declaration, or stay real. An instance declaration wins over the declaration of its class, so it can also
 * exempt one object from a class-level {@code @Verify}.
 */
@ExtendWith(StuntExtension.class)
class InstanceDeclarationTest {

    private final List< String > titles = new ArrayList<>();

    static class MyClass {
        String title;
        String text;

        void setTitle(String title) {
            this.title = title;
        }

        void setText(String text) {
            this.text = text;
        }

        String getTitle() {
            return title;
        }

        static String tag() {
            return "real";
        }
    }

    static class Sub extends MyClass {
        void extra() {
        }
    }

    static class Sibling extends MyClass {
    }

    /** The requested behaviour, verbatim. */
    @Test
    void verifyOneInstanceOnly() {
        var m1 = new MyClass();
        var m2 = new MyClass();

        verify(stubPartially(m2));
        verify(() -> m2.setText(arg.any()));

        m1.setTitle("test");                                                   // not declared: real
        m2.setText("test");                                                    // expected
        UnexpectedCallError e = assertThrows(UnexpectedCallError.class, () -> m2.setTitle("test"));
        assertTrue(e.getMessage().contains("setTitle(\"test\")"), e.getMessage());
        assertEquals("test", m1.title);
        TestSupport.clearRecordedFailures();
    }

    @Test
    void stubOneInstanceOnly() {
        var m1 = new MyClass();
        var m2 = stub(new MyClass());
        m1.setTitle("a");
        m2.setTitle("b");                                                      // default: body skipped
        assertEquals("a", m1.title);
        assertNull(m2.title);
        assertNull(m2.getTitle());
    }

    /** {@code stubPartially(m1)} exempts one object from a class-level {@code @Verify}. */
    @Test
    @StubPartially(MyClass.class)
    @Verify(MyClass.class)
    void instanceDeclarationOverridesTheClassMode() {
        var m1 = stubPartially(new MyClass());
        var m2 = new MyClass();
        m1.setTitle("ok");                                                     // exempt: original runs
        assertEquals("ok", m1.title);
        assertThrows(UnexpectedCallError.class, () -> m2.setTitle("x"));       // class rule still applies
        TestSupport.clearRecordedFailures();
    }

    /** A class-level {@code @Stub} plus {@code verify(m2)}: m2 is strict, the others return defaults. */
    @Test
    @Stub(MyClass.class)
    void verifyInstanceOnAStubClass() {
        var m1 = new MyClass();
        var m2 = verify(stubPartially(new MyClass()));
        m1.setTitle("x");
        assertNull(m1.title);                                                  // @Stub default
        assertThrows(UnexpectedCallError.class, () -> m2.setTitle("x"));
        TestSupport.clearRecordedFailures();
    }

    /** The declaring statement returns the object, so it can be inlined. */
    @Test
    void returnsTheObject() {
        var m = new MyClass();
        assertSame(m, stubPartially(m));
        assertSame(m, verify(m));
    }

    /** {@code verify(obj)} alone is a strict mock of the object, {@code audit(obj)} a strict audit. */
    @Test
    void verifyAloneIsAStrictMockAuditAStrictAudit() {
        var mock = verify(new MyClass());
        var audited = audit(new MyClass());
        when(() -> mock.setTitle(arg.any())).anyTimes();
        when(() -> audited.setTitle(arg.any())).anyTimes();
        mock.setTitle("m");
        audited.setTitle("s");
        assertNull(mock.title);                                                // mock: body skipped
        assertEquals("s", audited.title);                                        // audit: body ran
        assertThrows(UnexpectedCallError.class, mock::getTitle);
        assertThrows(UnexpectedCallError.class, audited::getTitle);
        TestSupport.clearRecordedFailures();
    }

    // ---------------------------------------------------------------- inheritance

    /** A method inherited from the superclass is intercepted for the declared subclass instance only. */
    @Test
    void inheritedMethodOnDeclaredSubclassInstance() {
        var sub = new Sub();
        var otherSub = new Sub();
        var sibling = new Sibling();
        var base = new MyClass();
        audit(sub);
        verify(() -> sub.setText(arg.any()));
        sub.setText("x");
        otherSub.setTitle("o");
        sibling.setTitle("s");
        base.setTitle("b");
        assertEquals("o", otherSub.title);
        assertEquals("s", sibling.title);
        assertEquals("b", base.title);
        assertThrows(UnexpectedCallError.class, () -> sub.setTitle("x"));
        TestSupport.clearRecordedFailures();
    }

    /** A class-level audit of the superclass and a lenient instance declaration of a subclass object. */
    @Test
    @Audit(MyClass.class)
    void instanceOfSubclassExemptedFromSuperclassAudit() {
        var sub = stubPartially(new Sub());
        var otherSub = new Sub();
        sub.setTitle("ok");
        sub.extra();
        assertEquals("ok", sub.title);
        assertThrows(UnexpectedCallError.class, () -> otherSub.extra());
        TestSupport.clearRecordedFailures();
    }

    /** Statics are never covered by an instance declaration. */
    @Test
    void staticsStayReal() {
        audit(new MyClass());
        assertEquals("real", MyClass.tag());
    }

    // ---------------------------------------------------------------- misuse

    @Test
    void redeclaringTheSameObjectInAnotherModeIsRejected() {
        var m = audit(new MyClass());
        verify(m);                                                             // adds nothing: already strict
        stubPartially(m);                                                      // same policy: fine
        StuntException e = assertThrows(StuntException.class, () -> stub(m));
        assertTrue(e.getMessage().contains("already declared as @StubPartially"), e.getMessage());
    }

    /** The class may still be declared afterwards; the instance keeps its own mode. */
    @Test
    void classMayBeDeclaredAfterTheInstance() {
        var m2 = audit(new MyClass());
        var m1 = new MyClass();
        stubPartially(MyClass.class);
        m1.setTitle("x");
        assertEquals("x", m1.title);
        assertThrows(UnexpectedCallError.class, () -> m2.setTitle("x"));
        TestSupport.clearRecordedFailures();
    }

    @Test
    void typeHandleIsNotAnInstance() {
        MyClass handle = stub(MyClass.class);
        StuntException e = assertThrows(StuntException.class, () -> stubPartially(handle));
        assertTrue(e.getMessage().contains("is the handle of @Stub"), e.getMessage());
    }

    /** {@code verify(handle)} is the programmatic {@code @Stub @Verify} for the whole class. */
    @Test
    void verifyOnAHandleMakesTheClassStrict() {
        MyClass handle = verify(stub(MyClass.class));
        when(() -> handle.getTitle()).thenReturn("stubbed");
        var m = new MyClass();
        assertEquals("stubbed", m.getTitle());
        assertThrows(UnexpectedCallError.class, () -> m.setText("x"));
        TestSupport.clearRecordedFailures();
    }

    @Test
    void dumpShowsInstanceDeclarations() {
        var m = verify(stubPartially(new MyClass()));
        String dump = dump();
        assertTrue(dump.contains("@StubPartially @Verify instance MyClass@"), dump);
        assertTrue(dump.contains("of org.eu.de.stuntmock.unit.InstanceDeclarationTest$MyClass"), dump);
        m.toString(); // Object contract stays real on instance declarations too
    }

    /**
     * A chain mapped for every instance of the class keeps applying to an object that is later given its own
     * mode: {@code verify(m2)} changes what happens to m2's <em>unmapped</em> calls, nothing else.
     */
    @Test
    @Stub(MyClass.class)
    void classLevelChainsStillApplyToAVerifiedInstance() {
        when(MyClass.class, m -> m.setTitle(arg.any())).thenDo((String t) -> titles.add(t));
        var m2 = verify(stubPartially(new MyClass()));
        m2.setTitle("kept");                                                   // mapped at class level: allowed
        assertEquals(List.of("kept"), titles);
        assertThrows(UnexpectedCallError.class, () -> m2.setText("x"));       // unmapped: strict
        TestSupport.clearRecordedFailures();
    }

    /** The other way round, a chain mapped on the instance never applies to other instances. */
    @Test
    @StubPartially(MyClass.class)
    void instanceChainsDoNotLeakToOtherInstances() {
        var m1 = new MyClass();
        var m2 = verify(stubPartially(new MyClass()));
        when(() -> m2.setTitle(arg.any())).thenDo((String t) -> titles.add("m2:" + t));
        m1.setTitle("one");
        m2.setTitle("two");
        assertEquals("one", m1.title);                                         // real
        assertEquals(List.of("m2:two"), titles);
    }
}
