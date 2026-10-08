package org.eu.de.stuntmock.unit;

import static org.eu.de.stuntmock.Stunt.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.eu.de.stuntmock.StubPartially;
import org.eu.de.stuntmock.StuntExtension;
import org.eu.de.stuntmock.UnexpectedCallError;
import org.eu.de.stuntmock.Verify;

/**
 * {@code @Verify} is a strict audit: a call no chain maps fails, a mapped call runs the real code unless a
 * terminal says otherwise. The three annotations therefore differ only in what an <em>unmapped</em> call
 * does; a chain without terminal always keeps the class's natural behaviour.
 */
@ExtendWith(StuntExtension.class)
class StrictAuditTest {

    static class Database {
        static final List< String > WRITES = new ArrayList<>();
    }

    static class Dao {
        void save(String row) {
            Database.WRITES.add(row);
        }

        String load(long id) {
            return "row" + id;
        }
    }

    static class Base {
        String persisted;

        void persist() {
            persisted = "yes";
        }
    }

    static class Entity extends Base {
        String text;

        void setText(String text) {
            this.text = text;
        }
    }

    @Test
    @StubPartially(Entity.class)
    @Verify(Entity.class)
    void verifiedCallRunsTheRealCode() {
        verify(Entity::persist);
        Entity e = new Entity();
        e.persist();
        assertEquals("yes", e.persisted);              // inherited from the base class, real
    }

    @Test
    @StubPartially(Entity.class)
    @Verify(Entity.class)
    void thenDoNothingSuppressesTheBody() {
        verify(Entity::persist).thenDoNothing();
        Entity e = new Entity();
        e.persist();
        assertNull(e.persisted);
    }

    /** A collaborator that must not run: a bare verify runs it, loudly, and the fix is a terminal. */
    @Test
    @StubPartially(Dao.class)
    @Verify(Dao.class)
    void aBareVerifyOnACollaboratorRunsIt() {
        Database.WRITES.clear();
        verify(Dao.class, d -> d.save(arg.any()));
        new Dao().save("row");
        assertEquals(List.of("row"), Database.WRITES);  // visible, not silently swallowed

        verify(Dao.class, d -> d.load(arg.anyLong())).thenReturn("stubbed");
        assertEquals("stubbed", new Dao().load(1L));    // the terminal keeps the database out
    }

    @Test
    @StubPartially(Dao.class)
    @Verify(Dao.class)
    void unmappedCallStillFails() {
        assertThrows(UnexpectedCallError.class, () -> new Dao().load(1L));
        TestSupport.clearRecordedFailures();
    }

    /** Instance scope: the same on one object, with the other objects untouched. */
    @Test
    void strictAuditOnOneInstance() {
        Entity watched = verify(stubPartially(new Entity()));
        Entity other = new Entity();
        when(watched, mtd.getters()).anyTimes();
        verify(() -> watched.setText(arg.any()));
        watched.setText("a");
        other.setText("b");
        other.persist();
        assertEquals("a", watched.text);
        assertEquals("b", other.text);
        assertEquals("yes", other.persisted);
        assertThrows(UnexpectedCallError.class, watched::persist);
        TestSupport.clearRecordedFailures();
    }

    /** {@code @StubPartially} and {@code @Verify} agree on mapped calls; they differ on unmapped ones. */
    @Test
    @StubPartially(Entity.class)
    void partialAndVerifyAgreeOnMappedCalls() {
        verify(Entity::persist);
        Entity e = new Entity();
        e.persist();
        assertEquals("yes", e.persisted);
        e.setText("unmapped is fine here");
        assertTrue(dump().contains("original (unmapped)"), dump());
    }
}
