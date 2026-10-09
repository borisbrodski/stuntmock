package org.eu.stuntmock.fakes;

/** A second static class, reached by {@link Tools#fromHelper()}. */
public final class Helper {

    private Helper() {
    }

    public static String help() {
        return "real-help";
    }
}
