package org.eu.de.stuntmock;

import java.lang.reflect.Method;
import java.util.function.Predicate;

/**
 * Selects a group of methods for one {@code when}/{@code verify}: {@code when(m2, mtd.getters()).anyTimes()}.
 * The group is one chain; counts apply to the matching calls in total. Built with the {@link Mtd} namespace
 * ({@code mtd.getters()}, {@code mtd.named("set*")}, …) and combined with {@link #and}, {@link #or},
 * {@link #except}.
 */
public final class MethodMatcher {

    private final Predicate< Method > predicate;
    private final String description;

    MethodMatcher(Predicate< Method > predicate, String description) {
        this.predicate = predicate;
        this.description = description;
    }

    public boolean matches(Method method) {
        return predicate.test(method);
    }

    public String describe() {
        return description;
    }

    public MethodMatcher and(MethodMatcher other) {
        return new MethodMatcher(m -> matches(m) && other.matches(m), description + " and " + other.description);
    }

    public MethodMatcher or(MethodMatcher other) {
        return new MethodMatcher(m -> matches(m) || other.matches(m), description + " or " + other.description);
    }

    /** This group without the methods {@code other} matches: {@code mtd.setters().except(mtd.named("setText"))}. */
    public MethodMatcher except(MethodMatcher other) {
        return new MethodMatcher(m -> matches(m) && !other.matches(m), description + " except " + other.description);
    }

    @Override
    public String toString() {
        return description;
    }
}
