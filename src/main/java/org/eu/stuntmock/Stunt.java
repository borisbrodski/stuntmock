package org.eu.stuntmock;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import org.eu.stuntmock.internal.ArgMatcher;
import org.eu.stuntmock.internal.Chain;
import org.eu.stuntmock.internal.Declaration;
import org.eu.stuntmock.internal.Descriptors;
import org.eu.stuntmock.internal.Dump;
import org.eu.stuntmock.internal.LambdaInspector;
import org.eu.stuntmock.internal.MatcherStack;
import org.eu.stuntmock.internal.Scope;
import org.eu.stuntmock.internal.Sites;
import org.eu.stuntmock.internal.Types;

/**
 * The Stunt DSL. Import statically: {@code import static org.eu.stuntmock.Stunt.*;}
 *
 * <h2>Declaring</h2>
 * A class takes part in mocking once it is declared, by annotation ({@link Stub}, {@link Verify},
 * {@link StubPartially}) or by statement ({@link #stub(Class)}, {@link #verify(Class)},
 * {@link #stubPartially(Class)}). Every instance of a declared class is affected, including instances the
 * production code creates itself, and so are its static methods.
 *
 * <h2>Mapping calls</h2>
 * {@link #when(Call)} maps a call without asserting anything; {@link #verify(Call)} maps it and asserts that it
 * happens ({@code times(1)} unless a count says otherwise). Both take a closure with exactly one call, run in
 * capture mode: {@code when(() -> dao.findById(1L)).thenReturn(x)}. Declare everything before the production
 * code runs; verification happens at the call and at the end of the test.
 */
public final class Stunt {

    /** The argument matchers: {@code arg.any()}, {@code arg.eq(1)}, {@code arg.startsWith("x")}, {@code arg.captor(Foo.class)}. */
    public static final Args arg = new Args();

    /** The method matchers: {@code mtd.getters()}, {@code mtd.named("set*")}, {@code mtd.declaredIn(Base.class)}. */
    public static final Mtd mtd = new Mtd();

    private Stunt() {
    }

    // ---------------------------------------------------------------- the two most used matchers, unprefixed

    /** {@code arg.any()}: anything, including {@code null}. Not for primitive parameters (it returns {@code null}). */
    public static < T > T any() {
        return arg.any();
    }

    /** {@code arg.eq(expected)}: a value equal to {@code expected}; needed only next to other matchers. */
    public static < T > T eq(T expected) {
        return arg.eq(expected);
    }

    /** {@code arg.any(type)}: any instance of {@code type} or {@code null}; also selects the overload. */
    public static < T > T any(Class< T > type) {
        return arg.any(type);
    }

    public static int anyInt() {
        return arg.anyInt();
    }

    public static long anyLong() {
        return arg.anyLong();
    }

    public static double anyDouble() {
        return arg.anyDouble();
    }

    public static float anyFloat() {
        return arg.anyFloat();
    }

    public static boolean anyBoolean() {
        return arg.anyBoolean();
    }

    public static short anyShort() {
        return arg.anyShort();
    }

    public static byte anyByte() {
        return arg.anyByte();
    }

    public static char anyChar() {
        return arg.anyChar();
    }

    public static String anyString() {
        return arg.anyString();
    }

    public static < T > java.util.List< T > anyList() {
        return arg.anyList();
    }

    public static < T > java.util.Set< T > anySet() {
        return arg.anySet();
    }

    public static < T > java.util.Collection< T > anyCollection() {
        return arg.anyCollection();
    }

    public static < K, V > java.util.Map< K, V > anyMap() {
        return arg.anyMap();
    }

    // ---------------------------------------------------------------- declarations

    /** Declares {@code type} as {@link Stub}: unmapped calls return defaults. Returns the handle. */
    public static < T > T stub(Class< T > type) {
        return declare(type, Mode.STUB);
    }

    /**
     * A strict mock: unmapped calls fail, mapped calls without terminal answer the default value. Short for
     * {@code verify(stub(type))}; on a class already declared with either policy it only adds the strictness,
     * so {@code verify(stubPartially(type))} is a strict audit. Returns the handle.
     *
     * <pre>{@code
     * Dao dao = verify(Dao.class);                 // strict mock: nothing runs, everything must be mapped
     * verify(() -> dao.save(arg.any()));
     * }</pre>
     *
     * The four declaring verbs: {@code stub} (defaults, lenient), {@code stubPartially} (real code, lenient),
     * {@code verify} (defaults, strict), {@code audit} (real code, strict).
     */
    public static < T > T verify(Class< T > type) {
        return strict(type, Mode.STUB, "verify");
    }

    /**
     * A strict audit: unmapped calls fail, mapped calls without terminal run the real code. Short for
     * {@code verify(stubPartially(type))}. Returns the handle.
     *
     * <pre>{@code
     * audit(Account.class);                     // every Account: real, but every call must be mapped
     * when(Account.class, mtd.getters()).anyTimes();
     * verify(Account::persist);
     * }</pre>
     */
    public static < T > T audit(Class< T > type) {
        return strict(type, Mode.PARTIAL, "audit");
    }

    private static < T > T strict(Class< T > type, Mode policy, String verb) {
        Scope scope = Scope.require();
        Declaration declaration = scope.declarationRelatedTo(type);
        if (declaration == null || !declaration.declaredType().equals(type) && !declaration.implementation().equals(type)) {
            declaration = scope.declare(type, policy, Sites.caller());
        }
        else if (declaration.mode() != policy && verb.equals("audit")) {
            throw new StuntException("audit(" + type.getSimpleName() + ".class): " + type.getSimpleName()
                + " is already declared as " + declaration.mode().annotation() + " (" + Sites.firstLine(declaration.declaredAt())
                + "); an audit runs the real code. Use verify(...) to make a stub strict");
        }
        scope.makeStrict(declaration);
        return type.cast(declaration.handle());
    }

    /**
     * Declares an interface or abstract class that no {@link TypeResolver} maps as an <em>instance mock</em>: a
     * proxy object that intercepts calls on itself only, which the test hands to the code under test. The
     * programmatic twin of {@code @Stub(proxy = true)}; see {@link Stub#proxy()}.
     *
     * <pre>{@code
     * PriceService prices = stubProxy(PriceService.class);
     * when(() -> prices.rateFor("DE")).thenReturn(0.19);
     * new TaxCalculator(prices).total(order);
     * }</pre>
     */
    public static < T > T stubProxy(Class< T > type) {
        Scope scope = Scope.require();
        Declaration declaration = scope.declare(type, Mode.STUB, Sites.caller(), true);
        return type.cast(declaration.handle());
    }

    /** Declares {@code type} as {@link StubPartially}: unmapped calls run the original code. Returns the handle. */
    public static < T > T stubPartially(Class< T > type) {
        return declare(type, Mode.PARTIAL);
    }

    /**
     * Declares one object as a {@code @Stub}: unmapped calls on this instance return defaults; other instances of
     * its class keep their own declaration, or stay real. Returns the object.
     */
    public static < T > T stub(T instance) {
        return declareInstance(instance, Mode.STUB);
    }

    /**
     * A strict mock of one object: unmapped calls on <em>this</em> object fail, mapped calls without terminal
     * answer the default value, every other instance of its class stays as it is. Short for
     * {@code verify(stub(obj))}; on an object (or handle) already declared with either policy it only adds the
     * strictness. Returns the object, so it can be written inline.
     *
     * <pre>{@code
     * var m1 = new MyClass();
     * var m2 = verify(new MyClass());               // strict mock of m2 only
     * var m3 = audit(new MyClass());                  // strict audit of m3 only
     * verify(() -> m3.setText(arg.any()));
     * m1.setTitle("x");                             // m1 is not declared: real code
     * m3.setTitle("x");                             // UnexpectedCallError
     * }</pre>
     *
     * An instance declaration wins over the declaration of its class, so {@code stubPartially(m1)} exempts one
     * object from a strict class-level declaration. Statics are never covered by an instance declaration.
     */
    public static < T > T verify(T instance) {
        return strict(instance, Mode.STUB, "verify");
    }

    /**
     * A strict audit of one object: unmapped calls on <em>this</em> object fail, mapped calls without terminal
     * run the real code. Short for {@code verify(stubPartially(obj))}; see {@link #verify(Object)}.
     */
    public static < T > T audit(T instance) {
        return strict(instance, Mode.PARTIAL, "audit");
    }

    private static < T > T strict(T instance, Mode policy, String verb) {
        if (instance instanceof Class< ? > type) {
            @SuppressWarnings("unchecked")
            T handle = (T) strict(type, policy, verb);
            return handle;
        }
        if (instance == null) {
            throw new StuntException("Cannot " + verb + " null");
        }
        Scope scope = Scope.require();
        Declaration declaration = scope.declarationForHandle(instance);
        if (declaration == null) {
            declaration = scope.declareInstance(instance, policy, Sites.caller());
        }
        else if (declaration.mode() != policy && verb.equals("audit")) {
            throw new StuntException("audit(" + Types.identity(instance) + "): the object is already declared as "
                + declaration.mode().annotation() + " (" + Sites.firstLine(declaration.declaredAt())
                + "); an audit runs the real code. Use verify(...) to make a stub strict");
        }
        scope.makeStrict(declaration);
        return instance;
    }

    /** Declares one object as {@code @StubPartially}; exempts it from a strict class-level declaration. Returns the object. */
    public static < T > T stubPartially(T instance) {
        return declareInstance(instance, Mode.PARTIAL);
    }

    @SuppressWarnings("unchecked")
    private static < T > T declareInstance(T instance, Mode mode) {
        if (instance == null) {
            throw new StuntException("Cannot declare null as " + mode.annotation());
        }
        if (instance instanceof Class< ? > type) {
            return (T) declare(type, mode); // a Class held in an Object-typed variable: the class form was meant
        }
        Scope.require().declareInstance(instance, mode, Sites.caller());
        return instance;
    }

    private static < T > T declare(Class< T > type, Mode mode) {
        Scope scope = Scope.require();
        Declaration declaration = scope.declare(type, mode, Sites.caller());
        return type.cast(declaration.handle());
    }

    // ---------------------------------------------------------------- when

    /** Maps the call made in the closure: {@code when(() -> service.find(arg.any())).thenReturn(x)}. */
    public static Stubbing when(Call call) {
        return map(Chain.Kind.WHEN, call, null);
    }

    /** Maps the call made on the handle for every instance: {@code when(Greeter.class, g -> g.greet(arg.any()))}. */
    public static < T > Stubbing when(Class< T > type, CallOn< T > call) {
        return map(Chain.Kind.WHEN, call, type);
    }

    /** Maps a method by name, for private methods: {@code when(Greeter.class, "secret", arg.anyString())}. */
    /**
     * Maps a method of every instance of its class, given as an unbound method reference:
     * {@code when(Greeter::getGreeting)}. Only for method references to instance methods with a unique name; an
     * overloaded name or a lambda needs the class: {@code when(Greeter.class, g -> g.pick("x"))}.
     */
    public static < T > Stubbing when(CallOn< T > call) {
        return mapUnbound(Chain.Kind.WHEN, call);
    }

    public static Stubbing when(Class< ? > type, String methodName, Object... args) {
        return mapNamed(Chain.Kind.WHEN, type, methodName, args);
    }

    // ---------------------------------------------------------------- verify

    /** Maps the call and asserts it happens: {@code verify(() -> dao.remove(arg.any())).times(2)}. */
    public static Stubbing verify(Call call) {
        return map(Chain.Kind.VERIFY, call, null);
    }

    public static < T > Stubbing verify(Class< T > type, CallOn< T > call) {
        return map(Chain.Kind.VERIFY, call, type);
    }

    /** {@code verify(Greeter::getGreeting)}: see {@link #when(CallOn)}. */
    public static < T > Stubbing verify(CallOn< T > call) {
        return mapUnbound(Chain.Kind.VERIFY, call);
    }

    public static Stubbing verify(Class< ? > type, String methodName, Object... args) {
        return mapNamed(Chain.Kind.VERIFY, type, methodName, args);
    }

    // ---------------------------------------------------------------- method groups

    /**
     * Maps a group of methods of one object at once: {@code when(m2, mtd.getters()).anyTimes()}. One chain
     * covers every method the matcher selects, with any arguments; counts apply to the matching calls in total.
     * A chain for a single signature always wins over a group chain, whatever the order they were declared in.
     * Statics are never part of an object's group. Terminals that need one return type or parameter list
     * ({@code thenReturn}, {@code thenReturns}, typed {@code thenDo}) are not available on a group.
     */
    public static Stubbing when(Object instance, MethodMatcher methods) {
        return mapGroup(Chain.Kind.WHEN, instance, methods);
    }

    /** Maps a group of methods for every instance of the class, statics included: {@code when(Entity.class, mtd.setters())}. */
    public static Stubbing when(Class< ? > type, MethodMatcher methods) {
        return mapGroup(Chain.Kind.WHEN, type, methods);
    }

    /** {@code verify(m2, mtd.setters()).times(2)}: two setter calls on m2 in total; see {@link #when(Object, MethodMatcher)}. */
    public static Stubbing verify(Object instance, MethodMatcher methods) {
        return mapGroup(Chain.Kind.VERIFY, instance, methods);
    }

    public static Stubbing verify(Class< ? > type, MethodMatcher methods) {
        return mapGroup(Chain.Kind.VERIFY, type, methods);
    }

    private static Stubbing mapGroup(Chain.Kind kind, Object target, MethodMatcher methods) {
        Scope scope = Scope.require();
        String site = Sites.caller();
        String what = kindName(kind) + "(" + (target instanceof Class< ? > c ? c.getSimpleName() + ".class"
            : Types.identity(target)) + ", mtd." + methods.describe() + ")";
        Declaration declaration;
        Object receiver;
        if (target instanceof Class< ? > type) {
            declaration = scope.declarationRelatedTo(type);
            if (declaration == null) {
                throw new UndeclaredClassException(what + ": " + type.getName() + " is not declared");
            }
            receiver = null;
        }
        else {
            declaration = scope.declarationForHandle(target);
            if (declaration != null && !declaration.instance()) {
                receiver = null; // a handle stands for the type
            }
            else {
                if (declaration == null) {
                    declaration = scope.declarationFor(target);
                }
                if (declaration == null) {
                    throw new UndeclaredClassException(what + ": " + target.getClass().getName()
                        + " is not declared. Declare the class, or the object with verify(obj) / stub(obj)");
                }
                receiver = target;
            }
        }
        int matched = 0;
        for (Method method : Descriptors.allMethods(declaration.implementation())) {
            if (receiver != null && java.lang.reflect.Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            if (methods.matches(method)) {
                matched++;
            }
        }
        if (matched == 0) {
            throw new StuntException(what + ": no method of " + declaration.implementation().getSimpleName()
                + " matches");
        }
        return new Stubbing(scope.newGroupChain(kind, declaration, methods, receiver, site));
    }


    // ---------------------------------------------------------------- configuration and diagnostics

    /** The state of the current thread's scope: declarations, chains with progress, calls so far. */
    public static String dump() {
        return Dump.of(Scope.current());
    }

    /** {@link #dump()} restricted to one class. */
    public static String dump(Class< ? > type) {
        return Dump.of(Scope.current(), type);
    }

    /** Prints {@link #dump()} to standard output; callable from production code or a debugger. */
    public static void printMocks() {
        System.out.println(dump());
    }

    // ---------------------------------------------------------------- mapping

    /** The class-less form for unbound method references: the class comes from the reference itself. */
    private static < T > Stubbing mapUnbound(Chain.Kind kind, CallOn< T > call) {
        LambdaInspector.Target target = LambdaInspector.targetOf(call);
        String name = kindName(kind);
        if (!target.methodReference()) {
            throw new StuntException(name + "(it -> ...): a lambda needs the class to type its parameter; write "
                + name + "(Foo.class, it -> it.method(...))");
        }
        if (target.isStatic()) {
            throw new StuntException(name + "(" + target.methodName() + "): a static method with a parameter needs a"
                + " lambda with matchers: " + name + "(() -> Foo." + target.methodName() + "(arg.any()))");
        }
        if (target.boundReceiver() != null) {
            throw new StuntException(name + "(x::" + target.methodName() + "): a bound method reference with a"
                + " parameter needs a lambda with matchers: " + name + "(() -> x." + target.methodName() + "(arg.any()))");
        }
        return map(kind, call, null); // the class comes from the reference; ambiguity between declared subclasses is checked there
    }

    private static < T > Stubbing map(Chain.Kind kind, Serializable closure, Class< T > declaredType) {
        Scope scope = Scope.require();
        String site = Sites.caller();
        List< ArgMatcher > stale = MatcherStack.drain();
        LambdaInspector.Target target = LambdaInspector.targetOf(closure);
        if (!stale.isEmpty()) {
            if (target.methodReference()) {
                throw new StuntException(kindName(kind) + ": matchers cannot be combined with a method reference;"
                    + " write a lambda: " + kindName(kind) + "(() -> x." + target.methodName() + "(arg.any()))");
            }
            throw new StuntException(kindName(kind) + ": " + stale.size() + " matcher(s) " + stale
                + " were registered before the closure ran; matchers belong inside the closure");
        }
        Class< ? > owner = target.owner(closure.getClass().getClassLoader());
        Declaration declaration;
        List< Declaration > candidates;
        if (declaredType != null) {
            declaration = scope.declarationRelatedTo(declaredType);
            if (declaration == null) {
                throw undeclared(scope, declaredType, owner, target.methodName());
            }
            if (!owner.isAssignableFrom(declaration.implementation()) && !owner.isAssignableFrom(declaredType)
                && !declaredType.isAssignableFrom(owner)) {
                throw new StuntException(kindName(kind) + "(" + declaredType.getSimpleName() + ".class, ...): the"
                    + " closure calls " + owner.getSimpleName() + "." + target.methodName() + " instead of a method"
                    + " of " + declaredType.getSimpleName());
            }
            candidates = List.of(declaration);
        }
        else if (target.boundReceiver() != null) {
            // x::m — the receiver decides, not the class that declares m (it may be a shared superclass)
            Object bound = target.boundReceiver();
            declaration = scope.declarationForHandle(bound);
            if (declaration == null) {
                declaration = scope.declarationFor(bound);
            }
            if (declaration == null) {
                throw undeclared(scope, bound.getClass(), owner, target.methodName());
            }
            candidates = List.of(declaration);
        }
        else if (target.isStatic()) {
            declaration = scope.declarationForStaticIn(owner);
            if (declaration == null) {
                declaration = scope.declarationRelatedTo(owner);
            }
            if (declaration == null) {
                throw undeclared(scope, owner, owner, target.methodName());
            }
            candidates = List.of(declaration);
        }
        else {
            // a lambda or an unbound reference: the class named in the closure; the actual receiver is only known
            // once the closure ran, so every related declaration is a candidate for the capture
            candidates = scope.declarationsRelatedTo(owner);
            if (candidates.isEmpty()) {
                throw undeclared(scope, owner, owner, target.methodName());
            }
            declaration = candidates.get(0);
            if (target.methodReference() && candidates.size() > 1 && !candidates.get(1).moreSpecificThan(declaration)
                && !declaration.moreSpecificThan(candidates.get(1))) {
                throw new StuntException(kindName(kind) + "(" + owner.getSimpleName() + "::" + target.methodName()
                    + "): ambiguous, " + describe(candidates) + " are declared; name one: " + kindName(kind) + "("
                    + candidates.get(0).declaredType().getSimpleName() + ".class, " + owner.getSimpleName() + "::"
                    + target.methodName() + ")");
            }
        }
        Method method;
        Object receiver;
        List< ArgMatcher > matchers;
        if (target.methodReference()) {
            // a method reference: resolve without executing; every parameter matches any value
            method = Descriptors.find(declaration.implementation(), target.methodName(), target.descriptor());
            if (method == null) {
                throw new StuntException(kindName(kind) + ": " + owner.getSimpleName() + "::" + target.methodName()
                    + " is not a method of " + declaration.implementation().getSimpleName());
            }
            receiver = target.isStatic() ? null : target.boundReceiver() != null ? target.boundReceiver()
                : declaration.handle();
            matchers = new ArrayList<>();
            for (Class< ? > p : method.getParameterTypes()) {
                matchers.add(ArgMatcher.any(p));
            }
        }
        else {
            Scope.Capture capture = new Scope.Capture(candidates, target.methodName(), target.descriptor());
            scope.beginCapture(capture);
            try {
                if (closure instanceof Call c) {
                    c.call();
                }
                else {
                    @SuppressWarnings("unchecked")
                    CallOn< Object > on = (CallOn< Object >) closure;
                    on.call(declaration.handle());
                }
            }
            catch (StuntException e) {
                throw e;
            }
            catch (Throwable t) {
                throw new StuntException(kindName(kind) + ": the closure threw " + t + " before the call "
                    + owner.getSimpleName() + "." + target.methodName() + " was reached", t);
            }
            finally {
                scope.endCapture();
                MatcherStack.clear();
            }
            if (!capture.done) {
                throw new StuntException(kindName(kind) + ": the call " + owner.getSimpleName() + "."
                    + target.methodName() + " in the closure was not intercepted. The receiver is not covered by"
                    + " a declaration (" + describe(candidates) + "): declare its class, or the object itself with "
                    + kindName(kind) + "(obj) / stub(obj) / stubPartially(obj); or the method is inherited from"
                    + " an undeclared JDK class");
            }
            declaration = capture.declaration;
            method = Descriptors.find(declaration.implementation(), target.methodName(), target.descriptor());
            if (method == null && capture.receiver != null) {
                method = Descriptors.find(capture.receiver.getClass(), target.methodName(), target.descriptor());
            }
            if (method == null) {
                throw new StuntException(kindName(kind) + ": cannot resolve " + owner.getSimpleName() + "."
                    + target.methodName() + target.descriptor());
            }
            receiver = capture.receiver;
            matchers = matchersFor(kind, method, capture.args, capture.matchers);
        }
        Object receiverFilter = receiver == null || scope.declarationForHandle(receiver) != null ? null : receiver;
        if (receiverFilter != null && !declaration.implementation().isInstance(receiverFilter)) {
            throw new StuntException(kindName(kind) + ": the receiver " + Types.identity(receiverFilter)
                + " is not an instance of the declared class " + declaration.implementation().getSimpleName());
        }
        if (Modifier.isStatic(method.getModifiers())) {
            receiverFilter = null;
        }
        return new Stubbing(scope.newChain(kind, declaration, method, receiverFilter, matchers, site));
    }

    private static Stubbing mapNamed(Chain.Kind kind, Class< ? > type, String methodName, Object[] args) {
        Scope scope = Scope.require();
        String site = Sites.caller();
        List< ArgMatcher > registered = MatcherStack.drain();
        Declaration declaration = scope.declarationRelatedTo(type);
        if (declaration == null) {
            throw undeclared(scope, type, type, methodName);
        }
        // a single generic null argument such as arg.any() arrives as a null array: recover it from the matchers
        Object[] values = args == null ? new Object[registered.size()] : args;
        List< Method > candidates = new ArrayList<>();
        for (Method m : Descriptors.findAll(declaration.implementation(), methodName)) {
            if (m.getParameterCount() == values.length) {
                candidates.add(m);
            }
        }
        if (candidates.isEmpty()) {
            throw new StuntException(kindName(kind) + "(" + type.getSimpleName() + ".class, \"" + methodName
                + "\", ...): no method " + methodName + " with " + values.length + " parameter(s) in "
                + declaration.implementation().getSimpleName() + " or its superclasses");
        }
        List< Method > compatible = new ArrayList<>();
        for (Method m : candidates) {
            if (compatible(m, values, registered)) {
                compatible.add(m);
            }
        }
        if (compatible.size() != 1) {
            StringBuilder sb = new StringBuilder(kindName(kind) + "(" + type.getSimpleName() + ".class, \"" + methodName
                + "\", ...): ").append(compatible.isEmpty() ? "no overload accepts the given arguments"
                : "the arguments fit " + compatible.size() + " overloads; select one with arg.any(Type.class)");
            sb.append(". Candidates:");
            for (Method m : candidates) {
                sb.append("\n  ").append(Descriptors.pretty(m));
            }
            throw new StuntException(sb.toString());
        }
        Method method = compatible.get(0);
        List< ArgMatcher > matchers = matchersFor(kind, method, values, registered);
        return new Stubbing(scope.newChain(kind, declaration, method, null, matchers, site));
    }

    private static boolean compatible(Method method, Object[] values, List< ArgMatcher > registered) {
        Class< ? >[] params = method.getParameterTypes();
        boolean useMatchers = registered.size() == values.length && !registered.isEmpty();
        for (int i = 0; i < params.length; i++) {
            Class< ? > boxed = Types.box(params[i]);
            if (useMatchers) {
                Class< ? > matcherType = registered.get(i).type();
                if (matcherType != null && !boxed.isAssignableFrom(Types.box(matcherType))) {
                    return false;
                }
            }
            else if (values[i] == null) {
                if (params[i].isPrimitive()) {
                    return false;
                }
            }
            else if (!boxed.isInstance(values[i])) {
                return false;
            }
        }
        return true;
    }

    private static List< ArgMatcher > matchersFor(Chain.Kind kind, Method method, Object[] args,
                                                  List< ArgMatcher > registered) {
        if (registered.isEmpty()) {
            List< ArgMatcher > result = new ArrayList<>();
            for (Object arg : args) {
                result.add(ArgMatcher.eq(arg));
            }
            return result;
        }
        if (registered.size() != args.length) {
            throw new StuntException(kindName(kind) + "(" + Descriptors.pretty(method) + "): " + registered.size()
                + " matcher(s) for " + args.length + " argument(s). Within one call use matchers for every argument"
                + " or for none; wrap plain values in arg.eq(...)");
        }
        Class< ? >[] params = method.getParameterTypes();
        for (int i = 0; i < params.length; i++) {
            Class< ? > matcherType = registered.get(i).type();
            if (matcherType != null && !Types.box(params[i]).isAssignableFrom(Types.box(matcherType))
                && !Types.box(matcherType).isAssignableFrom(Types.box(params[i]))) {
                throw new StuntException(kindName(kind) + "(" + Descriptors.pretty(method) + "): matcher "
                    + registered.get(i).describe() + " at position " + (i + 1) + " cannot match a "
                    + params[i].getSimpleName());
            }
        }
        return registered;
    }

    private static UndeclaredClassException undeclared(Scope scope, Class< ? > type, Class< ? > owner,
                                                       String methodName) {
        String what = type == owner ? type.getName() : type.getName() + " (closure calls " + owner.getSimpleName() + "."
            + methodName + ")";
        return new UndeclaredClassException(what + " is not declared in " + scope.name() + ". Declare it with @Stub,"
            + " @Verify or @StubPartially on the test class, a field, a parameter or the test method, or call"
            + " stub(" + type.getSimpleName() + ".class) / verify(...) / stubPartially(...) before this line.\n"
            + Dump.of(scope));
    }

    private static String describe(List< Declaration > declarations) {
        StringBuilder sb = new StringBuilder();
        for (Declaration d : declarations) {
            sb.append(sb.length() == 0 ? "" : ", ").append(d.mode().annotation()).append(' ')
                .append(d.declaredType().getSimpleName());
        }
        return sb.toString();
    }

    private static String kindName(Chain.Kind kind) {
        return kind.verb();
    }
}
