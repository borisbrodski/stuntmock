package org.eu.stuntmock.fakes;

import java.util.ArrayList;
import java.util.List;

/** The implementation the locator hands out. */
public class RepositoryImpl implements Repository {

    public final List< Entity > removed = new ArrayList<>();

    @Override
    public String load(long id) {
        return "real-" + id;
    }

    @Override
    public void remove(Entity entity) {
        removed.add(entity);
    }
}
