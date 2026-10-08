package org.eu.de.stuntmock;

import java.lang.reflect.Method;
import java.util.function.Predicate;

import org.eu.de.stuntmock.internal.Scope;
import org.eu.de.stuntmock.internal.Sites;

/**
 * Configuration of Stunt, set once per JVM before the first test class opens, for example from a guarded
 * initialization routine or a static initializer of a shared base test class. Nothing here is meant to be
 * called from a test method.
 *
 * <pre>{@code
 * static {
 *     StuntSettings.addTypeResolver(new DaoResolver());
 *     StuntSettings.ignoreCallSites(WithStunt.class);
 *     StuntSettings.getterConvention(GetterConvention.LENIENT);
 * }
 * }</pre>
 */
public final class StuntSettings {

    /** What {@code mtd.getters()} counts as a getter. */
    public enum GetterConvention {
        /**
         * Java Beans: {@code getX()} with no parameter and a result (of any type, {@code boolean} included), or
         * {@code isX()} with no parameter returning {@code boolean} or {@code Boolean}.
         */
        JAVA_BEANS,
        /** Any {@code getX()}, {@code isX()} or {@code hasX()} with no parameter and a result, whatever the result type. */
        LENIENT
    }

    private static final Predicate< Method > BEANS_GETTER = m -> m.getParameterCount() == 0
        && m.getReturnType() != void.class
        && (prefixed(m, "get") || prefixed(m, "is")
            && (m.getReturnType() == boolean.class || m.getReturnType() == Boolean.class));
    private static final Predicate< Method > LENIENT_GETTER = m -> m.getParameterCount() == 0
        && m.getReturnType() != void.class && (prefixed(m, "get") || prefixed(m, "is") || prefixed(m, "has"));
    private static final Predicate< Method > BEANS_SETTER = m -> m.getParameterCount() == 1 && prefixed(m, "set");

    private static volatile Predicate< Method > getter = BEANS_GETTER;
    private static volatile Predicate< Method > setter = BEANS_SETTER;

    private static final java.util.List< Runnable > INITIALIZATION = new java.util.ArrayList<>();
    private static final java.util.List< Object[] > DEFAULTS = new java.util.ArrayList<>();
    private static volatile String initializedFor;

    private StuntSettings() {
    }

    // ---------------------------------------------------------------- initialization

    /**
     * Registers setup that runs once per JVM, right before the first test class opens: type resolvers, ignored
     * call sites, conventions, and {@link #defaults(String, Runnable)}. Several blocks may be registered; they
     * run in registration order. The natural place to call this is a static initializer of a shared base test
     * class, which Java runs before JUnit does anything with the test class; the project can also implement
     * {@link StuntInitializer} and list it in {@code META-INF/services}, which needs no base class at all.
     *
     * <pre>{@code
     * abstract class UnitTest implements WithStunt {
     *     static {
     *         StuntSettings.initialization(() -> {
     *             StuntSettings.addTypeResolver(type -> isDao(type) ? DAO.get(type) : null);
     *             StuntSettings.ignoreCallSites(WithStunt.class);
     *         });
     *     }
     * }
     * }</pre>
     *
     * Registering after the first test class opened is an error: whatever the block would have set up is
     * already missing from that class. Note that a block must not declare or map anything (no {@code stub},
     * {@code when}, {@code infrastructure}): there is no test class yet; that is what {@link #defaults} is for.
     */
    public static synchronized void initialization(Runnable block) {
        if (initializedFor != null) {
            throw new StuntException("Too late: Stunt already ran its initialization for the first test class ("
                + initializedFor + "), so this block would be missing from it. Register initialization before any"
                + " test class opens: in a static initializer of a shared base test class, or by implementing"
                + " StuntInitializer and listing it in META-INF/services/org.eu.de.stuntmock.StuntInitializer");
        }
        INITIALIZATION.add(block);
    }

    /**
     * Registers declarations and mappings every test class starts with, run inside each class scope before the
     * class's own declarations, labelled as infrastructure (collapsed in dumps). This is where a project's
     * always-on stubs live: a service locator redirected to the test container, reference data, a frozen clock.
     *
     * <pre>{@code
     * StuntSettings.defaults("project", () -> {
     *     stubPartially(ServiceLocator.class);
     *     when(() -> ServiceLocator.get(any())).thenDo((Invocation inv) -> TestContainer.lookup(inv.arg(0)));
     * });
     * }</pre>
     *
     * Register from an {@link #initialization(Runnable)} block or a {@link StuntInitializer}; the same
     * "too late" rule applies.
     */
    public static synchronized void defaults(String label, Runnable block) {
        if (initializedFor != null && !initializing) {
            throw new StuntException("Too late: Stunt already ran its initialization for the first test class ("
                + initializedFor + "). Register defaults from a StuntSettings.initialization(...) block or a StuntInitializer");
        }
        DEFAULTS.add(new Object[] {label, block});
    }

    private static boolean initializing;

    /** Internal: runs the registered initialization once, before the first class scope opens. */
    public static synchronized void runInitialization(String firstTestClass) {
        if (initializedFor != null) {
            return;
        }
        initializing = true;
        try {
            for (StuntInitializer initializer : java.util.ServiceLoader.load(StuntInitializer.class)) {
                initializer.initialize();
            }
            for (int i = 0; i < INITIALIZATION.size(); i++) { // blocks may register further blocks
                INITIALIZATION.get(i).run();
            }
        }
        finally {
            initializing = false;
        }
        initializedFor = firstTestClass;
    }

    /** Internal: applies the registered defaults to the class scope that just opened. */
    public static void applyDefaults() {
        for (Object[] entry : DEFAULTS) {
            infrastructure((String) entry[0], (Runnable) entry[1]);
        }
    }


    // ---------------------------------------------------------------- frozen clock

    private static volatile boolean freezeClock;

    /**
     * Freezes the {@code java.time} clock in every test class, through {@code FrozenClock}, without registering
     * that extension on each class. Off by default. Set it from an {@link #initialization(Runnable)} block:
     *
     * <pre>{@code
     * StuntSettings.initialization(() -> StuntSettings.freezeClock(true));
     * }</pre>
     *
     * A class opts out with {@code @RealClock}; {@code @PretendRunningAt} and {@code FrozenClock.setNow(...)}
     * work as with the explicit extension. When off, a class still gets a frozen clock by registering
     * {@code @ExtendWith(FrozenClock.class)} itself.
     */
    public static void freezeClock(boolean enabled) {
        freezeClock = enabled;
    }

    /** Whether {@link #freezeClock(boolean)} is on. */
    public static boolean freezeClockEnabled() {
        return freezeClock;
    }

    // ---------------------------------------------------------------- type resolvers

    /**
     * Registers a resolver for interfaces implemented by convention (a DAO interface and its {@code Impl}, an
     * EJB local interface and its bean); see {@link TypeResolver}. Resolvers are tried in registration order.
     * Alternatively list the class in {@code META-INF/services/org.eu.de.stuntmock.TypeResolver}.
     */
    public static void addTypeResolver(TypeResolver resolver) {
        Scope.addResolver(resolver);
    }

    /** Removes a resolver; for tests of resolvers, the production setup registers them once and keeps them. */
    public static void removeTypeResolver(TypeResolver resolver) {
        Scope.removeResolver(resolver);
    }

    // ---------------------------------------------------------------- call sites in messages

    /**
     * Frames of these classes (and their nested classes) never count as the call site shown in messages and
     * dumps. Register delegating interfaces or base classes whose default methods forward to Stunt, such as
     * {@link WithStunt}, so that a message points at the test line, not at the delegator.
     */
    public static void ignoreCallSites(Class< ? >... types) {
        for (Class< ? > type : types) {
            Sites.ignore(type.getName());
        }
    }

    /** {@link #ignoreCallSites(Class...)} by class name or package prefix. */
    public static void ignoreCallSites(String... classOrPackagePrefixes) {
        Sites.ignore(classOrPackagePrefixes);
    }

    /** How many stack frames a call site shows (default 2: the mapping line and the line that called it). */
    public static void callSiteDepth(int frames) {
        Sites.depth(frames);
    }

    // ---------------------------------------------------------------- getters and setters

    /** Sets what {@code mtd.getters()} means; default {@link GetterConvention#JAVA_BEANS}. */
    public static void getterConvention(GetterConvention convention) {
        getter = convention == GetterConvention.LENIENT ? LENIENT_GETTER : BEANS_GETTER;
    }

    /** Replaces the definition of a getter entirely, e.g. to include {@code fetchX()} in a code base that uses it. */
    public static void getters(Predicate< Method > definition) {
        getter = definition;
    }

    /** Replaces the definition of a setter; default: {@code setX(one parameter)}, whatever it returns. */
    public static void setters(Predicate< Method > definition) {
        setter = definition;
    }

    // ---------------------------------------------------------------- infrastructure

    /**
     * Runs {@code block} with every declaration and mapping it makes labelled as infrastructure. Dumps and failure
     * messages collapse each label to one summary line (classes, chain count, call count), unless the failure
     * concerns one of its declarations or the dump is filtered to its class. For extensions such as
     * {@code FrozenClock}, whose five {@code java.time} declarations and hundreds of {@code now()} calls would
     * otherwise bury what a failure is about. A test's own {@code when} on such a class is not labelled and shows
     * in full.
     */
    public static void infrastructure(String label, Runnable block) {
        if (Scope.current() == null) {
            throw new StuntException("infrastructure(\"" + label + "\", ...) declares and maps for a test class, so it"
                + " needs an open class scope. From a static initializer or an initialization block use"
                + " StuntSettings.defaults(\"" + label + "\", ...) instead, which Stunt runs for every test class");
        }
        Scope scope = Scope.require();
        String previous = scope.currentGroup();
        scope.setCurrentGroup(label);
        try {
            block.run();
        }
        finally {
            scope.setCurrentGroup(previous);
        }
    }

    // ---------------------------------------------------------------- used by Mtd

    static boolean isGetter(Method method) {
        return getter.test(method);
    }

    static boolean isSetter(Method method) {
        return setter.test(method);
    }

    private static boolean prefixed(Method m, String prefix) {
        return m.getName().startsWith(prefix) && m.getName().length() > prefix.length()
            && Character.isUpperCase(m.getName().charAt(prefix.length()));
    }
}
