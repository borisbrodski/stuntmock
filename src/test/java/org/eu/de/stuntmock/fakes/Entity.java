package org.eu.de.stuntmock.fakes;

/** An entity whose {@code remove()} reaches the repository through the locator, like a typical JPA entity. */
public class Entity {

    private final long id;

    public Entity(long id) {
        this.id = id;
    }

    public long getId() {
        return id;
    }

    public void remove() {
        Locator.get(Repository.class).remove(this);
    }

    @Override
    public String toString() {
        return "Entity#" + id;
    }
}
