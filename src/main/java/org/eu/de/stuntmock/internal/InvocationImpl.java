package org.eu.de.stuntmock.internal;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.eu.de.stuntmock.Invocation;

/** The {@link Invocation} handed to closures. Internal. */
public final class InvocationImpl implements Invocation {

    private final Scope scope;
    private final Object receiver;
    private final Method method;
    private final String descriptor;
    private final Object[] args;

    InvocationImpl(Scope scope, Object receiver, Method method, String descriptor, Object[] args) {
        this.scope = scope;
        this.receiver = receiver;
        this.method = method;
        this.descriptor = descriptor;
        this.args = args;
    }

    @Override
    public Object receiver() {
        return receiver;
    }

    @Override
    public Method method() {
        return method;
    }

    @Override
    public Object[] args() {
        return args.clone();
    }

    @Override
    @SuppressWarnings("unchecked")
    public < T > T arg(int index) {
        return (T) args[index];
    }

    @Override
    public Object callOriginal() throws Throwable {
        if (java.lang.reflect.Modifier.isAbstract(method.getModifiers())) {
            throw new org.eu.de.stuntmock.StuntException(
                Descriptors.pretty(method) + " is abstract; there is no original implementation to call");
        }
        method.setAccessible(true);
        scope.bypassNext(receiver, method.getName(), descriptor);
        try {
            return method.invoke(receiver, args);
        }
        catch (InvocationTargetException e) {
            throw e.getCause();
        }
        finally {
            scope.clearBypass();
        }
    }

    @Override
    public String toString() {
        return Descriptors.pretty(method) + " on " + (receiver == null ? "static" : Types.identity(receiver));
    }
}
