package org.eu.de.stuntmock.unit;

import static org.eu.de.stuntmock.Stunt.*;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;

import org.eu.de.stuntmock.Stub;
import org.eu.de.stuntmock.StubPartially;
import org.eu.de.stuntmock.StuntExtension;
import org.eu.de.stuntmock.fakes.Greeter;
import org.eu.de.stuntmock.fakes.Tools;

/** The per-class lifecycle: instance fields are bound once, before an instance {@code @BeforeAll}. */
@ExtendWith(StuntExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LifecyclePerClassTest {

    @Stub
    Tools tools;

    @StubPartially
    Greeter greeter;

    @BeforeAll
    void classLevelDefaults() {
        assertNotNull(tools);
        assertNotNull(greeter);
        when(() -> Tools.version()).thenReturn("per-class");
        when(() -> greeter.getGreeting()).thenReturn("from instance @BeforeAll");
    }

    @Test
    void firstTest() {
        assertEquals("per-class", Tools.version());
        assertEquals("from instance @BeforeAll", new Greeter().getGreeting());
        when(() -> Tools.version()).thenReturn("first only");
        assertEquals("first only", Tools.version());
    }

    @Test
    void secondTestStartsFromClassLevelState() {
        assertEquals("per-class", Tools.version());
        assertEquals("from instance @BeforeAll", new Greeter().getGreeting());
    }
}
