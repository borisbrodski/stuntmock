package org.eu.stuntmock.dispatch;

/**
 * The outcome the dispatcher decides for an intercepted call. A non-null result tells the inlined advice to
 * skip the original method body and either return {@link #value} or throw {@link #thrown}.
 *
 * <p>This class lives in the bootstrap class loader (see {@link StuntDispatcher}), so it must not reference
 * anything outside {@code java.*} and this package.
 */
public final class StuntResult {

    /** The value to return; ignored when {@link #thrown} is set or the method is {@code void}. */
    public final Object value;

    /** The throwable to throw instead of returning. */
    public final Throwable thrown;

    private StuntResult(Object value, Throwable thrown) {
        this.value = value;
        this.thrown = thrown;
    }

    public static StuntResult returning(Object value) {
        return new StuntResult(value, null);
    }

    public static StuntResult throwing(Throwable thrown) {
        return new StuntResult(null, thrown);
    }
}
