package org.eu.stuntmock.fakes;

/** Interface resolved by convention to {@link RepositoryImpl}, like a DAO interface in many projects. */
public interface Repository {

    String load(long id);

    void remove(Entity entity);
}
