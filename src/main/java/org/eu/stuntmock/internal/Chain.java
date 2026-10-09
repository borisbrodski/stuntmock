package org.eu.stuntmock.internal;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.eu.stuntmock.StuntException;

/**
 * One {@code when(...)} or {@code verify(...)}: the mapped call, its matchers and its sequence of steps. A step
 * answers until its maximum is reached, then the next step takes over. Internal.
 */
public final class Chain {

    public enum Kind {
        WHEN, VERIFY;

        public String verb() {
            return name().toLowerCase();
        }
    }

    private final Kind kind;
    private final Declaration declaration;
    /** The mapped method, or {@code null} for a group chain. */
    private final Method method;
    private final String descriptor;
    /** The method group, or {@code null} for a single-method chain. */
    private final org.eu.stuntmock.MethodMatcher methodMatcher;
    private final java.util.Map< String, Method > resolved = new java.util.HashMap<>();
    /** Only calls on this exact receiver match; {@code null} means every instance (or a static method). */
    private final Object receiver;
    private final List< ArgMatcher > matchers;
    private final List< Step > steps = new ArrayList<>();
    private final String declaredAt;
    private final boolean classLevel;
    private final String group;
    private boolean lastWasCount;
    private boolean sealed;

    Chain(Kind kind, Declaration declaration, Method method, Object receiver, List< ArgMatcher > matchers,
          String declaredAt, boolean classLevel, String group) {
        this(kind, declaration, method, null, receiver, matchers, declaredAt, classLevel, group);
    }

    /** A group chain: every method the matcher selects, any arguments. */
    Chain(Kind kind, Declaration declaration, org.eu.stuntmock.MethodMatcher methodMatcher,
          Object receiver, String declaredAt, boolean classLevel, String group) {
        this(kind, declaration, null, methodMatcher, receiver, null, declaredAt, classLevel, group);
    }

    private Chain(Kind kind, Declaration declaration, Method method,
                  org.eu.stuntmock.MethodMatcher methodMatcher, Object receiver,
                  List< ArgMatcher > matchers, String declaredAt, boolean classLevel, String group) {
        this.group = group;
        this.kind = kind;
        this.declaration = declaration;
        this.method = method;
        this.methodMatcher = methodMatcher;
        this.descriptor = method == null ? null : Descriptors.of(method);
        this.receiver = receiver;
        this.matchers = matchers;
        this.declaredAt = declaredAt;
        this.classLevel = classLevel;
    }

    /** True for a chain that covers a group of methods ({@code when(obj, mtd.getters())}). */
    public boolean isGroup() {
        return methodMatcher != null;
    }

    // ---------------------------------------------------------------- building (called by Stubbing)

    public Kind kind() {
        return kind;
    }

    public Method method() {
        return method;
    }

    /** The infrastructure label this chain was created under, or {@code null} for a test's own chain. */
    public String group() {
        return group;
    }

    public Declaration declaration() {
        return declaration;
    }

    public String declaredAt() {
        return declaredAt;
    }

    public void addStep(Answer answer) {
        checkOpen();
        if (!steps.isEmpty()) {
            Step last = steps.get(steps.size() - 1);
            if (last.answer == Answer.DEFAULT && last.explicit && lastWasCount) {
                // the count came first, on a step that has no terminal yet: the terminal completes that step,
                // so anyTimes().thenCallOriginal() reads the same as thenCallOriginal().anyTimes()
                if (answer instanceof Answer.Sequence sequence && last.max != sequence.size() && last.max >= 0) {
                    throw new StuntException(describeShort() + ": thenReturns(...) with " + sequence.size()
                        + " values cannot be combined with the count given before it");
                }
                steps.set(steps.size() - 1, last.withAnswer(answer));
                lastWasCount = false;
                return;
            }
            if (last.explicit && last.max == Step.UNBOUNDED) {
                throw new StuntException(describeShort() + ": step " + (steps.size() + 1) + " can never be reached"
                    + " because step " + steps.size() + " has no upper bound (minTimes)");
            }
        }
        if (answer instanceof Answer.Sequence sequence) {
            int size = sequence.size();
            if (size < 0) {
                if (kind == Kind.VERIFY) {
                    throw new StuntException(describeShort() + ": thenReturns(...) in verify(...) needs a"
                        + " Collection so that the expected number of calls is known");
                }
                steps.add(new Step(answer, 0, Step.UNBOUNDED, true));
            }
            else {
                steps.add(new Step(answer, kind == Kind.VERIFY ? size : 0, size, true));
            }
        }
        else {
            steps.add(new Step(answer, -1, -1, false));
        }
        lastWasCount = false;
    }

    /** Applies a count modifier to the last step, creating a default-answer step if none exists yet. */
    public void count(int min, int max, String modifier) {
        checkOpen();
        if (lastWasCount) {
            throw new StuntException(describeShort() + ": " + modifier + " follows another count modifier; a count"
                + " belongs to the step before it");
        }
        if (kind != Kind.VERIFY && min > 0) {
            throw new StuntException(describeShort() + ": " + modifier + " asserts a minimum number of calls, but"
                + " when(...) does not assert. Use verify(...) instead");
        }
        if (steps.isEmpty()) {
            steps.add(new Step(Answer.DEFAULT, -1, -1, false));
        }
        Step last = steps.get(steps.size() - 1);
        if (last.answer instanceof Answer.Sequence sequence && max >= 0 && max != sequence.size()) {
            throw new StuntException(describeShort() + ": thenReturns(...) with " + sequence.size()
                + " values cannot be combined with " + modifier);
        }
        if (last.explicit && !(last.answer instanceof Answer.Sequence)) {
            throw new StuntException(describeShort() + ": " + modifier + " follows a count already given for step "
                + steps.size() + "; one count per step");
        }
        last.min = min;
        last.max = max;
        last.explicit = true;
        lastWasCount = true;
    }

    /** Fixes the defaults of unspecified counts; afterwards the chain is immutable. */
    void seal() {
        if (sealed) {
            return;
        }
        if (steps.isEmpty()) {
            steps.add(new Step(Answer.DEFAULT, -1, -1, false));
        }
        for (int i = 0; i < steps.size(); i++) {
            Step step = steps.get(i);
            boolean last = i == steps.size() - 1;
            if (!step.explicit) {
                if (kind == Kind.VERIFY) {
                    step.min = 1;
                    step.max = 1;
                }
                else {
                    step.min = 0;
                    step.max = last ? Step.UNBOUNDED : 1;
                }
            }
            if (!last && step.max == Step.UNBOUNDED) {
                throw new StuntException(describeShort() + ": step " + (i + 2) + " can never be reached because step "
                    + (i + 1) + " has no upper bound");
            }
        }
        sealed = true;
    }

    private void checkOpen() {
        if (sealed) {
            throw new StuntException(describeShort() + " cannot be changed after the test started using it");
        }
    }

    /** A fresh copy with reset counters, used to instantiate class-level chains for each test. */
    Chain instantiate() {
        seal();
        Chain copy = new Chain(kind, declaration, method, methodMatcher, receiver, matchers, declaredAt, classLevel, group);
        for (Step step : steps) {
            Step s = new Step(step.answer, step.min, step.max, true);
            copy.steps.add(s);
        }
        copy.sealed = true;
        return copy;
    }

    // ---------------------------------------------------------------- dispatching

    boolean matches(Declaration called, Object self, String name, String desc, Object[] args) {
        if (!declaration.appliesTo(called)) {
            return false;
        }
        if (receiver != null && receiver != self) {
            return false;
        }
        if (isGroup()) {
            Method candidate = methodFor(called, name, desc);
            return candidate != null && methodMatcher.matches(candidate);
        }
        if (!method.getName().equals(name) || !descriptor.equals(desc)) {
            return false;
        }
        if (matchers.size() != args.length) {
            return false;
        }
        for (int i = 0; i < args.length; i++) {
            if (!matchers.get(i).matches(args[i])) {
                return false;
            }
        }
        return true;
    }

    /** The step that answers the next call, or {@code null} if every step is exhausted. */
    Step current() {
        seal();
        for (Step step : steps) {
            if (!step.exhausted()) {
                return step;
            }
        }
        return null;
    }

    boolean exhausted() {
        return current() == null;
    }

    /** The method a call resolves to for this chain: the mapped one, or the called one for a group. */
    Method methodFor(Declaration called, String name, String desc) {
        if (!isGroup()) {
            return method;
        }
        String key = name + desc;
        Method found = resolved.get(key);
        if (found == null && !resolved.containsKey(key)) {
            found = Descriptors.find(called.implementation(), name, desc);
            resolved.put(key, found);
        }
        return found;
    }

    void matched(Object[] args) {
        if (matchers == null) {
            return;
        }
        for (int i = 0; i < args.length; i++) {
            matchers.get(i).onMatched(args[i]);
        }
    }

    /** The steps whose minimum was not reached, empty if the chain is satisfied. */
    List< Step > unsatisfied() {
        seal();
        List< Step > result = new ArrayList<>();
        for (Step step : steps) {
            if (step.count < step.min) {
                result.add(step);
            }
        }
        return result;
    }

    List< Step > steps() {
        return steps;
    }

    boolean classLevel() {
        return classLevel;
    }

    // ---------------------------------------------------------------- describing

    public String describeShort() {
        return kind.verb() + "(" + describeCall() + ")";
    }

    public String describeCall() {
        StringBuilder sb = new StringBuilder();
        if (isGroup()) {
            sb.append(receiver != null ? Types.identity(receiver) : declaration.declaredType().getSimpleName());
            return sb.append(".<").append(methodMatcher.describe()).append('>').toString();
        }
        if (receiver != null) {
            sb.append(Types.identity(receiver)).append('.');
        }
        else if (java.lang.reflect.Modifier.isStatic(method.getModifiers())) {
            sb.append(method.getDeclaringClass().getSimpleName()).append('.');
        }
        else {
            sb.append(declaration.declaredType().getSimpleName()).append('.');
        }
        sb.append(method.getName()).append('(');
        for (int i = 0; i < matchers.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(matchers.get(i).describe());
        }
        return sb.append(')').toString();
    }

    public String describe() {
        seal();
        StringBuilder sb = new StringBuilder(describeShort());
        for (int i = 0; i < steps.size(); i++) {
            Step step = steps.get(i);
            sb.append("\n      step ").append(i + 1).append(": ").append(step.describe());
        }
        sb.append("\n      declared ").append(declaredAt);
        if (classLevel) {
            sb.append(" (class level)");
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return describeShort();
    }

    // ---------------------------------------------------------------- steps

    static final class Step {

        static final int UNBOUNDED = Integer.MAX_VALUE;

        final Answer answer;
        int min;
        int max;
        boolean explicit;
        int count;
        private Iterator< ? > iterator;

        Step(Answer answer, int min, int max, boolean explicit) {
            this.answer = answer;
            this.min = min;
            this.max = max;
            this.explicit = explicit;
        }

        /** The same counts with a terminal filled in. */
        Step withAnswer(Answer terminal) {
            return new Step(terminal, min, max, explicit);
        }

        boolean exhausted() {
            if (answer instanceof Answer.Sequence sequence) {
                if (iterator == null) {
                    iterator = sequence.values().iterator();
                }
                return !iterator.hasNext() || count >= max;
            }
            return count >= max;
        }

        Object next() {
            return iterator.next();
        }

        String describe() {
            String progress;
            if (max == 0) {
                progress = "never()";
            }
            else if (max == UNBOUNDED) {
                progress = count + "/*";
            }
            else if (min == max) {
                progress = count + "/" + max;
            }
            else {
                progress = count + " of " + min + ".." + max;
            }
            return answer.describe() + " [" + progress + "]";
        }
    }

    /** Length of a {@code thenReturns} sequence, if it can be known in advance. */
    static int sizeOf(Iterable< ? > values) {
        return values instanceof Collection< ? > c ? c.size() : -1;
    }
}
