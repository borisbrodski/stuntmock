package org.eu.stuntmock.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.eu.stuntmock.WithStunt;
import org.eu.stuntmock.StubPartially;
import org.eu.stuntmock.Stunt;
import org.eu.stuntmock.StuntExtension;
import org.eu.stuntmock.StuntSettings;
import org.eu.stuntmock.fakes.Tools;

/**
 * Call sites in messages and dumps are rendered like stack-trace lines, so that IDEs link them, show the
 * mapping line and the line that called it, and skip registered delegators such as a base-class interface whose
 * default methods forward to {@link Stunt}.
 */
@ExtendWith(StuntExtension.class)
@StubPartially(Tools.class)
class CallSiteTest implements WithStunt {

    static {
        StuntSettings.ignoreCallSites(WithStunt.class);
    }

    private static final String HERE = "org.eu.stuntmock.unit.CallSiteTest";

    private void mapInHelper() {
        when(() -> Tools.version()).thenReturn("helper");
    }

    /** {@code at package.Class.method(File.java:line)}: the form consoles and JUnit views turn into links. */
    @Test
    void siteIsRenderedLikeAStackFrame() {
        when(() -> Tools.version()).thenReturn("v");
        String dump = Stunt.dump();
        assertTrue(dump.contains("declared at " + HERE + ".siteIsRenderedLikeAStackFrame(CallSiteTest.java:"), dump);
    }

    /** The delegator's frame is skipped; the site is the test line, not the default method. */
    @Test
    void delegatorFramesAreSkipped() {
        when(() -> Tools.version()).thenReturn("v");
        String dump = Stunt.dump();
        assertFalse(dump.contains("WithStunt.when("), dump);
        assertTrue(dump.contains(HERE + ".delegatorFramesAreSkipped(CallSiteTest.java:"), dump);
    }

    /** Two frames by default: a mapping made in a helper also names the test that called the helper. */
    @Test
    void helperAndItsCallerAreBothShown() {
        mapInHelper();
        String dump = Stunt.dump();
        int helper = dump.indexOf(HERE + ".mapInHelper(CallSiteTest.java:");
        int caller = dump.indexOf(HERE + ".helperAndItsCallerAreBothShown(CallSiteTest.java:");
        assertTrue(helper >= 0 && caller > helper, dump);
        assertEquals("helper", Tools.version());
    }

    /** The walk stops at the reflective call that invoked the test, so runner frames never appear. */
    @Test
    void runnerFramesNeverAppear() {
        when(() -> Tools.version()).thenReturn("v");
        String dump = Stunt.dump();
        assertFalse(dump.contains("org.junit."), dump);
        assertFalse(dump.contains("jdk.internal."), dump);
        assertFalse(dump.contains("java.lang.reflect"), dump);
    }

    /** The depth is configurable; one frame shows only the mapping line. */
    @Test
    void depthIsConfigurable() {
        StuntSettings.callSiteDepth(1);
        try {
            mapInHelper();
            String dump = Stunt.dump();
            assertTrue(dump.contains(HERE + ".mapInHelper(CallSiteTest.java:"), dump);
            assertFalse(dump.contains(HERE + ".depthIsConfigurable(CallSiteTest.java:"), dump);
        }
        finally {
            StuntSettings.callSiteDepth(2);
        }
    }
}
