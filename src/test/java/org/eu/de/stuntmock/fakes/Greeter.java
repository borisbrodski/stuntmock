package org.eu.de.stuntmock.fakes;

import java.util.ArrayList;
import java.util.List;

/**
 * Fake production class with instance state, private methods, overloads and primitives. Instances are typically
 * created by the code under test.
 */
public class Greeter extends AbstractGreeter implements Greeting {

    private String greeting = "Hello";
    private final List< String > log = new ArrayList<>();

    public Greeter() {
    }

    public Greeter(String greeting) {
        this.greeting = greeting;
    }

    public String greet(String name) {
        return privateGreeting(name);
    }

    private String privateGreeting(String name) {
        log.add(name);
        return greeting + ", " + name + "!";
    }

    public void setGreeting(String greeting) {
        this.greeting = greeting;
    }

    public String getGreeting() {
        return greeting;
    }

    public List< String > getLog() {
        return log;
    }

    // overloads
    public String pick(String s) {
        return "string:" + s;
    }

    public String pick(Integer i) {
        return "integer:" + i;
    }

    public String pick(String s, int i) {
        return "both:" + s + i;
    }

    public int add(int a, int b) {
        return a + b;
    }

    public long id() {
        return 42L;
    }

    public boolean isPolite() {
        return true;
    }

    public void fail() {
        throw new IllegalStateException("real failure");
    }

    /** Runs a callback the caller passes in; used to test exceptions thrown into production code. */
    public String guarded(Runnable action) {
        try {
            action.run();
            return "ok";
        }
        catch (Exception e) {
            return "swallowed " + e.getClass().getSimpleName();
        }
    }

    @Override
    public String toString() {
        return "Greeter(" + greeting + ")";
    }
}
