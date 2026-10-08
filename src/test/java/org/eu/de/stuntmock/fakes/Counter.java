package org.eu.de.stuntmock.fakes;

/** Fake production class with state, for thenDo/callOriginal tests. */
public class Counter {

    private int value;

    public int next() {
        return ++value;
    }

    public int value() {
        return value;
    }

    public void add(int delta) {
        value += delta;
    }
}
