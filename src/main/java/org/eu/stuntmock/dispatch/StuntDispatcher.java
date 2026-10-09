package org.eu.stuntmock.dispatch;

/**
 * Entry point of every inlined advice. Injected into the bootstrap class loader at agent installation so that
 * advice inlined into any class, including JDK classes, can call it. The framework registers its handler via
 * reflection in {@link #handler}; nothing in the application code base may reference this class directly,
 * otherwise a second copy would be defined in the application class loader before the bootstrap copy exists.
 */
public final class StuntDispatcher {

    /** Set by the framework once installed. Volatile so worker threads see the assignment. */
    public static volatile StuntHandler handler;

    private StuntDispatcher() {
    }

    /** Fast path when no handler is installed; otherwise forwards to the handler. Never throws on its own. */
    public static StuntResult onCall(Object self, String declaringType, String methodName, String descriptor,
                                     Object[] args) {
        StuntHandler current = handler;
        if (current == null) {
            return null;
        }
        return current.onCall(self, declaringType, methodName, descriptor, args);
    }
}
