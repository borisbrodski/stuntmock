package org.eu.stuntmock;

/** A {@code when}/{@code verify} targets a class that is not declared with {@code @Stub}, {@code @Verify} or {@code @StubPartially}. */
public class UndeclaredClassException extends StuntException {

    private static final long serialVersionUID = 1L;

    public UndeclaredClassException(String message) {
        super(message);
    }
}
