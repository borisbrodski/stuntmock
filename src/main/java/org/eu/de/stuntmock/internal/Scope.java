package org.eu.de.stuntmock.internal;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.CopyOnWriteArrayList;

import org.eu.de.stuntmock.Mode;
import org.eu.de.stuntmock.StuntException;
import org.eu.de.stuntmock.TypeResolver;

/**
 * The mocking state of the current thread: declarations, chains, the call trace and the failures collected so
 * far. Two scopes exist while a test runs: the class scope (opened before {@code @BeforeAll}) and the test
 * scope (opened before {@code @BeforeEach}); the test scope sees the class scope's declarations and gets a fresh
 * copy of its chains. Internal.
 */
public final class Scope {

    private static final ThreadLocal< Scope > CLASS_SCOPE = new ThreadLocal<>();
    private static final ThreadLocal< Scope > TEST_SCOPE = new ThreadLocal<>();
    /** Depth of framework-internal work (matching, formatting) during which interception is suspended. */
    private static final ThreadLocal< int[] > INTERNAL = ThreadLocal.withInitial(() -> new int[1]);
    private static final List< TypeResolver > RESOLVERS = new CopyOnWriteArrayList<>();
    private static volatile boolean resolversLoaded;

    private final Scope parent;
    private final String name;
    /** The test class of a class scope; {@code null} for a test scope. */
    private final Class< ? > testClass;
    private final boolean classScope;
    private final List< Declaration > declarations = new ArrayList<>();
    private final Map< Object, Declaration > handles = new IdentityHashMap<>();
    private final List< Chain > chains = new ArrayList<>();
    private final CallTrace trace;
    private final List< AssertionError > failures = new ArrayList<>();
    private Capture capture;
    private Bypass bypass;

    private Scope(Scope parent, String name, Class< ? > testClass, boolean classScope) {
        this.parent = parent;
        this.name = name;
        this.testClass = testClass;
        this.classScope = classScope;
        this.trace = new CallTrace();
    }

    // ---------------------------------------------------------------- lifecycle

    /**
     * Opens the class scope of {@code testClass}. A {@code @Nested} class opens a scope inside its enclosing
     * class's scope and sees its declarations and chains. A scope of an unrelated class still open on this
     * thread means that class's {@code afterAll} never ran (a custom engine, a crash); it is closed with a
     * warning rather than poisoning every class that follows.
     */
    public static Scope openClassScope(Class< ? > testClass) {
        Scope open = CLASS_SCOPE.get();
        if (open != null && !enclosedBy(testClass, open.testClass)) {
            Log.warn("The Stunt class scope of " + open.name + " was still open when " + testClass.getName()
                + " started: its afterAll did not run. Closing it, together with any test scope, to keep "
                + testClass.getSimpleName() + " clean.", null);
            CLASS_SCOPE.remove();
            TEST_SCOPE.remove();
            open = null;
        }
        Scope scope = new Scope(open, testClass.getSimpleName(), testClass, true);
        CLASS_SCOPE.set(scope);
        return scope;
    }

    private static boolean enclosedBy(Class< ? > type, Class< ? > enclosing) {
        for (Class< ? > c = type.getEnclosingClass(); c != null; c = c.getEnclosingClass()) {
            if (c == enclosing) {
                return true;
            }
        }
        return false;
    }

    public static Scope openTestScope(String name) {
        Scope classScope = CLASS_SCOPE.get();
        if (TEST_SCOPE.get() != null) {
            throw new StuntException("A test scope is already open (" + TEST_SCOPE.get().name + ")");
        }
        Scope scope = new Scope(classScope, name, null, false);
        java.util.ArrayDeque< Scope > levels = new java.util.ArrayDeque<>();
        for (Scope s = classScope; s != null; s = s.parent) {
            levels.push(s);
        }
        for (Scope level : levels) {
            for (Chain template : level.chains) {
                scope.chains.add(template.instantiate());
            }
        }
        TEST_SCOPE.set(scope);
        return scope;
    }

    public static void closeTestScope() {
        TEST_SCOPE.remove();
    }

    public static void closeClassScope() {
        Scope open = CLASS_SCOPE.get();
        if (open != null && open.parent != null) {
            CLASS_SCOPE.set(open.parent); // leaving a @Nested class: back to the enclosing class's scope
        }
        else {
            CLASS_SCOPE.remove();
        }
    }

    /** The scope a call is dispatched against: the test scope if open, else the class scope, else {@code null}. */
    public static Scope current() {
        Scope test = TEST_SCOPE.get();
        return test != null ? test : CLASS_SCOPE.get();
    }

    public static Scope classScope() {
        return CLASS_SCOPE.get();
    }

    public static Scope testScope() {
        return TEST_SCOPE.get();
    }

    public static Scope require() {
        Scope scope = current();
        if (scope == null) {
            throw new StuntException("No Stunt scope is open. Is the test class annotated with"
                + " @ExtendWith(StuntExtension.class), and is this call made from a test, @BeforeEach or"
                + " @BeforeAll method?");
        }
        return scope;
    }

    public String name() {
        return name;
    }

    public boolean isClassScope() {
        return classScope;
    }

    /** The scope this one was opened inside: the enclosing class's for a {@code @Nested} class, the class scope for a test scope, else {@code null}. */
    public Scope parent() {
        return parent;
    }

    // ---------------------------------------------------------------- resolvers

    /** Registers a resolver; it applies to every declaration made afterwards. */
    public static void addResolver(TypeResolver resolver) {
        resolvers().add(resolver);
    }

    public static void removeResolver(TypeResolver resolver) {
        RESOLVERS.remove(resolver);
    }

    private static List< TypeResolver > resolvers() {
        if (!resolversLoaded) {
            synchronized (RESOLVERS) {
                if (!resolversLoaded) {
                    for (TypeResolver r : ServiceLoader.load(TypeResolver.class)) {
                        RESOLVERS.add(r);
                    }
                    resolversLoaded = true;
                }
            }
        }
        return RESOLVERS;
    }

    // ---------------------------------------------------------------- declarations

    /** Declares a class in this scope, or returns the existing declaration when it is already declared. */
    public Declaration declare(Class< ? > type, Mode mode, String declaredAt) {
        return declare(type, mode, declaredAt, false);
    }

    /** @param proxyRequested the test asked for an instance mock ({@code @Stub(proxy = true)} / {@code stubProxy}) */
    public Declaration declare(Class< ? > type, Mode mode, String declaredAt, boolean proxyRequested) {
        Declaration existing = findDeclared(type);
        if (existing != null) {
            if (existing.mode() != mode) {
                throw new StuntException(type.getName() + " is already declared as " + existing.mode().annotation()
                    + " (" + Sites.firstLine(existing.declaredAt()) + ") and cannot also be " + mode.annotation() + " (" + declaredAt
                    + ")");
            }
            return existing;
        }
        Class< ? > implementation = null;
        Object resolvedInstance = null;
        if (!Hierarchy.isJdk(type)) {
            for (TypeResolver resolver : resolvers()) {
                Object instance;
                try {
                    instance = resolver.instanceOf(type);
                }
                catch (RuntimeException | LinkageError e) {
                    throw new StuntException("TypeResolver " + resolver.getClass().getName() + " threw while resolving "
                        + type.getName() + " (instanceOf): " + e, e);
                }
                if (instance == null) {
                    continue;
                }
                try {
                    implementation = resolver.implementationOf(type, instance);
                }
                catch (RuntimeException | LinkageError e) {
                    throw new StuntException("TypeResolver " + resolver.getClass().getName() + " threw while resolving "
                        + type.getName() + " (implementationOf): " + e, e);
                }
                if (implementation == null) {
                    throw new StuntException("TypeResolver " + resolver.getClass().getName() + " returned an instance for "
                        + type.getName() + " but no implementation class");
                }
                if (!implementation.isInstance(instance)) {
                    throw new StuntException("TypeResolver " + resolver.getClass().getName() + " returned "
                        + implementation.getName() + " for " + type.getName() + ", but its instance is a "
                        + instance.getClass().getName());
                }
                if (!type.isAssignableFrom(implementation)) {
                    throw new StuntException("TypeResolver " + resolver.getClass().getName() + " returned "
                        + implementation.getName() + " for " + type.getName() + ", which is not a " + type.getSimpleName());
                }
                resolvedInstance = instance;
                break;
            }
        }
        boolean proxy = false;
        if (implementation == null) {
            boolean abstractType = type.isInterface() || Modifier.isAbstract(type.getModifiers());
            if (proxyRequested && !abstractType) {
                throw new StuntException(type.getName() + " is a class and is intercepted in place; proxy is for an"
                    + " interface or abstract class without resolver. Declare it with @Stub / stub(" + type.getSimpleName()
                    + ".class)");
            }
            if (abstractType) {
                String what = type.isInterface() ? "an interface" : "abstract";
                if (mode == Mode.PARTIAL) {
                    throw new StuntException(type.getName() + " is " + what + " and no TypeResolver provided its"
                        + " instance, so there is no original code to run: @StubPartially is not possible. Declare the"
                        + " implementation class, or register a TypeResolver");
                }
                if (!proxyRequested) {
                    throw new StuntException(type.getName() + " is " + what + " and no TypeResolver provided its"
                        + " instance. Register one (StuntSettings.addTypeResolver) before the first test class opens:"
                        + " a static field is bound before any @BeforeAll runs, so use a static initializer or"
                        + " META-INF/services. Or declare the implementation class, or ask for an instance mock"
                        + " explicitly with @Stub(proxy = true) / stubProxy(" + type.getSimpleName() + ".class),"
                        + " which the test then has to hand to the code under test");
                }
                proxy = true;
            }
            implementation = type;
        }
        else if (proxyRequested) {
            throw new StuntException(type.getName() + " is resolved to " + implementation.getName()
                + " by a TypeResolver; proxy is only for types no resolver knows");
        }
        if (!proxy && (implementation.isInterface() || Modifier.isAbstract(implementation.getModifiers()))) {
            throw new StuntException("TypeResolver returned " + implementation.getName() + " for " + type.getName()
                + ", but that is not a concrete class");
        }
        if (findDeclared(implementation) != null) {
            Declaration other = findDeclared(implementation);
            if (other.mode() != mode) {
                throw new StuntException(type.getName() + " resolves to " + implementation.getName()
                    + ", which is already declared as " + other.mode().annotation() + " (" + Sites.firstLine(other.declaredAt()) + ")");
            }
            return other;
        }
        Object handle;
        if (proxy) {
            handle = Handles.proxy(type);
            implementation = handle.getClass();
        }
        else {
            Engine.instrument(implementation);
            handle = resolvedInstance != null ? resolvedInstance : Handles.uninitialized(implementation);
        }
        Declaration declaration = new Declaration(type, implementation, mode, handle, proxy, declaredAt, this,
            currentGroup());
        declarations.add(declaration);
        handles.put(handle, declaration);
        return declaration;
    }

    /**
     * Declares one real object: only calls on it are intercepted, in the given mode; other instances of its class
     * keep whatever the class is declared as, or stay real. The class is instrumented if it is not yet.
     */
    public Declaration declareInstance(Object object, Mode mode, String declaredAt) {
        Declaration existing = declarationForHandle(object);
        if (existing != null) {
            if (!existing.instance()) {
                throw new StuntException(Types.identity(object) + " is the handle of " + existing.describe()
                    + "; an instance declaration needs a real object of the class, not its handle");
            }
            if (existing.mode() != mode) {
                throw new StuntException(Types.identity(object) + " is already declared as " + existing.mode().annotation()
                    + " (" + Sites.firstLine(existing.declaredAt()) + ") and cannot also be " + mode.annotation()
                    + " (" + declaredAt + ")");
            }
            return existing;
        }
        Class< ? > implementation = object.getClass();
        Engine.instrument(implementation);
        Declaration declaration = new Declaration(implementation, implementation, mode, object, false, true,
            declaredAt, this, currentGroup());
        declarations.add(declaration);
        handles.put(object, declaration);
        return declaration;
    }

    private Declaration findDeclared(Class< ? > type) {
        for (Scope s = this; s != null; s = s.parent) {
            for (Declaration d : s.declarations) {
                if (!d.instance() && (d.declaredType() == type || d.implementation() == type)) {
                    return d;
                }
            }
        }
        return null;
    }

    public List< Declaration > declarations() {
        List< Declaration > result = new ArrayList<>();
        java.util.ArrayDeque< Scope > levels = new java.util.ArrayDeque<>();
        for (Scope s = this; s != null; s = s.parent) {
            levels.push(s);
        }
        for (Scope level : levels) {
            result.addAll(level.declarations);
        }
        return result;
    }

    /** The declaration a call on {@code receiver} belongs to, most specific class first. */
    public Declaration declarationFor(Object receiver) {
        Declaration best = null;
        for (Declaration d : declarations()) {
            if (d.covers(receiver) && (best == null || d.moreSpecificThan(best))) {
                best = d;
            }
        }
        return best;
    }

    /** The declaration a static call declared in the named class belongs to. */
    Declaration declarationForStatic(String declaringTypeName) {
        Declaration best = null;
        for (Declaration d : declarations()) {
            if (d.coversStatic(declaringTypeName) && (best == null || d.moreSpecificThan(best))) {
                best = d;
            }
        }
        return best;
    }

    /** The declaration behind a handle, or {@code null} for an ordinary object. */
    public Declaration declarationForHandle(Object handle) {
        for (Scope s = this; s != null; s = s.parent) {
            Declaration d = s.handles.get(handle);
            if (d != null) {
                return d;
            }
        }
        return null;
    }

    /**
     * Every declaration a class named in a closure relates to, most specific first. Several unrelated ones
     * (two declared subclasses of the class that declares the method) are possible; the caller decides.
     */
    public List< Declaration > declarationsRelatedTo(Class< ? > owner) {
        List< Declaration > result = new ArrayList<>();
        for (Declaration d : declarations()) {
            if (d.relatesTo(owner)) {
                result.add(d);
            }
        }
        result.sort((a, b) -> a.moreSpecificThan(b) && !b.moreSpecificThan(a) ? -1
            : b.moreSpecificThan(a) && !a.moreSpecificThan(b) ? 1 : 0);
        return result;
    }

    /** The static-call declaration for a method declared in {@code owner}, or {@code null}. */
    public Declaration declarationForStaticIn(Class< ? > owner) {
        return declarationForStatic(owner.getName());
    }

    /** The declaration a class named in a closure relates to, or {@code null}. */
    public Declaration declarationRelatedTo(Class< ? > owner) {
        Declaration best = null;
        for (Declaration d : declarations()) {
            if (d.relatesTo(owner) && (best == null || d.moreSpecificThan(best))) {
                best = d;
            }
        }
        return best;
    }

    // ---------------------------------------------------------------- chains

    public Chain newGroupChain(Chain.Kind kind, Declaration declaration,
                               org.eu.de.stuntmock.MethodMatcher matcher, Object receiver, String declaredAt) {
        Chain chain = new Chain(kind, declaration, matcher, receiver, declaredAt, isClassScope(), currentGroup());
        chains.add(chain);
        return chain;
    }

    public Chain newChain(Chain.Kind kind, Declaration declaration, java.lang.reflect.Method method, Object receiver,
                          List< ArgMatcher > matchers, String declaredAt) {
        Chain chain = new Chain(kind, declaration, method, receiver, matchers, declaredAt, isClassScope(),
            currentGroup());
        chains.add(chain);
        return chain;
    }

    List< Chain > chains() {
        return chains;
    }

    /** Seals every chain so the next call dispatches against fixed counts. */
    void sealChains() {
        for (Chain chain : chains) {
            chain.seal();
        }
    }

    // ---------------------------------------------------------------- capture, bypass, internal guard

    public static final class Capture {
        /** The declarations the captured call may belong to; the dispatcher records which one it was. */
        public final List< Declaration > candidates;
        public final String methodName;
        public final String descriptor;
        public Declaration declaration;
        public Object receiver;
        public Object[] args;
        public List< ArgMatcher > matchers;
        public boolean done;

        public Capture(List< Declaration > candidates, String methodName, String descriptor) {
            this.candidates = candidates;
            this.methodName = methodName;
            this.descriptor = descriptor;
        }

        boolean accepts(Declaration d) {
            return candidates.contains(d);
        }
    }

    Capture capture() {
        return capture;
    }

    public void beginCapture(Capture c) {
        this.capture = c;
    }

    public void endCapture() {
        this.capture = null;
    }

    private record Bypass(Object receiver, String methodName, String descriptor) {
    }

    void bypassNext(Object receiver, String methodName, String descriptor) {
        bypass = new Bypass(receiver, methodName, descriptor);
    }

    void clearBypass() {
        bypass = null;
    }

    boolean consumeBypass(Object receiver, String methodName, String descriptor) {
        Bypass b = bypass;
        if (b != null && b.receiver == receiver && b.methodName.equals(methodName)
            && b.descriptor.equals(descriptor)) {
            bypass = null;
            return true;
        }
        return false;
    }

    static boolean insideFramework() {
        return INTERNAL.get()[0] > 0;
    }

    static void enterFramework() {
        INTERNAL.get()[0]++;
    }

    static void exitFramework() {
        INTERNAL.get()[0]--;
    }

    // ---------------------------------------------------------------- trace and failures

    CallTrace trace() {
        return trace;
    }

    void fail(AssertionError error) {
        failures.add(error);
    }

    private String group;
    private int quiet;
    /** Declarations made strict in this scope (class scope: every test; test scope: this test only). */
    private final Set< Declaration > strict = java.util.Collections.newSetFromMap(new IdentityHashMap<>());

    /** Whether unmapped calls on this declaration fail, in this scope or an enclosing one. */
    public boolean isStrict(Declaration declaration) {
        for (Scope s = this; s != null; s = s.parent) {
            if (s.strict.contains(declaration)) {
                return true;
            }
        }
        return false;
    }

    /** {@code verify(...)} on an existing declaration: unmapped calls fail from now on, within this scope. */
    public void makeStrict(Declaration declaration) {
        strict.add(declaration);
    }

    /** While an infrastructure chain answers, calls its closure makes are its internals and are not traced. */
    void enterQuiet() {
        quiet++;
    }

    void exitQuiet() {
        quiet--;
    }

    boolean quiet() {
        return quiet > 0;
    }

    /** The label of the {@code StuntSettings.infrastructure(...)} block currently running in this scope, or {@code null}. */
    public String currentGroup() {
        return group;
    }

    public void setCurrentGroup(String group) {
        this.group = group;
    }

    public List< AssertionError > failures() {
        return failures;
    }

    /** End-of-test check: every verify chain satisfied and no recorded failure. */
    public void verifyAll() {
        if (!failures.isEmpty()) {
            throw failures.get(0);
        }
        List< String > missing = new ArrayList<>();
        Declaration firstMissing = null;
        for (Chain chain : chains) {
            if (chain.kind() != Chain.Kind.VERIFY) {
                continue;
            }
            for (Chain.Step step : chain.unsatisfied()) {
                if (firstMissing == null) {
                    firstMissing = chain.declaration();
                }
                missing.add(chain.describeShort() + ": expected " + expected(step) + " to " + step.answer.describe()
                    + ", got " + step.count + " — declared " + chain.declaredAt()
                    + (chain.classLevel() ? " (class level)" : ""));
            }
        }
        if (!missing.isEmpty()) {
            enterFramework(); // rendering must not dispatch toString() of declared objects
            StringBuilder sb = new StringBuilder("Missing calls in ").append(name).append(":\n");
            for (String m : missing) {
                sb.append("  ").append(m).append('\n');
            }
            sb.append(Dump.focused(this, firstMissing));
            exitFramework();
            throw new org.eu.de.stuntmock.MissingCallError(sb.toString());
        }
    }

    private static String expected(Chain.Step step) {
        if (step.min == step.max) {
            return step.min + (step.min == 1 ? " call" : " calls");
        }
        if (step.max == Chain.Step.UNBOUNDED) {
            return "at least " + step.min + " calls";
        }
        return step.min + ".." + step.max + " calls";
    }
}
