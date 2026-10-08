package org.eu.de.stuntmock.unit;

import static org.eu.de.stuntmock.Stunt.dump;
import static org.eu.de.stuntmock.Stunt.when;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.eu.de.stuntmock.Stub;
import org.eu.de.stuntmock.StubPartially;
import org.eu.de.stuntmock.StuntExtension;
import org.eu.de.stuntmock.Verify;
import org.eu.de.stuntmock.fakes.Counter;
import org.eu.de.stuntmock.fakes.Greeter;
import org.eu.de.stuntmock.fakes.Helper;
import org.eu.de.stuntmock.fakes.Tools;

/** The declaring annotations are repeatable on classes and methods; repeating equals listing in one array. */
@ExtendWith(StuntExtension.class)
@Stub(Tools.class)
@Stub(Counter.class)
@StubPartially(Helper.class)
@Verify(Helper.class)
class RepeatedAnnotationTest {

    @Test
    void repeatedClassLevelAnnotationsDeclareEveryClass() {
        String dump = dump();
        assertTrue(dump.contains("@Stub org.eu.de.stuntmock.fakes.Tools"), dump);
        assertTrue(dump.contains("@Stub org.eu.de.stuntmock.fakes.Counter"), dump);
        assertTrue(dump.contains("@Verify org.eu.de.stuntmock.fakes.Helper"), dump);
        assertEquals(0, Tools.doubled(21));
        assertEquals(0, new Counter().next());
    }

    @Test
    @StubPartially(Greeter.class)
    @StubPartially({})
    void repeatedMethodLevelAnnotationsDeclareTheTestOnly() {
        when(Greeter::getGreeting).thenReturn("x");
        assertEquals("x", new Greeter().getGreeting());
        assertTrue(dump().contains("@StubPartially org.eu.de.stuntmock.fakes.Greeter"), dump());
    }
}
