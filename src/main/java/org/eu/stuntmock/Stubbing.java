package org.eu.stuntmock;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Objects;

import org.eu.stuntmock.internal.Answer;
import org.eu.stuntmock.internal.Chain;
import org.eu.stuntmock.internal.Declaration;
import org.eu.stuntmock.internal.Descriptors;
import org.eu.stuntmock.internal.LambdaInspector;
import org.eu.stuntmock.internal.Types;

/**
 * The chain returned by {@code when(...)} and {@code verify(...)}. Every terminal ({@code thenReturn},
 * {@code thenThrow}, {@code thenDo}, {@code thenDoNothing}, {@code thenCallOriginal}, {@code thenReturns}) appends
 * one step; a count modifier ({@code times}, {@code minTimes}, {@code maxTimes}, {@code never}) applies to the
 * step before it. A step answers until its maximum is reached, then the next step takes over.
 *
 * <pre>{@code
 * verify(() -> dao.findById(1L))
 *     .thenReturn("test").times(2)          // calls 1-2
 *     .thenThrow(new NotFoundException())   // call 3
 *     .thenReturn("test2").times(3);        // calls 4-6; a 7th call fails
 * }</pre>
 *
 * Defaults of a step without count: exactly once, except the last step, which in {@code when} answers forever
 * and in {@code verify} exactly once. After the last step, {@code when} falls back to the declared mode and
 * {@code verify} fails the call.
 */
@SuppressWarnings({"overloads", "cast"}) // the Fn/Proc overload pairs are intended: javac resolves them by the closure body
public final class Stubbing {

    private final Chain chain;

    Stubbing(Chain chain) {
        this.chain = chain;
    }

    // ---------------------------------------------------------------- terminals

    /** Returns {@code value}; numeric values are widened to the method's return type. */
    public Stubbing thenReturn(Object value) {
        requireSingle("thenReturn");
        Object coerced = Types.coerce(value, method().getReturnType(), chain.describeShort() + ".thenReturn");
        chain.addStep(Answer.returning(coerced));
        return this;
    }

    /** One step per value: {@code thenReturn(a, b, c)} is {@code thenReturn(a).thenReturn(b).thenReturn(c)}. */
    public Stubbing thenReturn(Object first, Object... more) {
        thenReturn(first);
        for (Object value : more) {
            thenReturn(value);
        }
        return this;
    }

    /** One value per call from the iterable; in {@code verify} the iterable must be a Collection. */
    public Stubbing thenReturns(Iterable< ? > values) {
        Objects.requireNonNull(values, "values");
        requireSingle("thenReturns");
        chain.addStep(Answer.sequence(values, method().getReturnType()));
        return this;
    }

    public Stubbing thenThrow(Throwable throwable) {
        Objects.requireNonNull(throwable, "throwable");
        chain.addStep(Answer.throwing(throwable));
        return this;
    }

    /** Returns the default value of the return type; for {@code void} methods simply skips the body. */
    public Stubbing thenDoNothing() {
        chain.addStep(Answer.NOTHING);
        return this;
    }

    /** Runs the original method body; useful on a {@code @Stub} class to let one method through. */
    public Stubbing thenCallOriginal() {
        Declaration declaration = chain.declaration();
        if (chain.isGroup()) {
            chain.addStep(Answer.PROCEED_ANSWER);   // a proxy in the group has no original and answers the default value
            return this;
        }
        java.lang.reflect.Method declared = Descriptors.find(declaration.declaredType(), method().getName(),
            Descriptors.of(method()));
        java.lang.reflect.Method original = declared != null ? declared : method();
        if (declaration.proxy() || java.lang.reflect.Modifier.isAbstract(original.getModifiers())) {
            throw new StuntException(chain.describeShort() + ".thenCallOriginal(): " + Descriptors.pretty(original)
                + " is abstract, there is no original to call");
        }
        chain.addStep(Answer.PROCEED_ANSWER);
        return this;
    }

    // ---------------------------------------------------------------- thenDo

    /** A closure without parameters: {@code thenDo(() -> counter++)} or {@code thenDo(() -> { log.clear(); })}. */
    public < R > Stubbing thenDo(Fn0< R > closure) {
        return doing(closure, inv -> closure.call(), null);
    }

    public Stubbing thenDo(Proc0 closure) {
        return doing(closure, inv -> {
            closure.call();
            return null;
        }, null);
    }

    /**
     * A closure with the method's parameters, explicitly typed with their boxed types:
     * {@code thenDo((String name, Integer age) -> ...)}, or {@code thenDo((Invocation inv) -> ...)} for full access.
     */
    @SuppressWarnings("unchecked")
    public < A, R > Stubbing thenDo(Fn1< A, R > closure) {
        return doing(closure, inv -> closure.call(inv.<A>arg(0)), inv -> closure.call((A) inv));
    }

    @SuppressWarnings("unchecked")
    public < A > Stubbing thenDo(Proc1< A > closure) {
        return doing(closure, inv -> {
            closure.call(inv.<A>arg(0));
            return null;
        }, inv -> {
            closure.call((A) inv);
            return null;
        });
    }

    public < A, B, R > Stubbing thenDo(Fn2< A, B, R > closure) {
        return doing(closure, inv -> closure.call(inv.<A>arg(0), inv.<B>arg(1)), null);
    }

    public < A, B > Stubbing thenDo(Proc2< A, B > closure) {
        return doing(closure, inv -> {
            closure.call(inv.<A>arg(0), inv.<B>arg(1));
            return null;
        }, null);
    }

    public < A, B, C, R > Stubbing thenDo(Fn3< A, B, C, R > closure) {
        return doing(closure, inv -> closure.call(inv.<A>arg(0), inv.<B>arg(1), inv.<C>arg(2)), null);
    }

    public < A, B, C > Stubbing thenDo(Proc3< A, B, C > closure) {
        return doing(closure, inv -> {
            closure.call(inv.<A>arg(0), inv.<B>arg(1), inv.<C>arg(2));
            return null;
        }, null);
    }

    public < A, B, C, D, R > Stubbing thenDo(Fn4< A, B, C, D, R > closure) {
        return doing(closure, inv -> closure.call(inv.<A>arg(0), inv.<B>arg(1), inv.<C>arg(2), inv.<D>arg(3)),
            null);
    }

    public < A, B, C, D > Stubbing thenDo(Proc4< A, B, C, D > closure) {
        return doing(closure, inv -> {
            closure.call(inv.<A>arg(0), inv.<B>arg(1), inv.<C>arg(2), inv.<D>arg(3));
            return null;
        }, null);
    }

    private Stubbing doing(Serializable closure, Answer.Doing.Body withArgs, Answer.Doing.Body withInvocation) {
        Class< ? >[] params = LambdaInspector.parameterTypes(closure);
        Method method = method();
        Answer.Doing.Body checked = withArgs;
        if (params.length == 1 && params[0] == Invocation.class) {
            checked = withInvocation;
        }
        else if (params.length > 0) {
            if (chain.isGroup()) {
                throw new StuntException(chain.describeShort() + ".thenDo: a group of methods has no common"
                    + " parameter list; use () or (Invocation inv), or map the single method");
            }
            Class< ? >[] expected = method.getParameterTypes();
            if (params.length != expected.length) {
                throw new StuntException(chain.describeShort() + ".thenDo: the closure takes " + params.length
                    + " parameter(s) but " + Descriptors.pretty(method) + " has " + expected.length
                    + ". Use (), (Invocation inv), or the method's parameters with their boxed types");
            }
            for (int i = 0; i < params.length; i++) {
                Class< ? > boxed = Types.box(expected[i]);
                if (!params[i].isAssignableFrom(boxed)) {
                    throw new StuntException(chain.describeShort() + ".thenDo: closure parameter " + (i + 1)
                        + " is " + params[i].getSimpleName() + " but " + Descriptors.pretty(method) + " takes "
                        + expected[i].getSimpleName() + (expected[i].isPrimitive() ? " (declare it as "
                        + boxed.getSimpleName() + ")" : ""));
                }
            }
        }
        chain.addStep(new Answer.Doing(checked, "thenDo(" + describeParams(params) + " -> ...)"));
        return this;
    }

    private static String describeParams(Class< ? >[] params) {
        return Arrays.stream(params).map(Class::getSimpleName).reduce((a, b) -> a + ", " + b).map(s -> "(" + s + ")")
            .orElse("()");
    }

    // ---------------------------------------------------------------- counts

    /** The step before answers exactly {@code n} calls; in {@code verify} it also asserts that many. */
    public Stubbing times(int n) {
        requireNonNegative(n, "times");
        chain.count(chain.kind() == Chain.Kind.VERIFY ? n : 0, n, "times(" + n + ")");
        return this;
    }

    /** {@code verify} only: the step before answers at least {@code n} calls and stays active. */
    public Stubbing minTimes(int n) {
        requireNonNegative(n, "minTimes");
        chain.count(n, Integer.MAX_VALUE, "minTimes(" + n + ")");
        return this;
    }

    /** The step before answers at most {@code n} calls. */
    public Stubbing maxTimes(int n) {
        requireNonNegative(n, "maxTimes");
        chain.count(0, n, "maxTimes(" + n + ")");
        return this;
    }

    /**
     * The step before answers any number of calls, including none, and asserts nothing: {@code minTimes(0)}
     * with no maximum. On a {@code when} chain this is already the default of the last step, so it only states
     * the intent; on a {@code verify} chain it removes the implicit {@code times(1)}. Typical use: the calls on
     * a {@code @Verify} object the test is not interested in:
     * <pre>{@code
     * verify(m2);
     * when(m2::getId).anyTimes();            // allowed, the real getter runs
     * when(m2, mtd.getters()).anyTimes();    // every getter
     * verify(() -> m2.setText(arg.any()));
     * }</pre>
     * Like {@code minTimes}, it must be the last step of the chain.
     */
    public Stubbing anyTimes() {
        chain.count(0, Integer.MAX_VALUE, "anyTimes()");
        return this;
    }

    /** {@code verify} only: the call must not happen; the first call fails the test. */
    public Stubbing never() {
        if (chain.kind() != Chain.Kind.VERIFY) {
            throw new StuntException(chain.describeShort() + ".never(): " + chain.kind().verb() + "(...) does not assert; use"
                + " verify(...).never()");
        }
        chain.count(0, 0, "never()");
        return this;
    }

    private static void requireNonNegative(int n, String modifier) {
        if (n < 0) {
            throw new StuntException(modifier + "(" + n + "): the count must not be negative");
        }
    }

    private Method method() {
        return chain.method();
    }

    private void requireSingle(String terminal) {
        if (chain.isGroup()) {
            throw new StuntException(chain.describeShort() + "." + terminal + ": a group of methods has no common"
                + " return type; map the single method, or use thenDoNothing/thenCallOriginal/thenThrow/thenDo");
        }
    }

    @Override
    public String toString() {
        return chain.describe();
    }

    // ---------------------------------------------------------------- closure shapes

    public interface Fn0< R > extends Serializable {
        R call() throws Throwable;
    }

    public interface Proc0 extends Serializable {
        void call() throws Throwable;
    }

    public interface Fn1< A, R > extends Serializable {
        R call(A a) throws Throwable;
    }

    public interface Proc1< A > extends Serializable {
        void call(A a) throws Throwable;
    }

    public interface Fn2< A, B, R > extends Serializable {
        R call(A a, B b) throws Throwable;
    }

    public interface Proc2< A, B > extends Serializable {
        void call(A a, B b) throws Throwable;
    }

    public interface Fn3< A, B, C, R > extends Serializable {
        R call(A a, B b, C c) throws Throwable;
    }

    public interface Proc3< A, B, C > extends Serializable {
        void call(A a, B b, C c) throws Throwable;
    }

    public interface Fn4< A, B, C, D, R > extends Serializable {
        R call(A a, B b, C c, D d) throws Throwable;
    }

    public interface Proc4< A, B, C, D > extends Serializable {
        void call(A a, B b, C c, D d) throws Throwable;
    }
}
