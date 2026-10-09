package org.eu.stuntmock.fakes;

/** Code under test that creates its own collaborators and calls statics: the JMockit-style situation. */
public class Service {

    public String welcome(String name) {
        Greeter greeter = new Greeter();
        return greeter.greet(name) + " [" + Tools.version() + "]";
    }

    public String load(long id) {
        return Locator.get(Repository.class).load(id);
    }

    public void removeAll(Entity... entities) {
        for (Entity e : entities) {
            e.remove();
        }
    }
}
