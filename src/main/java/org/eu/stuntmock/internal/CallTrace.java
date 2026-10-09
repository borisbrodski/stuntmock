package org.eu.stuntmock.internal;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** The bounded record of intercepted calls on declared classes in one scope, for diagnostics. Internal. */
class CallTrace {

    /** Entries kept per declaration; noisy infrastructure can therefore not crowd out the classes a test is about. */
    static final int LIMIT_PER_DECLARATION = 200;

    /** Swallows everything; used while an infrastructure chain answers. */
    static final CallTrace DISCARDING = new CallTrace() {
        @Override
        void add(Declaration declaration, Object self, String methodName, Object[] args, String outcome,
                 boolean chained, String group) {
        }
    };

    /**
     * {@code chained} tells whether a chain answered the call; {@code group} is then that chain's infrastructure
     * label, or {@code null} for a test's own chain.
     */
    record Entry(int number, Declaration declaration, String call, String outcome, boolean chained, String group) {
    }

    private final List< Entry > entries = new ArrayList<>();
    private final Map< Declaration, Integer > counts = new IdentityHashMap<>();
    private final Map< Declaration, Integer > droppedPerDeclaration = new IdentityHashMap<>();
    private int total;

    /** A call no chain answered. */
    void record(Declaration declaration, Object self, String methodName, Object[] args, String outcome) {
        add(declaration, self, methodName, args, outcome, false, null);
    }

    /** A call a chain answered. */
    void record(Declaration declaration, Object self, String methodName, Object[] args, String outcome,
                String group) {
        add(declaration, self, methodName, args, outcome, true, group);
    }

    void add(Declaration declaration, Object self, String methodName, Object[] args, String outcome,
             boolean chained, String group) {
        int count = counts.merge(declaration, 1, Integer::sum);
        total++;
        if (count > LIMIT_PER_DECLARATION) {
            droppedPerDeclaration.merge(declaration, 1, Integer::sum);
            return;
        }
        String receiver = self == null ? declaration.implementation().getSimpleName() : Types.identity(self);
        String call = receiver + "." + methodName + "(" + Types.formatArgs(args) + ")";
        entries.add(new Entry(total, declaration, call, outcome, chained, group));
    }

    List< Entry > entries() {
        return entries;
    }

    /** All calls on a declaration, including those beyond the trace limit. */
    int countFor(Declaration declaration) {
        return counts.getOrDefault(declaration, 0);
    }

    /** Calls on a declaration that were counted but not kept. */
    int droppedFor(Declaration declaration) {
        return droppedPerDeclaration.getOrDefault(declaration, 0);
    }
}
