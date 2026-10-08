package org.eu.de.stuntmock.internal;

import java.lang.reflect.Method;
import java.util.List;

import org.eu.de.stuntmock.Mode;
import org.eu.de.stuntmock.UnexpectedCallError;
import org.eu.de.stuntmock.dispatch.StuntHandler;
import org.eu.de.stuntmock.dispatch.StuntResult;

/**
 * Decides what an intercepted call does: capture it (inside a {@code when}/{@code verify} closure), answer it
 * from a matching chain, apply the declared mode, or let the original run. Internal.
 */
public final class Dispatch implements StuntHandler {

    @Override
    public StuntResult onCall(Object self, String declaringType, String methodName, String descriptor,
                              Object[] args) {
        if (Scope.insideFramework()) {
            return null;
        }
        Scope scope = Scope.current();
        if (scope == null) {
            return null;
        }
        if (scope.consumeBypass(self, methodName, descriptor)) {
            return null;
        }
        Scope.enterFramework();
        try {
            Declaration declaration = self == null ? scope.declarationForStatic(declaringType)
                : scope.declarationFor(self);
            if (declaration == null) {
                return null;
            }
            return dispatch(scope, declaration, self, methodName, descriptor, args);
        }
        catch (Throwable t) {
            return StuntResult.throwing(t);
        }
        finally {
            Scope.exitFramework();
        }
    }

    /** Calls on proxy handles arrive here instead of through inlined advice. */
    static Object onProxyCall(Object proxy, Method method, Object[] args) throws Throwable {
        String descriptor = Descriptors.of(method);
        Scope scope = Scope.current();
        Declaration declaration = scope == null ? null : scope.declarationForHandle(proxy);
        StuntResult result = null;
        if (declaration != null && !Scope.insideFramework()) {
            Scope.enterFramework();
            try {
                result = dispatch(scope, declaration, proxy, method.getName(), descriptor, args);
            }
            finally {
                Scope.exitFramework();
            }
        }
        if (result == null) {
            return identityAnswer(proxy, method, args, Types.defaultValue(method.getReturnType()));
        }
        if (result.thrown != null) {
            throw result.thrown;
        }
        return result.value;
    }

    private static StuntResult dispatch(Scope scope, Declaration declaration, Object self, String methodName,
                                        String descriptor, Object[] args) {
        Scope.Capture capture = scope.capture();
        if (capture != null && !capture.done && capture.accepts(declaration)
            && capture.methodName.equals(methodName) && capture.descriptor.equals(descriptor)) {
            capture.declaration = declaration;
            capture.receiver = self;
            capture.args = args.clone();
            capture.matchers = MatcherStack.drain();
            capture.done = true;
            Method method = Descriptors.find(declaration.implementation(), methodName, descriptor);
            return StuntResult.returning(method == null ? null : Types.defaultValue(method.getReturnType()));
        }
        // newest matching chain decides: it answers while it has steps left; an exhausted verify chain fails the
        // call (its count is an assertion); an exhausted when chain has no opinion and defers to older chains
        // a chain for one signature beats a chain for a group of methods, whatever the order; among equals the
        // newest wins
        List< Chain > chains = scope.chains();
        for (int pass = 0; pass < 2; pass++) {
        for (int i = chains.size() - 1; i >= 0; i--) {
            Chain chain = chains.get(i);
            if (chain.isGroup() != (pass == 1) || !chain.matches(declaration, self, methodName, descriptor, args)) {
                continue;
            }
            if (!chain.exhausted()) {
                return answer(scope, declaration, chain, chain.methodFor(declaration, methodName, descriptor), self,
                    descriptor, args);
            }
            if (chain.kind() == Chain.Kind.VERIFY) {
                return fail(scope, declaration, self, methodName, args, new UnexpectedCallError(
                    "Unexpected call " + describe(declaration, self, methodName, args) + ": " + chain.describeShort()
                        + " is already satisfied and allows no further call\n    " + chain.describe() + "\n"
                        + Dump.focused(scope, declaration)));
            }
        }
        }
        Object handleAnswer = handleMethodAnswer(scope, self, methodName, descriptor, args);
        if (handleAnswer != null) {
            trace(scope).record(declaration, self, methodName, args, "handle identity");
            return StuntResult.returning(handleAnswer);
        }
        return unmapped(scope, declaration, self, methodName, descriptor, args);
    }

    /** The trace, or a discarding one while an infrastructure chain is answering. */
    private static CallTrace trace(Scope scope) {
        return scope.quiet() ? CallTrace.DISCARDING : scope.trace();
    }

    private static StuntResult answer(Scope scope, Declaration declaration, Chain chain, Method method, Object self,
                                      String descriptor, Object[] args) {
        Chain.Step step = chain.current();
        step.count++;
        chain.matched(args);
        InvocationImpl invocation = new InvocationImpl(scope, self, method, descriptor, args);
        Object value;
        boolean quiet = chain.group() != null;
        try {
            Scope.exitFramework();
            if (quiet) {
                scope.enterQuiet();
            }
            try {
                value = step.answer.answer(invocation, step);
            }
            finally {
                if (quiet) {
                    scope.exitQuiet();
                }
                Scope.enterFramework();
            }
        }
        catch (Throwable t) {
            trace(scope).record(declaration, self, method.getName(), args,
                chain.describeShort() + " step threw " + t.getClass().getSimpleName(), chain.group());
            Log.debug(() -> "call " + describe(declaration, self, method.getName(), args) + " -> "
                + chain.describeShort() + " -> threw " + t);
            return StuntResult.throwing(t);
        }
        if (value == Answer.PROCEED) {
            trace(scope).record(declaration, self, method.getName(), args,
                chain.describeShort() + " -> original", chain.group());
            Log.debug(() -> "call " + describe(declaration, self, method.getName(), args) + " -> "
                + chain.describeShort() + " -> original");
            return null;
        }
        if (value == Answer.MODE_DEFAULT) {
            // a chain without a terminal keeps the class's natural behaviour: real code for a partial mock and for
            // a @Verify class (a strict audit), the default value for a @Stub class. On a proxy there is no real
            // code, so it answers the default value as well.
            if (declaration.mode() != Mode.STUB && !declaration.proxy()) {
                trace(scope).record(declaration, self, method.getName(), args,
                    chain.describeShort() + " -> original", chain.group());
                Log.debug(() -> "call " + describe(declaration, self, method.getName(), args) + " -> "
                    + chain.describeShort() + " -> original");
                return null;
            }
            Object defaultValue = Types.defaultValue(method.getReturnType());
            trace(scope).record(declaration, self, method.getName(), args,
                chain.describeShort() + " -> default " + Types.format(defaultValue), chain.group());
            Log.debug(() -> "call " + describe(declaration, self, method.getName(), args) + " -> "
                + chain.describeShort() + " -> default " + Types.format(defaultValue));
            return StuntResult.returning(defaultValue);
        }
        Object coerced = method.getReturnType() == void.class ? null
            : Types.coerce(value, method.getReturnType(), chain.describeShort());
        trace(scope).record(declaration, self, method.getName(), args, chain.describeShort() + " -> "
            + Types.format(coerced), chain.group());
        Log.debug(() -> "call " + describe(declaration, self, method.getName(), args) + " -> "
            + chain.describeShort() + " -> " + Types.format(coerced));
        return StuntResult.returning(coerced);
    }

    /** {@code toString()}, {@code hashCode()} and {@code equals(Object)}: never mocked implicitly. */
    static boolean isObjectContract(String methodName, String descriptor) {
        return ("toString".equals(methodName) && "()Ljava/lang/String;".equals(descriptor))
            || ("hashCode".equals(methodName) && "()I".equals(descriptor))
            || ("equals".equals(methodName) && "(Ljava/lang/Object;)Z".equals(descriptor));
    }

    private static StuntResult unmapped(Scope scope, Declaration declaration, Object self, String methodName,
                                        String descriptor, Object[] args) {
        Mode mode = declaration.mode();
        if (self != null && isObjectContract(methodName, descriptor)) {
            // an unmapped toString/hashCode/equals runs its original on every mode: production code puts these
            // objects into sets and messages, and a mocked hashCode of 0 or a failing toString would only break
            // that. Mapping them explicitly (when(x::hashCode).never()) still works.
            trace(scope).record(declaration, self, methodName, args, "original (Object contract)");
            Log.debug(() -> "call " + describe(declaration, self, methodName, args) + " -> original (Object contract)");
            return null;
        }
        if (scope.isStrict(declaration)) {
            return fail(scope, declaration, self, methodName, args, new UnexpectedCallError(
                "Unexpected call " + describe(declaration, self, methodName, args) + ": no when(...)/verify(...)"
                    + " maps it and the " + (declaration.instance() ? "object" : "class") + " is declared "
                    + declaration.mode().annotation() + " @Verify (" + Sites.firstLine(declaration.declaredAt()) + ")\n"
                    + Dump.focused(scope, declaration)));
        }
        if (mode == Mode.PARTIAL) {
            trace(scope).record(declaration, self, methodName, args, "original (unmapped)");
            Log.debug(() -> "call " + describe(declaration, self, methodName, args) + " -> original");
            return null;
        }
        Method method = Descriptors.find(declaration.implementation(), methodName, descriptor);
        Object value = method == null ? null : Types.defaultValue(method.getReturnType());
        trace(scope).record(declaration, self, methodName, args, "default " + Types.format(value) + " (unmapped)");
        Log.debug(() -> "call " + describe(declaration, self, methodName, args) + " -> default " + Types.format(value));
        return StuntResult.returning(value);
    }

    private static StuntResult fail(Scope scope, Declaration declaration, Object self, String methodName,
                                    Object[] args, UnexpectedCallError error) {
        trace(scope).record(declaration, self, methodName, args, "UNEXPECTED");
        Log.debug(() -> "call " + describe(declaration, self, methodName, args) + " -> UNEXPECTED");
        scope.fail(error);
        return StuntResult.throwing(error);
    }

    /** Identity-based {@code toString}, {@code hashCode} and {@code equals} for handles, so that messages and debuggers work. */
    private static Object handleMethodAnswer(Scope scope, Object self, String methodName, String descriptor,
                                             Object[] args) {
        if (self == null || scope.declarationForHandle(self) == null) {
            return null;
        }
        return identityAnswer(self, methodName, descriptor, args);
    }

    private static Object identityAnswer(Object self, String methodName, String descriptor, Object[] args) {
        if (methodName.equals("toString") && descriptor.equals("()Ljava/lang/String;")) {
            return "handle of " + self.getClass().getSimpleName().replace("$StuntProxy", "") + "@"
                + Integer.toHexString(System.identityHashCode(self));
        }
        if (methodName.equals("hashCode") && descriptor.equals("()I")) {
            return System.identityHashCode(self);
        }
        if (methodName.equals("equals") && descriptor.equals("(Ljava/lang/Object;)Z")) {
            return self == args[0];
        }
        return null;
    }

    private static Object identityAnswer(Object proxy, Method method, Object[] args, Object fallback) {
        Object answer = identityAnswer(proxy, method.getName(), Descriptors.of(method), args);
        return answer != null ? answer : fallback;
    }

    private static String describe(Declaration declaration, Object self, String methodName, Object[] args) {
        String receiver = self == null ? declaration.implementation().getSimpleName() : Types.identity(self);
        return receiver + "." + methodName + "(" + Types.formatArgs(args) + ")";
    }
}
