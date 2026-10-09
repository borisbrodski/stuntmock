package org.eu.stuntmock.internal;

import java.util.function.Supplier;

/**
 * Logging through the platform {@link System.Logger} under the name {@code org.eu.stuntmock}, so
 * that Log4j, Logback or JUL can pick it up (Log4j: {@code log4j-jpl}). DEBUG traces every dispatch decision.
 * Internal.
 */
public final class Log {

    public static final String NAME = "org.eu.stuntmock";
    private static final System.Logger LOG = System.getLogger(NAME);

    private Log() {
    }

    public static boolean isDebug() {
        return LOG.isLoggable(System.Logger.Level.DEBUG);
    }

    public static void debug(Supplier< String > message) {
        if (isDebug()) {
            LOG.log(System.Logger.Level.DEBUG, message.get());
        }
    }

    public static void warn(String message, Throwable t) {
        LOG.log(System.Logger.Level.WARNING, message, t);
    }
}
