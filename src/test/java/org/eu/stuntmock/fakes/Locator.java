package org.eu.stuntmock.fakes;

/** A locator like {@code DAO.get(...)}: production code obtains repositories here, never by injection. */
public final class Locator {

    private static final RepositoryImpl REPOSITORY = new RepositoryImpl();

    private Locator() {
    }

    @SuppressWarnings("unchecked")
    public static < T > T get(Class< T > type) {
        if (type == Repository.class || type == RepositoryImpl.class) {
            return (T) REPOSITORY;
        }
        throw new IllegalArgumentException("unknown " + type);
    }

    /** The resolver a base test class would register for this convention. */
    public static final class Resolver implements org.eu.stuntmock.TypeResolver {
        @Override
        public Object instanceOf(Class< ? > type) {
            return type == Repository.class || type == RepositoryImpl.class ? get(type) : null;
        }
    }
}
