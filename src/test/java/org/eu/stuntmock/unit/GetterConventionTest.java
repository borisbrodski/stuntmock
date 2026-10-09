package org.eu.stuntmock.unit;

import static org.eu.stuntmock.Stunt.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.eu.stuntmock.StubPartially;
import org.eu.stuntmock.StuntExtension;
import org.eu.stuntmock.StuntSettings;
import org.eu.stuntmock.StuntSettings.GetterConvention;

/**
 * What {@code mtd.getters()} counts as a getter: the Java Beans convention by default, tolerant of
 * {@code getX()} returning {@code boolean} and {@code isX()} returning {@code Boolean}; the lenient convention
 * or a custom predicate through {@link StuntSettings}.
 */
@ExtendWith(StuntExtension.class)
@StubPartially(GetterConventionTest.Bean.class)
class GetterConventionTest {

    static class Bean {
        long getId() {
            return 1;
        }

        boolean getActive() {              // get prefix, boolean result
            return true;
        }

        Boolean isValid() {                // is prefix, boxed result
            return true;
        }

        String isName() {                  // is prefix, non-boolean result: lenient only
            return "n";
        }

        boolean hasItems() {               // has prefix: lenient only
            return true;
        }

        String getter() {                  // no upper-case letter after the prefix: never
            return "g";
        }

        String fetchAll() {                // custom convention only
            return "all";
        }
    }

    @AfterEach
    void resetConvention() {
        StuntSettings.getterConvention(GetterConvention.JAVA_BEANS);
    }

    @Test
    void javaBeansToleratesTheUsualMix() {
        when(Bean.class, mtd.getters()).thenDoNothing();
        Bean b = new Bean();
        assertEquals(0L, b.getId());
        assertFalse(b.getActive());
        assertNull(b.isValid());
        assertEquals("n", b.isName());     // not a getter: real
        assertTrue(b.hasItems());          // not a getter: real
        assertEquals("g", b.getter());     // not a getter: real
    }

    @Test
    void lenientAcceptsIsAndHasWithAnyResult() {
        StuntSettings.getterConvention(GetterConvention.LENIENT);
        when(Bean.class, mtd.getters()).thenDoNothing();
        Bean b = new Bean();
        assertNull(b.isName());
        assertFalse(b.hasItems());
        assertEquals("g", b.getter());     // still not a getter
    }

    @Test
    void customDefinitionReplacesIt() {
        StuntSettings.getters(m -> m.getName().startsWith("fetch") && m.getParameterCount() == 0);
        when(Bean.class, mtd.getters()).thenDoNothing();
        Bean b = new Bean();
        assertNull(b.fetchAll());
        assertEquals(1L, b.getId());       // no longer a getter under the custom definition
    }
}
