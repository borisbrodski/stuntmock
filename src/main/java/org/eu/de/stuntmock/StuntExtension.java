package org.eu.de.stuntmock;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.TestInstancePostProcessor;

import org.eu.de.stuntmock.internal.Engine;
import org.eu.de.stuntmock.internal.MatcherStack;
import org.eu.de.stuntmock.internal.Declaration;
import org.eu.de.stuntmock.internal.Scope;

/**
 * Drives the Stunt lifecycle: {@code @ExtendWith(StuntExtension.class)} on the test class (or automatic
 * registration through {@code junit.jupiter.extensions.autodetection.enabled=true}).
 *
 * <pre>
 * beforeAll     install the agent, open the class scope, declare class-level annotations and static fields
 *               (and instance fields for the per-class lifecycle); @BeforeAll methods then declare and map
 *               class-level chains, which every test gets a fresh copy of
 * beforeEach    open the test scope, declare method-level annotations and (per-method lifecycle) instance fields
 * test          parameters annotated with @Stub/@Verify/@StubPartially are resolved as handles
 * afterEach     check every verify(...) chain, rethrow recorded fail-fast errors, close the test scope
 * afterAll      close the class scope
 * </pre>
 */
public final class StuntExtension implements BeforeAllCallback, AfterAllCallback, BeforeEachCallback,
    AfterEachCallback, TestInstancePostProcessor, ParameterResolver {

    @Override
    public void beforeAll(ExtensionContext context) {
        Engine.install();
        Class< ? > testClass = context.getRequiredTestClass();
        initialize(testClass); // static initializers of the test class and its base classes register resolvers
        StuntSettings.runInitialization(testClass.getName());
        Scope scope = Scope.openClassScope(testClass);
        try {
            StuntSettings.applyDefaults();
            if (StuntSettings.freezeClockEnabled()) {
                CLOCK.beforeAll(context);
            }
            declareTypeAnnotations(scope, testClass);
            Optional< Object > instance = context.getTestInstance();
            for (Field field : annotatedFields(testClass)) {
                if (Modifier.isStatic(field.getModifiers())) {
                    bind(scope, field, null);
                }
                else if (instance.isPresent()) {
                    bind(scope, field, instance.get());
                }
            }
        }
        catch (RuntimeException | Error e) {
            Scope.closeClassScope(); // a failed beforeAll must not leave the scope behind
            throw e;
        }
    }

    @Override
    public void postProcessTestInstance(Object testInstance, ExtensionContext context) {
        if (Scope.classScope() == null) {
            return; // per-class lifecycle: beforeAll binds the instance fields
        }
        Scope scope = Scope.testScope();
        if (scope == null) {
            scope = Scope.openTestScope(context.getDisplayName());
        }
        for (Field field : annotatedFields(testInstance.getClass())) {
            if (!Modifier.isStatic(field.getModifiers())) {
                bind(scope, field, testInstance);
            }
        }
    }

    /** Drives the frozen clock for every class when {@code StuntSettings.freezeClock(true)} is set. */
    private static final org.eu.de.stuntmock.time.FrozenClock CLOCK = new org.eu.de.stuntmock.time.FrozenClock();

    @Override
    public void beforeEach(ExtensionContext context) {
        Scope scope = Scope.testScope();
        if (scope == null) {
            scope = Scope.openTestScope(context.getDisplayName());
        }
        if (StuntSettings.freezeClockEnabled()) {
            CLOCK.beforeEach(context);
        }
        Optional< java.lang.reflect.Method > method = context.getTestMethod();
        if (method.isPresent()) {
            try {
                declareAnnotations(scope, method.get(), "@%s on " + method.get().getName() + "()");
            }
            catch (RuntimeException | Error e) {
                Scope.closeTestScope();
                MatcherStack.clear();
                throw e;
            }
        }
    }

    @Override
    public void afterEach(ExtensionContext context) {
        Scope scope = Scope.testScope();
        try {
            if (scope != null) {
                scope.verifyAll();
            }
        }
        finally {
            Scope.closeTestScope();
            MatcherStack.clear();
        }
    }

    @Override
    public void afterAll(ExtensionContext context) {
        if (StuntSettings.freezeClockEnabled()) {
            CLOCK.afterAll(context);
        }
        Scope.closeClassScope();
    }

    // ---------------------------------------------------------------- parameters

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        return modeOf(parameterContext.getParameter()) != null;
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        Parameter parameter = parameterContext.getParameter();
        Mode mode = modeOf(parameter);
        Scope scope = Scope.current();
        if (scope == null) {
            throw new ParameterResolutionException("No Stunt scope open for parameter " + parameter.getName());
        }
        Declaration declaration = scope.declare(parameter.getType(), mode, mode.annotation()
            + strictSuffix(parameter) + " on parameter " + parameter.getName() + " of "
            + parameterContext.getDeclaringExecutable().getName() + "()", proxyRequested(parameter));
        if (isStrict(parameter)) {
            scope.makeStrict(declaration);
        }
        return declaration.handle();
    }

    // ---------------------------------------------------------------- helpers

    private static void initialize(Class< ? > testClass) {
        try {
            Class.forName(testClass.getName(), true, testClass.getClassLoader());
        }
        catch (ClassNotFoundException e) {
            throw new StuntException("Cannot initialize " + testClass.getName(), e);
        }
    }

    private static void declareTypeAnnotations(Scope scope, Class< ? > testClass) {
        List< Class< ? > > chain = new ArrayList<>();
        for (Class< ? > c = testClass; c != null && c != Object.class; c = c.getSuperclass()) {
            chain.add(0, c);
        }
        for (Class< ? > c : chain) {
            declareAnnotations(scope, c, "@%s on class " + c.getSimpleName());
        }
    }

    private static void declareAnnotations(Scope scope, AnnotatedElement element, String siteFormat) {
        for (Stub stub : element.getAnnotationsByType(Stub.class)) {
            for (Class< ? > type : stub.value()) {
                scope.declare(type, Mode.STUB, String.format(siteFormat, stub.proxy() ? "Stub(proxy)" : "Stub"), stub.proxy());
            }
        }
        for (StubPartially partially : element.getAnnotationsByType(StubPartially.class)) {
            for (Class< ? > type : partially.value()) {
                scope.declare(type, Mode.PARTIAL, String.format(siteFormat, "StubPartially"));
            }
        }
        for (Verify verify : element.getAnnotationsByType(Verify.class)) {
            for (Class< ? > type : verify.value()) {
                // @Verify alone is @Stub @Verify; next to @StubPartially on the same element it keeps that policy
                Declaration declared = declaredOn(element, type) ? scope.declarationRelatedTo(type)
                    : scope.declare(type, Mode.STUB, String.format(siteFormat, "Verify"));
                scope.makeStrict(declared);
            }
        }
        for (Audit audit : element.getAnnotationsByType(Audit.class)) {
            for (Class< ? > type : audit.value()) {
                scope.makeStrict(scope.declare(type, Mode.PARTIAL, String.format(siteFormat, "Audit")));
            }
        }
    }

    private static List< Field > annotatedFields(Class< ? > testClass) {
        List< Field > result = new ArrayList<>();
        for (Class< ? > c = testClass; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (modeOf(field) != null) {
                    result.add(field);
                }
            }
        }
        return result;
    }

    private static void bind(Scope scope, Field field, Object instance) {
        Mode mode = modeOf(field);
        Declaration declaration = scope.declare(field.getType(), mode, mode.annotation()
            + strictSuffix(field) + " on field " + field.getName() + " of "
            + field.getDeclaringClass().getSimpleName(), proxyRequested(field));
        if (isStrict(field)) {
            scope.makeStrict(declaration);
        }
        Object handle = declaration.handle();
        try {
            field.setAccessible(true);
            field.set(instance, handle);
        }
        catch (IllegalAccessException e) {
            throw new StuntException("Cannot assign the handle to field " + field.getName(), e);
        }
    }

    /** Whether {@code @Stub}/{@code @StubPartially} on {@code element} lists {@code type}. */
    private static boolean declaredOn(AnnotatedElement element, Class< ? > type) {
        for (Stub stub : element.getAnnotationsByType(Stub.class)) {
            if (java.util.Arrays.asList(stub.value()).contains(type)) {
                return true;
            }
        }
        for (StubPartially partially : element.getAnnotationsByType(StubPartially.class)) {
            if (java.util.Arrays.asList(partially.value()).contains(type)) {
                return true;
            }
        }
        return false;
    }

    /** The policy of a field or parameter, or {@code null} if it carries no declaration. */
    private static Mode modeOf(AnnotatedElement element) {
        int stubs = element.getAnnotationsByType(Stub.class).length;
        int partials = element.getAnnotationsByType(StubPartially.class).length;
        int audits = element.getAnnotationsByType(Audit.class).length;
        boolean verify = element.getAnnotationsByType(Verify.class).length > 0;
        if (stubs + partials + audits > 1) {
            throw new StuntException("Exactly one of @Stub, @StubPartially, @Audit is allowed on " + element);
        }
        if (audits == 1 || partials == 1) {
            return Mode.PARTIAL;
        }
        if (stubs == 1 || verify) {
            return Mode.STUB;        // @Verify alone is a strict mock
        }
        return null;
    }

    private static boolean proxyRequested(AnnotatedElement element) {
        for (Stub stub : element.getAnnotationsByType(Stub.class)) {
            if (stub.proxy()) {
                return true;
            }
        }
        return false;
    }

    private static String strictSuffix(AnnotatedElement element) {
        if (element.getAnnotationsByType(Audit.class).length > 0) {
            return " (@Audit)";
        }
        return element.getAnnotationsByType(Verify.class).length > 0 ? " @Verify" : "";
    }

    private static boolean isStrict(AnnotatedElement element) {
        return element.getAnnotationsByType(Verify.class).length > 0 || element.getAnnotationsByType(Audit.class).length > 0;
    }

    /** True if the test class runs with {@code @TestInstance(PER_CLASS)}. */
    static boolean perClass(ExtensionContext context) {
        return context.getTestInstanceLifecycle().map(l -> l == TestInstance.Lifecycle.PER_CLASS).orElse(false);
    }
}
