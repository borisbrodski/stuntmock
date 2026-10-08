package org.eu.de.stuntmock;

/** Thrown at the end of a test when a {@code verify(...)} chain received fewer calls than declared. */
public class MissingCallError extends AssertionError {

    private static final long serialVersionUID = 1L;

    public MissingCallError(String message) {
        super(message);
    }
}
