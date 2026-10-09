package org.eu.stuntmock;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Method matchers, used through the {@code mtd} namespace: {@code when(m2, mtd.getters()).anyTimes()} maps
 * every getter of {@code m2} with one chain. The namespace object is {@link Stunt#mtd} (or the constant of
 * {@link WithStunt}).
 *
 * <p>A group chain covers every method the matcher selects, with any arguments. Counts apply to the matching
 * calls in total; a chain for a single signature always wins over a group. All overloads and private methods
 * are included; on a class ({@code when(Entity.class, ...)}) statics are included, on an object never.
 * {@code thenReturn}, {@code thenReturns} and typed {@code thenDo} need one return type or parameter list and
 * are therefore not available on a group. Matchers combine with {@link MethodMatcher#and},
 * {@link MethodMatcher#or} and {@link MethodMatcher#except}.
 */
public final class Mtd {

    Mtd() {
    }

    /**
     * The getters of the class as {@link StuntSettings} defines them. By default the Java Beans convention,
     * tolerant of the usual mix: {@code getX()} with no parameter and any result (a {@code boolean} included),
     * or {@code isX()} with no parameter returning {@code boolean} or {@code Boolean}. The name must continue
     * with an upper-case letter, so {@code getter()} and {@code issue()} are not getters.
     * {@code StuntSettings.getterConvention(LENIENT)} also accepts {@code hasX()} and any result type for
     * {@code isX()}; {@code StuntSettings.getters(predicate)} replaces the definition.
     *
     * <pre>{@code
     * var m2 = verify(new Entity());
     * when(m2, mtd.getters()).anyTimes();        // every getter of m2 runs freely
     * verify(() -> m2.setText(arg.any()));
     * }</pre>
     */
    public MethodMatcher getters() {
        return new MethodMatcher(StuntSettings::isGetter, "getters");
    }

    /**
     * The setters of the class: {@code setX(one parameter)}, whatever the method returns (fluent setters
     * included). Overloads with two parameters are not setters. {@code StuntSettings.setters(predicate)}
     * replaces the definition.
     *
     * <pre>{@code
     * when(Entity.class, mtd.setters()).thenDoNothing();     // no setter of any Entity changes anything
     * verify(m2, mtd.setters()).times(2);                    // exactly two setter calls on m2, in total
     * }</pre>
     */
    public MethodMatcher setters() {
        return new MethodMatcher(StuntSettings::isSetter, "setters");
    }

    /**
     * Methods whose name matches one of the patterns, with {@code *} (any characters) and {@code ?} (one
     * character) as wildcards; every overload of a matching name is included, private methods too.
     *
     * <pre>{@code
     * when(m2, mtd.named("get*", "is*", "has*")).anyTimes();
     * when(Entity.class, mtd.named("computeHash")).thenDoNothing();   // a private method, all overloads
     * }</pre>
     */
    public MethodMatcher named(String... patterns) {
        if (patterns.length == 0) {
            throw new StuntException("mtd.named(): at least one name or pattern is needed");
        }
        Pattern[] regexes = Arrays.stream(patterns).map(Mtd::glob).toArray(Pattern[]::new);
        return new MethodMatcher(m -> Arrays.stream(regexes).anyMatch(r -> r.matcher(m.getName()).matches()),
            "named(" + Arrays.stream(patterns).map(p -> '"' + p + '"').collect(Collectors.joining(", ")) + ")");
    }

    /**
     * Every method. {@code when(obj, mtd.anyMethod()).anyTimes()} spells out what {@code stubPartially(obj)}
     * does; {@code when(Entity.class, mtd.anyMethod()).thenThrow(e)} makes a class explode on touch. Mostly
     * useful as a base for {@code except}: {@code mtd.anyMethod().except(mtd.setters())}.
     */
    public MethodMatcher anyMethod() {
        return new MethodMatcher(m -> true, "anyMethod");
    }

    /**
     * The methods a (super)class or interface contributes, by the class that declares them: the plumbing of an
     * entity base class, say. A method the subclass overrides is declared in the subclass and therefore not in
     * the group.
     *
     * <pre>{@code
     * var e = verify(new Account());
     * when(e, mtd.declaredIn(BaseEntity.class)).anyTimes();   // getId, getVersion, ... do not matter
     * verify(() -> e.remove());
     * }</pre>
     */
    public MethodMatcher declaredIn(Class< ? > type) {
        return new MethodMatcher(m -> m.getDeclaringClass() == type, "declaredIn(" + type.getSimpleName() + ")");
    }

    /** The static methods of the class; only meaningful in the class form, an object's group never has statics. */
    public MethodMatcher staticMethods() {
        return new MethodMatcher(m -> Modifier.isStatic(m.getModifiers()), "staticMethods");
    }

    /** The private methods of the class, inherited ones included. */
    public MethodMatcher privateMethods() {
        return new MethodMatcher(m -> Modifier.isPrivate(m.getModifiers()), "privateMethods");
    }

    /** Methods returning {@code void}; combine with others: {@code mtd.setters().and(mtd.returningVoid())}. */
    public MethodMatcher returningVoid() {
        return new MethodMatcher(m -> m.getReturnType() == void.class, "returningVoid");
    }

    /**
     * The universal method matcher, a predicate on {@code java.lang.reflect.Method}:
     * {@code mtd.methodThat(m -> m.isAnnotationPresent(Transactional.class))}.
     */
    public MethodMatcher methodThat(Predicate< Method > predicate) {
        return new MethodMatcher(predicate, "methodThat(...)");
    }

    /** The complement of a matcher; {@code except} on a matcher usually reads better. */
    public MethodMatcher not(MethodMatcher matcher) {
        return new MethodMatcher(m -> !matcher.matches(m), "not " + matcher.describe());
    }

    private static Pattern glob(String pattern) {
        return Pattern.compile("\\Q" + pattern.replace("*", "\\E.*\\Q").replace("?", "\\E.\\Q") + "\\E");
    }
}
