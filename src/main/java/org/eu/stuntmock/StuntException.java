package org.eu.stuntmock;

/** A misuse of the Stunt API detected while a test declares or maps mocks, before any production code runs. */
public class StuntException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public StuntException(String message) {
        super(message);
    }

    public StuntException(String message, Throwable cause) {
        super(message, cause);
    }
}
