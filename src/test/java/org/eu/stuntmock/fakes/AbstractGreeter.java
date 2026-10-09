package org.eu.stuntmock.fakes;

/** Superclass of {@link Greeter}: tests that inherited methods are intercepted. */
public abstract class AbstractGreeter {

    public String inherited() {
        return "inherited-real";
    }

    public static String staticInherited() {
        return "static-inherited-real";
    }
}
