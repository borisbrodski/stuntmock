package org.eu.stuntmock.unit;

import static org.eu.stuntmock.Stunt.dump;
import static org.eu.stuntmock.Stunt.when;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.eu.stuntmock.Stub;
import org.eu.stuntmock.StubPartially;
import org.eu.stuntmock.StuntExtension;
import org.eu.stuntmock.Verify;
import org.eu.stuntmock.fakes.Counter;
import org.eu.stuntmock.fakes.Greeter;
import org.eu.stuntmock.fakes.Helper;
import org.eu.stuntmock.fakes.Tools;

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
        assertTrue(dump.contains("@Stub org.eu.stuntmock.fakes.Tools"), dump);
        assertTrue(dump.contains("@Stub org.eu.stuntmock.fakes.Counter"), dump);
        assertTrue(dump.contains("@Verify org.eu.stuntmock.fakes.Helper"), dump);
        assertEquals(0, Tools.doubled(21));
        assertEquals(0, new Counter().next());
    }

    @Test
    @StubPartially(Greeter.class)
    @StubPartially({})
    void repeatedMethodLevelAnnotationsDeclareTheTestOnly() {
        when(Greeter::getGreeting).thenReturn("x");
        assertEquals("x", new Greeter().getGreeting());
        assertTrue(dump().contains("@StubPartially org.eu.stuntmock.fakes.Greeter"), dump());
    }
}
