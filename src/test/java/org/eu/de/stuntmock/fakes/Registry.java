package org.eu.de.stuntmock.fakes;

/** A static registry the suite's defaults stub for every test class; real answer: inactive. */
public final class Registry {

    private Registry() {
    }

    public static boolean active() {
        return false;
    }

    public static String name() {
        return "real";
    }
}
