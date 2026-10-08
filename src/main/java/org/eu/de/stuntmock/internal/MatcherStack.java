package org.eu.de.stuntmock.internal;

import java.util.ArrayList;
import java.util.List;

/** The thread-local list of matchers registered since the last {@code when}/{@code verify} began. Internal. */
public final class MatcherStack {

    private static final ThreadLocal< List< ArgMatcher > > STACK = ThreadLocal.withInitial(ArrayList::new);

    private MatcherStack() {
    }

    public static void push(ArgMatcher matcher) {
        STACK.get().add(matcher);
    }

    /** Returns and clears the registered matchers. */
    public static List< ArgMatcher > drain() {
        List< ArgMatcher > matchers = new ArrayList<>(STACK.get());
        STACK.get().clear();
        return matchers;
    }

    public static void clear() {
        STACK.get().clear();
    }
}
