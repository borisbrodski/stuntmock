package org.eu.de.stuntmock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Records the arguments passed at a mapped position: {@code when(() -> dao.save(captor.capture()))}. After the
 * production code ran, {@link #getValue()} is the last recorded argument and {@link #getValues()} all of them.
 * Created with {@code arg.captor(Foo.class)}.
 *
 * @param <T> the argument type
 */
public final class Captor< T > {

    private final Class< T > type;
    private final List< T > values = new ArrayList<>();

    Captor(Class< T > type) {
        this.type = type;
    }

    /** Use in place of the argument; matches any value of the captor's type and records it. */
    public T capture() {
        Args.registerCaptor(this);
        return null;
    }

    /** The argument recorded by the most recent matching call. */
    public T getValue() {
        if (values.isEmpty()) {
            throw new StuntException("No argument captured yet for " + type.getSimpleName());
        }
        return values.get(values.size() - 1);
    }

    /** All recorded arguments, oldest first. */
    public List< T > getValues() {
        return Collections.unmodifiableList(values);
    }

    /** Internal: the captured type. */
    public Class< T > type() {
        return type;
    }

    /** Internal: records a value at dispatch time. */
    public void record(Object value) {
        values.add(type.cast(value));
    }

    @Override
    public String toString() {
        return "arg.captor(" + type.getSimpleName() + ")";
    }
}
