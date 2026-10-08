package org.eu.de.stuntmock.unit;

import static org.eu.de.stuntmock.unit.OtherLibrary.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.eu.de.stuntmock.WithStunt;
import org.eu.de.stuntmock.StubPartially;
import org.eu.de.stuntmock.StuntExtension;
import org.eu.de.stuntmock.StuntSettings;
import org.eu.de.stuntmock.fakes.Greeter;

/**
 * Through {@link WithStunt}, Stunt is usable with no static import, and the {@code arg}/{@code mtd}
 * namespaces keep the matcher names out of the class scope: {@code startsWith} below is another library's,
 * statically imported, and resolves to it, while Stunt's is {@code arg.startsWith}. Only {@code any()} and
 * {@code eq()} are members on purpose, because they are used so often; those two do shadow a static import.
 */
@ExtendWith(StuntExtension.class)
@StubPartially(Greeter.class)
class DelegatorTest implements WithStunt {

    static {
        StuntSettings.ignoreCallSites(WithStunt.class);
    }

    @Test
    void noImportsAndNoCollision() {
        Greeter greeter = new Greeter();
        when(() -> greeter.greet(arg.startsWith("B"))).thenReturn("stunt");
        when(greeter, mtd.named("getGreeting")).thenDoNothing();

        assertEquals("stunt", greeter.greet("Bob"));
        assertEquals("other:B", startsWith("B"));          // the other library's, not shadowed
        assertNull(greeter.getGreeting());
    }

    @Test
    void anyAndEqAreMembersForConvenience() {
        Greeter greeter = new Greeter();
        when(() -> greeter.greet(any())).thenReturn("anyone");
        when(() -> greeter.greet(eq("Ann"))).thenReturn("Ann");
        assertEquals("Ann", greeter.greet("Ann"));
        assertEquals("anyone", greeter.greet("Bob"));
    }

    @Test
    void instanceVerificationThroughTheDelegator() {
        Greeter greeter = verify(stubPartially(new Greeter()));
        when(greeter, mtd.getters()).anyTimes();
        verify(() -> greeter.setGreeting(arg.any()));
        greeter.setGreeting("x");
        assertEquals("x", greeter.getGreeting());
    }

    @Test
    void callSiteIsTheTestLine() {
        Greeter greeter = new Greeter();
        when(() -> greeter.greet("x")).thenReturn("y");
        String dump = dump();
        assertEquals(true, dump.contains("DelegatorTest.callSiteIsTheTestLine(DelegatorTest.java:"), dump);
    }
}
