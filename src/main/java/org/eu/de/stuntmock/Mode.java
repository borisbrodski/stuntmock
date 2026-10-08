package org.eu.de.stuntmock;

/**
 * The answer policy of a declaration: what a call gets when nothing says otherwise. Strictness (whether an
 * unmapped call is allowed at all) is a separate modifier, {@code @Verify} / {@code verify(...)}, that composes
 * with either policy.
 */
public enum Mode {

    /** The default value of the return type ({@link Stub}). */
    STUB("@Stub"),

    /** The original method body ({@link StubPartially}). */
    PARTIAL("@StubPartially");

    private final String annotation;

    Mode(String annotation) {
        this.annotation = annotation;
    }

    /** The annotation that declares this policy, for messages. */
    public String annotation() {
        return annotation;
    }
}
