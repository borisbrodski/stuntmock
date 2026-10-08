package org.eu.de.stuntmock.internal;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

import org.objenesis.Objenesis;
import org.objenesis.ObjenesisStd;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.InvocationHandlerAdapter;
import net.bytebuddy.matcher.ElementMatchers;

/** Creation of handles: uninitialized instances of concrete classes, proxies for unresolvable interfaces. Internal. */
final class Handles {

    private static final Objenesis OBJENESIS = new ObjenesisStd(true);

    private Handles() {
    }

    /** An instance created without running any constructor; only meant for {@code when}/{@code verify} closures. */
    static Object uninitialized(Class< ? > type) {
        return OBJENESIS.newInstance(type);
    }

    /** An instance-scoped proxy for an interface or abstract class; every call is dispatched like an intercepted one. */
    static Object proxy(Class< ? > type) {
        InvocationHandler handler = (proxy, method, args) -> Dispatch.onProxyCall(proxy, method,
            args == null ? new Object[0] : args);
        ClassLoader loader = type.getClassLoader() == null ? Handles.class.getClassLoader() : type.getClassLoader();
        Class< ? > proxyClass = new ByteBuddy()
            .subclass(type)
            .name("org.eu.de.stuntmock.proxies." + type.getSimpleName() + "$StuntProxy")
            .method(ElementMatchers.any())
            .intercept(InvocationHandlerAdapter.of(handler))
            .make()
            .load(loader, ClassLoadingStrategy.Default.WRAPPER)
            .getLoaded();
        return OBJENESIS.newInstance(proxyClass);
    }

    static boolean isProxy(Object o) {
        return o != null && o.getClass().getName().endsWith("$StuntProxy");
    }

    static String pretty(Method method) {
        return Descriptors.pretty(method);
    }
}
