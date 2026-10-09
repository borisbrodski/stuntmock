package org.eu.stuntmock.internal;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Renders the state of a scope: declarations, chains with progress, and the call trace. Internal.
 *
 * <p>Declarations and chains made under {@code StuntSettings.infrastructure(label, ...)} (a frozen clock, reference
 * data, …) are collapsed to one summary line per label, so that a failure about a DAO is not buried under
 * five hundred {@code LocalDate.now()} calls. They are shown in full when the dump is focused on one of them
 * (the declaration a failure concerns) or filtered to their class. Runs of identical consecutive calls are
 * compacted to one line with a count.
 */
public final class Dump {

    private Dump() {
    }

    /** The whole scope; infrastructure collapsed. */
    public static String of(Scope scope) {
        return render(scope, null, null);
    }

    /** Only what relates to {@code filter}; infrastructure of that class in full. */
    public static String of(Scope scope, Class< ? > filter) {
        return render(scope, filter, null);
    }

    /** The whole scope, with the infrastructure group of {@code focus} (if any) shown in full. */
    public static String focused(Scope scope, Declaration focus) {
        return render(scope, null, focus);
    }

    private static String render(Scope scope, Class< ? > filter, Declaration focus) {
        Scope.enterFramework();
        try {
            return doRender(scope, filter, focus);
        }
        finally {
            Scope.exitFramework();
        }
    }

    private static String doRender(Scope scope, Class< ? > filter, Declaration focus) {
        if (scope == null) {
            return "Stunt: no scope open on thread " + Thread.currentThread().getName() + "\n";
        }
        String focusGroup = focus != null ? focus.group() : null;
        StringBuilder sb = new StringBuilder();
        sb.append("Stunt ").append(scope.isClassScope() ? "class scope " : "test scope ").append(scope.name())
            .append('\n');

        // ---- declarations
        sb.append("  Declarations:\n");
        boolean any = false;
        Map< String, List< Declaration > > collapsed = new LinkedHashMap<>();
        for (Declaration d : scope.declarations()) {
            if (filter != null && !d.relatesTo(filter)) {
                continue;
            }
            if (isCollapsed(d.group(), filter, focusGroup)) {
                collapsed.computeIfAbsent(d.group(), k -> new ArrayList<>()).add(d);
                continue;
            }
            any = true;
            sb.append("    ").append(d.describe()).append('\n');
        }
        for (Map.Entry< String, List< Declaration > > e : collapsed.entrySet()) {
            any = true;
            sb.append("    ").append(summary(scope, e.getKey(), e.getValue())).append('\n');
        }
        if (!any) {
            sb.append("    (none)\n");
        }

        // ---- chains
        sb.append("  Chains (newest first wins):\n");
        any = false;
        List< Chain > chains = scope.chains();
        for (int i = chains.size() - 1; i >= 0; i--) {
            Chain chain = chains.get(i);
            if (filter != null && !chain.declaration().relatesTo(filter)) {
                continue;
            }
            if (isCollapsed(chain.group(), filter, focusGroup)) {
                continue;
            }
            any = true;
            sb.append("    #").append(i + 1).append(' ').append(chain.describe()).append('\n');
        }
        if (!any) {
            sb.append("    (none)\n");
        }

        // ---- calls, compacted
        sb.append("  Calls on declared classes:\n");
        any = false;
        CallTrace.Entry run = null;
        int runLength = 0;
        for (CallTrace.Entry e : scope.trace().entries()) {
            if (filter != null && !e.declaration().relatesTo(filter)) {
                continue;
            }
            boolean ownChain = e.chained() && !isCollapsed(e.group(), filter, focusGroup);
            if (isCollapsed(e.declaration().group(), filter, focusGroup) && !ownChain) {
                continue;
            }
            any = true;
            if (run != null && run.call().equals(e.call()) && run.outcome().equals(e.outcome())) {
                runLength++;
                continue;
            }
            appendRun(sb, run, runLength);
            run = e;
            runLength = 1;
        }
        appendRun(sb, run, runLength);
        for (Declaration d : scope.declarations()) {
            int dropped = scope.trace().droppedFor(d);
            if (dropped > 0 && (filter == null || d.relatesTo(filter)) && !isCollapsed(d.group(), filter, focusGroup)) {
                any = true;
                sb.append("    ... ").append(dropped).append(" more on ").append(d.declaredType().getSimpleName())
                    .append(" (trace limit)\n");
            }
        }
        if (!any) {
            sb.append("    (none)\n");
        }
        return sb.toString();
    }

    private static boolean isCollapsed(String group, Class< ? > filter, String focusGroup) {
        return group != null && filter == null && !group.equals(focusGroup);
    }

    private static void appendRun(StringBuilder sb, CallTrace.Entry run, int length) {
        if (run == null) {
            return;
        }
        sb.append("    ").append(run.number()).append(". ").append(run.call()).append(" -> ").append(run.outcome());
        if (length > 1) {
            sb.append("   (x").append(length).append(", calls ").append(run.number()).append('-')
                .append(run.number() + length - 1).append(')');
        }
        sb.append('\n');
    }

    /** One line for an infrastructure group: its classes, chain count and call count. */
    private static String summary(Scope scope, String group, List< Declaration > declarations) {
        Set< String > names = new LinkedHashSet<>();
        for (Declaration d : declarations) {
            names.add(d.mode().annotation() + (scope.isStrict(d) ? " @Verify " : " ") + d.declaredType().getSimpleName());
        }
        int chainCount = 0;
        for (Chain c : scope.chains()) {
            if (group.equals(c.group())) {
                chainCount++;
            }
        }
        int callCount = 0;
        for (Declaration d : declarations) {
            callCount += scope.trace().countFor(d);
        }
        return "[" + group + "] " + String.join(", ", names) + " - " + chainCount + " chain(s), " + callCount
            + " call(s); Stunt.dump(" + declarations.get(0).declaredType().getSimpleName() + ".class) for details";
    }
}
