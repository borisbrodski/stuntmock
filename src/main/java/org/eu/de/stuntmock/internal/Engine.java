package org.eu.de.stuntmock.internal;

import java.io.File;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eu.de.stuntmock.StuntException;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.loading.ClassInjector;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.utility.JavaModule;

/**
 * Installs the agent once per JVM, injects the bootstrap dispatcher, and instruments declared classes on demand.
 * Instrumentation is permanent for the JVM's lifetime; the inlined advice is a no-op while no scope is open.
 * Internal.
 */
public final class Engine {

    private static final String DISPATCH_PACKAGE = "org.eu.de.stuntmock.dispatch.";
    private static final List< String > DISPATCH_CLASSES = List.of(DISPATCH_PACKAGE + "StuntResult",
        DISPATCH_PACKAGE + "StuntHandler", DISPATCH_PACKAGE + "StuntDispatcher");
    private static final Set< String > INSTRUMENTED_NAMES = Collections.synchronizedSet(new HashSet<>());
    private static final Set< Class< ? > > INSTRUMENTED = new HashSet<>();
    private static Instrumentation instrumentation;
    private static boolean installed;

    private Engine() {
    }

    public static synchronized void install() {
        if (installed) {
            return;
        }
        instrumentation = ByteBuddyAgent.install();
        injectDispatcher();
        registerHandler();
        new AgentBuilder.Default()
            .disableClassFormatChanges()
            .ignore(ElementMatchers.none())
            .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
            .with(AgentBuilder.InitializationStrategy.NoOp.INSTANCE)
            .with(AgentBuilder.TypeStrategy.Default.REDEFINE)
            .with(new ErrorListener())
            .type((typeDescription, classLoader, module, classBeingRedefined, protectionDomain) ->
                INSTRUMENTED_NAMES.contains(typeDescription.getName()))
            .transform((builder, typeDescription, classLoader, module, protectionDomain) ->
                builder.visit(Advice.to(StuntAdvice.class).on(ElementMatchers.isMethod()
                    .and(ElementMatchers.not(ElementMatchers.isAbstract()))
                    .and(ElementMatchers.not(ElementMatchers.isNative()))
                    .and(ElementMatchers.not(ElementMatchers.isSynthetic())))))
            .installOn(instrumentation);
        installed = true;
    }

    /** Instruments the class and every class of its hierarchy whose method bodies can run for it. */
    public static synchronized void instrument(Class< ? > type) {
        install();
        for (Class< ? > c : Hierarchy.of(type)) {
            if (INSTRUMENTED.contains(c)) {
                continue;
            }
            if (!instrumentation.isModifiableClass(c)) {
                throw new StuntException(c.getName() + " cannot be instrumented (not modifiable by the JVM)");
            }
            INSTRUMENTED_NAMES.add(c.getName());
            TRANSFORM_ERRORS.remove(c.getName());
            try {
                instrumentation.retransformClasses(c);
            }
            catch (UnmodifiableClassException | RuntimeException | LinkageError e) {
                INSTRUMENTED_NAMES.remove(c.getName());
                throw new StuntException("Could not instrument " + c.getName() + ": " + e, e);
            }
            // the JVM swallows exceptions a class file transformer throws; the listener records them for us
            Throwable transformError = TRANSFORM_ERRORS.remove(c.getName());
            if (transformError != null) {
                INSTRUMENTED_NAMES.remove(c.getName());
                throw new StuntException("Could not instrument " + c.getName() + ": " + transformError, transformError);
            }
            INSTRUMENTED.add(c);
        }
    }

    public static synchronized boolean isInstrumented(Class< ? > type) {
        return INSTRUMENTED.contains(type);
    }

    // ---------------------------------------------------------------- bootstrap dispatcher

    private static void injectDispatcher() {
        ClassLoader own = Engine.class.getClassLoader();
        if (loadedByBootstrap(DISPATCH_CLASSES.get(2))) {
            return;
        }
        Map< String, byte[] > types = new HashMap<>();
        ClassFileLocator locator = ClassFileLocator.ForClassLoader.of(own);
        try {
            for (String name : DISPATCH_CLASSES) {
                types.put(name, locator.locate(name).resolve());
            }
            File temp = Files.createTempDirectory("stunt-dispatch").toFile();
            temp.deleteOnExit();
            ClassInjector.UsingInstrumentation.of(temp, ClassInjector.UsingInstrumentation.Target.BOOTSTRAP,
                instrumentation).injectRaw(types);
        }
        catch (Exception e) {
            throw new StuntException("Could not inject the Stunt dispatcher into the bootstrap class loader", e);
        }
        for (String name : DISPATCH_CLASSES) {
            Class< ? > seenByApp;
            try {
                seenByApp = Class.forName(name, false, own);
            }
            catch (ClassNotFoundException e) {
                throw new StuntException("Dispatcher class " + name + " is not visible after injection", e);
            }
            if (seenByApp.getClassLoader() != null) {
                throw new StuntException(name + " was loaded by the application class loader before Stunt was"
                    + " installed. Nothing may reference the package " + DISPATCH_PACKAGE + " directly; make sure"
                    + " Stunt is installed by its JUnit extension before any such reference");
            }
        }
    }

    private static boolean loadedByBootstrap(String name) {
        try {
            return Class.forName(name, false, null).getClassLoader() == null;
        }
        catch (ClassNotFoundException e) {
            return false;
        }
    }

    private static void registerHandler() {
        try {
            Class< ? > dispatcher = Class.forName(DISPATCH_CLASSES.get(2), true, null);
            Field handler = dispatcher.getField("handler");
            handler.set(null, new Dispatch());
        }
        catch (ReflectiveOperationException e) {
            throw new StuntException("Could not register the Stunt handler", e);
        }
    }

    // ---------------------------------------------------------------- diagnostics

    private static final java.util.Map< String, Throwable > TRANSFORM_ERRORS = new java.util.concurrent.ConcurrentHashMap<>();

    private static final class ErrorListener extends AgentBuilder.Listener.Adapter {
        @Override
        public void onError(String typeName, ClassLoader classLoader, JavaModule module, boolean loaded,
                            Throwable throwable) {
            TRANSFORM_ERRORS.put(typeName, throwable);
            Log.warn("Instrumentation of " + typeName + " failed: " + throwable, throwable);
        }

        @Override
        public void onTransformation(TypeDescription typeDescription, ClassLoader classLoader, JavaModule module,
                                     boolean loaded, net.bytebuddy.dynamic.DynamicType dynamicType) {
            Log.debug(() -> "instrumented " + typeDescription.getName());
        }
    }
}
