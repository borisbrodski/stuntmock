package org.eu.stuntmock.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Finds the test-code frames that declared or mapped something, for messages. Internal.
 *
 * <p>A call site is rendered like a stack trace, {@code at pkg.MyTest.test(MyTest.java:42)}, so that IDE
 * consoles and JUnit views link it. Up to {@link #depth(int)} frames are shown, one per line, so that a mapping
 * made in a helper method also names the test that called the helper. Frames of Stunt itself, of the JDK and of
 * JUnit are skipped; classes registered with {@link #ignore(String...)} (delegating interfaces, test base
 * classes) are skipped too. The walk stops at the reflective call JUnit uses to invoke the test, so runner
 * frames never appear.
 */
public final class Sites {

    private static final String OWN_PACKAGE = "org.eu.stuntmock.";
    private static final StackWalker WALKER = StackWalker.getInstance();
    private static final Set< String > IGNORED = ConcurrentHashMap.newKeySet();
    private static volatile int depth = 2;

    private Sites() {
    }

    /** Class names or package prefixes whose frames never count as a call site. */
    public static void ignore(String... classOrPackagePrefixes) {
        for (String prefix : classOrPackagePrefixes) {
            IGNORED.add(prefix);
        }
    }

    /** How many frames a call site shows; at least one. */
    public static void depth(int frames) {
        depth = Math.max(1, frames);
    }

    /** The call site as one or more stack-trace lines; continuation lines are indented. */
    public static String caller() {
        List< String > lines = WALKER.walk(frames -> {
            List< String > result = new ArrayList<>();
            for (StackWalker.StackFrame f : (Iterable< StackWalker.StackFrame >) frames::iterator) {
                String className = f.getClassName();
                if (isReflectiveInvocation(className)) {
                    break; // beyond this point only the test runner
                }
                if (isFramework(className) || isIgnored(className)) {
                    continue;
                }
                result.add("at " + className + "." + f.getMethodName() + "(" + f.getFileName() + ":" + f.getLineNumber() + ")");
                if (result.size() >= depth) {
                    break;
                }
            }
            return result;
        });
        if (lines.isEmpty()) {
            return "at unknown location";
        }
        return String.join("\n        ", lines);
    }

    /** The first line of a (possibly multi-line) call site, for use inside a sentence. */
    public static String firstLine(String site) {
        int newline = site.indexOf('\n');
        return newline < 0 ? site : site.substring(0, newline);
    }

    private static boolean isReflectiveInvocation(String className) {
        return className.startsWith("jdk.internal.reflect.") || className.equals("java.lang.reflect.Method");
    }

    private static boolean isIgnored(String className) {
        for (String prefix : IGNORED) {
            if (className.equals(prefix) || className.startsWith(prefix + ".") || className.startsWith(prefix + "$")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isFramework(String className) {
        if (className.startsWith(OWN_PACKAGE)) {
            // only the core is framework; extensions built on the public API (…stunt.time) and Stunt's own tests
            // (…stunt.unit, …stunt.fakes) are ordinary callers whose lines are worth reporting
            String rest = className.substring(OWN_PACKAGE.length());
            return rest.indexOf('.') < 0 || rest.startsWith("internal.") || rest.startsWith("dispatch.");
        }
        return className.startsWith("java.") || className.startsWith("jdk.") || className.startsWith("sun.")
            || className.startsWith("org.junit.");
    }
}
