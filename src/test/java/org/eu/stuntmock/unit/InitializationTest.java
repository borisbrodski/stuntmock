package org.eu.stuntmock.unit;

import static org.eu.stuntmock.Stunt.dump;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.eu.stuntmock.StuntException;
import org.eu.stuntmock.StuntExtension;
import org.eu.stuntmock.StuntSettings;
import org.eu.stuntmock.fakes.Registry;
import org.eu.stuntmock.unit.fixtures.ForkedMain;
import org.eu.stuntmock.unit.fixtures.GlobalClockFixture;
import org.eu.stuntmock.unit.fixtures.StaticInitializationFixture;

/**
 * Project setup: {@code StuntSettings.initialization} runs once per JVM before the first test class, a
 * {@code StuntInitializer} listed in {@code META-INF/services} the same way; {@code StuntSettings.defaults}
 * declares what every test class starts with; registering any of it after the first class opened is an error
 * that says so.
 */
@ExtendWith(StuntExtension.class)
class InitializationTest {

    /** The suite's initializer ({@code META-INF/services}) ran exactly once, before this class. */
    @Test
    void serviceInitializerRanOnce() {
        assertEquals(1, SuiteInitializer.RUNS.get());
    }

    /** ... and its defaults are in effect here, as in every test class, labelled as infrastructure. */
    @Test
    void defaultsApplyToEveryTestClass() {
        assertTrue(Registry.active());
        assertTrue(dump().contains("[suite] @StubPartially Registry"), dump());
    }

    @Test
    void initializationAfterTheFirstTestClassIsTooLate() {
        StuntException e = assertThrows(StuntException.class, () -> StuntSettings.initialization(() -> { }));
        assertTrue(e.getMessage().startsWith("Too late: Stunt already ran its initialization"), e.getMessage());
        assertTrue(e.getMessage().contains("static initializer of a shared base test class"), e.getMessage());
        assertTrue(e.getMessage().contains("META-INF/services/org.eu.stuntmock.StuntInitializer"), e.getMessage());
    }

    @Test
    void defaultsAfterTheFirstTestClassAreTooLate() {
        StuntException e = assertThrows(StuntException.class, () -> StuntSettings.defaults("late", () -> { }));
        assertTrue(e.getMessage().startsWith("Too late"), e.getMessage());
    }

    /** {@code infrastructure} needs a class scope; from a static initializer the message points at {@code defaults}. */
    @Test
    void infrastructureOutsideAScopeExplainsDefaults() throws InterruptedException {
        List< Throwable > caught = new ArrayList<>();
        Thread noScope = new Thread(() -> {
            try {
                StuntSettings.infrastructure("project", () -> { });
            }
            catch (Throwable t) {
                caught.add(t);
            }
        });
        noScope.start();
        noScope.join();
        assertEquals(1, caught.size());
        assertTrue(caught.get(0).getMessage().contains("StuntSettings.defaults(\"project\", ...)"), caught.get(0).getMessage());
    }

    /** The classpath of this JVM plus the code sources of everything the fork needs (a console launcher run has only the launcher jar in java.class.path). */
    private static String classpath() {
        java.util.LinkedHashSet< String > entries = new java.util.LinkedHashSet<>();
        for (String entry : System.getProperty("java.class.path").split(File.pathSeparator)) {
            entries.add(entry);
        }
        for (String className : List.of(ForkedMain.class.getName(), "org.eu.stuntmock.Stunt",
            "org.junit.platform.launcher.Launcher", "org.junit.platform.engine.TestEngine",
            "org.junit.platform.commons.JUnitException", "org.junit.jupiter.api.Test",
            "org.junit.jupiter.engine.JupiterTestEngine", "org.opentest4j.AssertionFailedError",
            "org.apiguardian.api.API", "net.bytebuddy.ByteBuddy", "net.bytebuddy.agent.ByteBuddyAgent",
            "org.objenesis.ObjenesisStd")) {
            try {
                java.security.CodeSource source = Class.forName(className).getProtectionDomain().getCodeSource();
                if (source != null) {
                    entries.add(new File(source.getLocation().toURI()).getPath());
                }
            }
            catch (ClassNotFoundException | java.net.URISyntaxException e) {
                // not on this classpath: the launcher jar bundles it or it is not needed
            }
        }
        return String.join(File.pathSeparator, entries);
    }

    /** The static-initializer route, end to end, in a fresh JVM: initialization once, defaults in every test. */
    @Test
    void staticInitializerRouteInAFreshJvm() throws Exception {
        String java = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
        Process process = new ProcessBuilder(java, "-XX:+EnableDynamicAgentLoading", "-cp", classpath(),
            ForkedMain.class.getName(), StaticInitializationFixture.class.getName()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes());
        assertEquals(0, process.waitFor(), output);
    }

    /** {@code StuntSettings.freezeClock(true)} freezes the clock in every class without registering FrozenClock. */
    @Test
    void freezeClockSettingDrivesTheClockForEveryClass() {
        StuntSettings.freezeClock(true);
        try {
            assertEquals(null, Launch.firstFailure(GlobalClockFixture.class),
                String.valueOf(Launch.firstFailure(GlobalClockFixture.class)));
            assertEquals(null, Launch.firstFailure(GlobalClockFixture.AlsoRegisteredExplicitly.class),
                String.valueOf(Launch.firstFailure(GlobalClockFixture.AlsoRegisteredExplicitly.class)));
        }
        finally {
            StuntSettings.freezeClock(false);
        }
    }

    /** Off by default: without the setting and without the extension the clock advances. */
    @Test
    void clockIsNotFrozenByDefault() throws InterruptedException {
        java.time.Instant first = java.time.Instant.now();
        Thread.sleep(5);
        assertTrue(java.time.Instant.now().isAfter(first));
    }
}
