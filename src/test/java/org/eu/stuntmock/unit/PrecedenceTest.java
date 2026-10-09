package org.eu.stuntmock.unit;

import static org.eu.stuntmock.Stunt.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.eu.stuntmock.StubPartially;
import org.eu.stuntmock.StuntExtension;
import org.eu.stuntmock.UnexpectedCallError;
import org.eu.stuntmock.internal.Scope;

/**
 * Which chain answers a call: a chain for one signature beats a chain for a group of methods, whatever the
 * order they were declared in; among equals the newest wins; an exhausted {@code verify} fails, an exhausted
 * {@code when} defers.
 */
@ExtendWith(StuntExtension.class)
class PrecedenceTest {

    static class Entity {
        String text;
        String title;

        void setText(String text) {
            this.text = text;
        }

        void setTitle(String title) {
            this.title = title;
        }

        String getText() {
            return text;
        }
    }

    /** Broad allowance first, specific verification after: the verification counts its call. */
    @Test
    void specificAfterGroup() {
        var m2 = verify(stubPartially(new Entity()));
        when(m2, mtd.setters()).anyTimes();
        verify(() -> m2.setText("x")).thenDoNothing();
        m2.setText("x");
        assertNull(m2.text);                           // the verify answered, not the group
        m2.setTitle("free");
        assertEquals("free", m2.title);                // the group answered
        assertThrows(UnexpectedCallError.class, () -> m2.setText("x"));   // verify exhausted: fails, group does not rescue
        TestSupport.clearRecordedFailures();
    }

    /** The same the other way round: the group does not swallow the verified call just because it is newer. */
    @Test
    void specificBeforeGroup() {
        var m2 = verify(stubPartially(new Entity()));
        verify(() -> m2.setText("x")).thenDoNothing();
        when(m2, mtd.setters()).anyTimes();
        m2.setText("x");
        assertNull(m2.text);
        m2.setTitle("free");
        assertEquals("free", m2.title);
        Scope.testScope().verifyAll();                 // the verify was satisfied by its own call
    }

    /** A specific chain with different arguments does not shadow the group for other arguments. */
    @Test
    void specificWithOtherArgumentsLeavesTheGroup() {
        var m2 = verify(stubPartially(new Entity()));
        when(m2, mtd.setters()).anyTimes();
        verify(() -> m2.setText("x")).thenDoNothing();
        m2.setText("y");                               // not "x": the group answers, body runs
        assertEquals("y", m2.text);
        m2.setText("x");
    }

    /** Two groups overlapping: the newer one answers. */
    @Test
    @StubPartially(Entity.class)
    void newerGroupWins() {
        Entity e = new Entity();
        when(Entity.class, mtd.anyMethod()).thenThrow(new IllegalStateException("old"));
        when(Entity.class, mtd.setters()).thenDoNothing();
        e.setText("a");                                // the newer group
        assertNull(e.text);
        assertThrows(IllegalStateException.class, e::getText);   // only the older group matches
    }

    /** An exhausted {@code when} group defers to the next older matching chain. */
    @Test
    @StubPartially(Entity.class)
    void exhaustedWhenGroupDefers() {
        Entity e = new Entity();
        when(Entity.class, mtd.setters()).thenThrow(new IllegalStateException("fallback"));
        when(Entity.class, mtd.setters()).thenDoNothing().times(1);
        e.setText("a");
        assertThrows(IllegalStateException.class, () -> e.setText("b"));
    }

    /** Class-level and instance-level groups: the instance one is newer and wins, class-level still applies elsewhere. */
    @Test
    @StubPartially(Entity.class)
    void instanceGroupOverClassGroup() {
        Entity a = new Entity();
        Entity b = new Entity();
        when(Entity.class, mtd.setters()).thenDoNothing();
        when(b, mtd.setters()).thenCallOriginal();
        a.setText("x");
        b.setText("y");
        assertNull(a.text);
        assertEquals("y", b.text);
    }
}
