package org.eu.de.stuntmock;

/**
 * Thrown at the call site when production code makes a call the test did not allow: an unmapped call on a
 * {@link Verify} class, or a call beyond the count of a {@code verify(...)} chain. An {@link Error}, so a
 * {@code catch (Exception)} in production code cannot swallow it; Stunt additionally records it and rethrows at
 * the end of the test.
 */
public class UnexpectedCallError extends AssertionError {

    private static final long serialVersionUID = 1L;

    public UnexpectedCallError(String message) {
        super(message);
    }
}
