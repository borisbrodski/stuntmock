package org.eu.stuntmock;

/**
 * Tells Stunt which object the production code obtains for a declared type, so that the type can be declared
 * by its interface: {@code @Stub CustomerDao dao} finds {@code CustomerDaoImpl} through the
 * resolver, instruments it, and uses the located object as handle.
 *
 * <pre>{@code
 * StuntSettings.addTypeResolver(type -> isDaoInterface(type) ? DAO.get(type) : null);
 * StuntSettings.addTypeResolver(type -> isEjbLocalInterface(type) ? TestContainer.lookup(type) : null);
 * }</pre>
 *
 * <p>Stunt asks every registered resolver, in registration order, for every declared type outside the JDK,
 * interfaces and classes alike (so that {@code @Stub CustomerDaoImpl} gets the same located object as
 * handle). A resolver returns {@code null} for types it does not know; the first non-null answer wins. Without
 * an answer, a concrete class is instrumented as it is with an uninitialized instance as handle, and an
 * interface becomes an instance mock. An exception from a resolver fails the test with the resolver's name,
 * the type and the cause.
 *
 * <p>Register resolvers once per JVM, before the first test class opens, or list the class in
 * {@code META-INF/services/org.eu.stuntmock.TypeResolver}.
 */
@FunctionalInterface
public interface TypeResolver {

    /**
     * @return the object the production code obtains for {@code type}, or {@code null} if this resolver does
     *         not know the type
     */
    Object instanceOf(Class< ? > type);

    /**
     * The class whose method bodies execute for the resolved instance: by default its runtime class. Override
     * only when the instance is a proxy (Hibernate, CGLIB, a JDK proxy) whose runtime class is not the class
     * that holds the code; return that class, so that fresh instances of it are intercepted as well.
     */
    default Class< ? > implementationOf(Class< ? > type, Object instance) {
        return instance.getClass();
    }
}
