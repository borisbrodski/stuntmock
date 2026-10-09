package org.eu.stuntmock.fakes;

/** Interface of {@link Greeter} with a default method: tests that default methods are intercepted. */
public interface Greeting {

    String greet(String name);

    default String politely(String name) {
        return "Dear " + greet(name);
    }
}
