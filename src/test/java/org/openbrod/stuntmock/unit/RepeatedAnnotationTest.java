package org.openbrod.stuntmock.unit;

import static org.openbrod.stuntmock.Stunt.dump;
import static org.openbrod.stuntmock.Stunt.when;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.openbrod.stuntmock.Stub;
import org.openbrod.stuntmock.StubPartially;
import org.openbrod.stuntmock.StuntExtension;
import org.openbrod.stuntmock.Verify;
import org.openbrod.stuntmock.fakes.Counter;
import org.openbrod.stuntmock.fakes.Greeter;
import org.openbrod.stuntmock.fakes.Helper;
import org.openbrod.stuntmock.fakes.Tools;

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
        assertTrue(dump.contains("@Stub org.openbrod.stuntmock.fakes.Tools"), dump);
        assertTrue(dump.contains("@Stub org.openbrod.stuntmock.fakes.Counter"), dump);
        assertTrue(dump.contains("@Verify org.openbrod.stuntmock.fakes.Helper"), dump);
        assertEquals(0, Tools.doubled(21));
        assertEquals(0, new Counter().next());
    }

    @Test
    @StubPartially(Greeter.class)
    @StubPartially({})
    void repeatedMethodLevelAnnotationsDeclareTheTestOnly() {
        when(Greeter::getGreeting).thenReturn("x");
        assertEquals("x", new Greeter().getGreeting());
        assertTrue(dump().contains("@StubPartially org.openbrod.stuntmock.fakes.Greeter"), dump());
    }
}
