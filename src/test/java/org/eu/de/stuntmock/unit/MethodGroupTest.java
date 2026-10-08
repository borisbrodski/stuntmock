package org.eu.de.stuntmock.unit;

import static org.eu.de.stuntmock.Stunt.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.eu.de.stuntmock.Invocation;
import org.eu.de.stuntmock.Stub;
import org.eu.de.stuntmock.StubPartially;
import org.eu.de.stuntmock.StuntException;
import org.eu.de.stuntmock.StuntExtension;
import org.eu.de.stuntmock.UndeclaredClassException;
import org.eu.de.stuntmock.UnexpectedCallError;
import org.eu.de.stuntmock.Verify;
import org.eu.de.stuntmock.fakes.PriceService;
import org.eu.de.stuntmock.internal.Scope;

/**
 * Method groups: {@code when(obj, mtd.getters())} maps every method a matcher selects as one chain. Counts
 * apply to the matching calls in total. The typical use is a {@code @Verify} object whose getters do not matter.
 */
@ExtendWith(StuntExtension.class)
class MethodGroupTest {

    static class Base {
        String baseText;

        void setBaseText(String t) {
            baseText = t;
        }

        String getBaseText() {
            return baseText;
        }
    }

    static class Entity extends Base {
        long id = 42;
        boolean active = true;
        String text;
        String title;
        List< String > log = new ArrayList<>();

        long getId() {
            return id;
        }

        boolean isActive() {
            return active;
        }

        void setText(String text) {
            this.text = text;
        }

        void setTitle(String title) {
            this.title = title;
        }

        void setText(String text, boolean upper) {
            this.text = upper ? text.toUpperCase() : text;
        }

        private int computeHash() {
            return 7;
        }

        int publicHash() {
            return computeHash();
        }

        static String kind() {
            return "entity";
        }
    }

    // ---------------------------------------------------------------- the intended reading

    /** Verify this, the getters do not matter, everything else is unexpected. */
    @Test
    void verifyThisGettersDoNotMatter() {
        var m2 = verify(stubPartially(new Entity()));
        when(m2, mtd.getters()).anyTimes();
        verify(() -> m2.setText(arg.any()));

        assertEquals(42L, m2.getId());                  // real getter, as often as it likes
        assertTrue(m2.isActive());
        assertEquals(42L, m2.getId());
        m2.setText("x");
        assertEquals("x", m2.text);                     // strict audit: the body ran
        assertThrows(UnexpectedCallError.class, () -> m2.setTitle("x"));
        TestSupport.clearRecordedFailures();
    }

    // ---------------------------------------------------------------- matchers

    @Test
    @StubPartially(Entity.class)
    void gettersMatchGetAndIsWithoutParameters() {
        when(Entity.class, mtd.getters()).thenDoNothing();
        Entity e = new Entity();
        assertEquals(0L, e.getId());
        assertFalse(e.isActive());
        assertNull(e.getBaseText());                   // inherited getter
        e.setText("real");
        assertEquals("real", e.text);                  // setters untouched
    }

    @Test
    @StubPartially(Entity.class)
    void settersMatchEveryOverload() {
        when(Entity.class, mtd.setters()).thenDoNothing();
        Entity e = new Entity();
        e.setText("a");
        e.setTitle("b");
        e.setBaseText("c");
        assertNull(e.text);
        assertNull(e.title);
        assertNull(e.baseText);
        e.setText("d", true);                          // two parameters: not a setter
        assertEquals("D", e.text);
    }

    @Test
    @StubPartially(Entity.class)
    void namedMatchesGlobsAndPrivateMethods() {
        when(Entity.class, mtd.named("set*", "computeHash")).thenDoNothing();
        Entity e = new Entity();
        e.setText("a");
        assertNull(e.text);
        assertEquals(0, e.publicHash());               // computeHash skipped, returns default
    }

    @Test
    @StubPartially(Entity.class)
    void declaredInMatchesWhatABaseClassContributes() {
        when(Entity.class, mtd.declaredIn(Base.class)).thenDoNothing();
        Entity e = new Entity();
        e.setBaseText("x");
        assertNull(e.baseText);
        e.setText("y");
        assertEquals("y", e.text);
    }

    @Test
    @StubPartially(Entity.class)
    void combinatorsReadAsEnglish() {
        when(Entity.class, mtd.setters().except(mtd.named("setTitle"))).thenDoNothing();
        Entity e = new Entity();
        e.setText("a");
        e.setTitle("b");
        assertNull(e.text);
        assertEquals("b", e.title);
        when(Entity.class, mtd.getters().or(mtd.returningVoid())).thenThrow(new IllegalStateException("grouped"));
        assertThrows(IllegalStateException.class, e::getId);
        assertThrows(IllegalStateException.class, () -> e.setTitle("c"));
        assertEquals(7, e.publicHash());               // neither a getter nor void
    }

    @Test
    @StubPartially(Entity.class)
    void methodThatIsTheUniversalMatcher() {
        when(Entity.class, mtd.methodThat(m -> m.getName().length() == 5)).thenDoNothing();
        Entity e = new Entity();
        assertEquals(0L, e.getId());                   // "getId" has five letters
        assertTrue(e.isActive());
    }

    // ---------------------------------------------------------------- statics

    @Test
    @StubPartially(Entity.class)
    void classGroupIncludesStatics() {
        when(Entity.class, mtd.staticMethods()).thenDoNothing();
        assertNull(Entity.kind());
    }

    @Test
    @StubPartially(Entity.class)
    void instanceGroupNeverIncludesStatics() {
        Entity e = new Entity();
        when(e, mtd.anyMethod()).thenDoNothing();
        assertEquals("entity", Entity.kind());
        assertEquals(0L, e.getId());
    }

    // ---------------------------------------------------------------- counts in total

    @Test
    void countsApplyToTheGroupInTotal() {
        var m2 = verify(stubPartially(new Entity()));
        verify(m2, mtd.setters()).times(2);
        m2.setText("a");
        m2.setTitle("b");
        assertThrows(UnexpectedCallError.class, () -> m2.setBaseText("c"));
        TestSupport.clearRecordedFailures();
    }

    @Test
    void groupVerifyReportsMissingCallsInTotal() {
        var m2 = verify(stubPartially(new Entity()));
        verify(m2, mtd.setters()).times(2);
        m2.setText("a");
        assertThrows(org.eu.de.stuntmock.MissingCallError.class, () -> Scope.testScope().verifyAll());
        m2.setTitle("b");
    }

    // ---------------------------------------------------------------- modes

    @Test
    @Stub(Entity.class)
    void onAStubClassAGroupCanLetMethodsThrough() {
        when(Entity.class, mtd.getters()).thenCallOriginal();
        Entity e = new Entity();
        assertEquals(42L, e.getId());
        e.setText("x");
        assertNull(e.text);                            // still stubbed
    }

    @Test
    void onAnInterfaceMockAGroupAnswersTheDefault(@Stub(proxy = true) @Verify PriceService prices) {
        when(prices, mtd.anyMethod()).anyTimes();
        assertEquals(0d, prices.rateFor("DE"));
    }

    // ---------------------------------------------------------------- terminals on a group

    @Test
    @StubPartially(Entity.class)
    void thenDoWithInvocationSeesTheResolvedMethod() {
        List< String > names = new ArrayList<>();
        when(Entity.class, mtd.setters()).thenDo((Invocation inv) -> {
            names.add(inv.method().getName() + "=" + inv.arg(0));
        });
        Entity e = new Entity();
        e.setText("a");
        e.setTitle("b");
        assertEquals(List.of("setText=a", "setTitle=b"), names);
    }

    @Test
    @StubPartially(Entity.class)
    void thenReturnIsRejectedOnAGroup() {
        StuntException e = assertThrows(StuntException.class, () -> when(Entity.class, mtd.getters()).thenReturn(1L));
        assertTrue(e.getMessage().contains("no common return type"), e.getMessage());
    }

    @Test
    @StubPartially(Entity.class)
    void typedThenDoIsRejectedOnAGroup() {
        StuntException e = assertThrows(StuntException.class,
            () -> when(Entity.class, mtd.setters()).thenDo((String s) -> { }));
        assertTrue(e.getMessage().contains("no common parameter list"), e.getMessage());
    }

    // ---------------------------------------------------------------- errors

    @Test
    @StubPartially(Entity.class)
    void groupMatchingNothingIsAnError() {
        StuntException e = assertThrows(StuntException.class, () -> when(Entity.class, mtd.named("fetch*")));
        assertTrue(e.getMessage().contains("no method of Entity matches"), e.getMessage());
    }

    @Test
    void undeclaredObjectIsReported() {
        Entity e = new Entity();
        assertThrows(UndeclaredClassException.class, () -> when(e, mtd.getters()));
    }

    @Test
    void dumpShowsTheGroup() {
        var m2 = verify(stubPartially(new Entity()));
        when(m2, mtd.getters()).anyTimes();
        m2.getId();
        String dump = dump();
        assertTrue(dump.contains(".<getters>)"), dump);
        assertTrue(dump.contains("default behaviour [1/*]"), dump);
    }
}
