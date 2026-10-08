package org.eu.de.stuntmock;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.DoublePredicate;
import java.util.function.IntPredicate;
import java.util.function.LongPredicate;
import java.util.function.Predicate;

import org.eu.de.stuntmock.internal.ArgMatcher;
import org.eu.de.stuntmock.internal.MatcherStack;

/**
 * Argument matchers for {@code when}/{@code verify}, used through the {@code arg} namespace:
 * {@code when(() -> service.find(arg.eq(1), arg.any()))}. The namespace object is {@link Stunt#arg}; a delegating
 * test interface can carry it as a constant so that tests need no import at all, and no matcher name ever
 * collides with another library's.
 *
 * <p>Rule: within one mapped call, either every argument is a matcher or none is; a plain value is compared with
 * {@code equals}. To mix, wrap the plain values in {@link #eq(Object)}. Use the primitive forms
 * ({@link #anyInt()} …) for primitive parameters: {@link #any()} returns {@code null}, which cannot be unboxed.
 */
public final class Args {

    Args() {
    }

    // ---------------------------------------------------------------- any

    /**
     * Matches anything, including {@code null}. Returns {@code null}, so it cannot stand for a primitive
     * parameter: write {@code arg.anyInt()} and friends there, otherwise the closure fails with a
     * {@code NullPointerException} before the call is reached.
     *
     * <pre>{@code
     * when(() -> dao.save(arg.any())).thenDoNothing();
     * when(() -> calc.add(arg.anyInt(), arg.anyInt())).thenReturn(7);   // not arg.any() for int
     * }</pre>
     *
     * Rule for every mapped call: either every argument is a matcher or none is. To mix, wrap the plain values
     * in {@link #eq(Object)}.
     */
    public < T > T any() {
        MatcherStack.push(ArgMatcher.any(null));
        return null;
    }

    /**
     * Matches any instance of {@code type} (or {@code null}). Also the way to pick an overload in the by-name
     * form or when the compiler cannot decide: {@code when(Greeter.class, g -> g.pick(arg.any(String.class)))}.
     */
    public < T > T any(Class< T > type) {
        MatcherStack.push(ArgMatcher.any(type));
        return null;
    }

    /** Matches any {@code int}; returns {@code 0}, which is what makes it usable for a primitive parameter. */
    public int anyInt() {
        MatcherStack.push(ArgMatcher.any(int.class));
        return 0;
    }

    /** Matches any {@code long}. */
    public long anyLong() {
        MatcherStack.push(ArgMatcher.any(long.class));
        return 0L;
    }

    /** Matches any {@code double}. */
    public double anyDouble() {
        MatcherStack.push(ArgMatcher.any(double.class));
        return 0d;
    }

    /** Matches any {@code float}. */
    public float anyFloat() {
        MatcherStack.push(ArgMatcher.any(float.class));
        return 0f;
    }

    /** Matches any {@code boolean}: {@code when(() -> flag.set(arg.anyBoolean()))}. */
    public boolean anyBoolean() {
        MatcherStack.push(ArgMatcher.any(boolean.class));
        return false;
    }

    /** Matches any {@code short}. */
    public short anyShort() {
        MatcherStack.push(ArgMatcher.any(short.class));
        return 0;
    }

    /** Matches any {@code byte}. */
    public byte anyByte() {
        MatcherStack.push(ArgMatcher.any(byte.class));
        return 0;
    }

    /** Matches any {@code char}. */
    public char anyChar() {
        MatcherStack.push(ArgMatcher.any(char.class));
        return 0;
    }

    /** Matches any {@code String} or {@code null}; selects the {@code String} overload where there is one. */
    public String anyString() {
        MatcherStack.push(ArgMatcher.any(String.class));
        return null;
    }

    /** Matches any {@code List} or {@code null}; returns an empty list so the closure never trips over it. */
    public < T > List< T > anyList() {
        MatcherStack.push(ArgMatcher.any(List.class));
        return Collections.emptyList();
    }

    /** Matches any {@code Set} or {@code null}. */
    public < T > Set< T > anySet() {
        MatcherStack.push(ArgMatcher.any(Set.class));
        return Collections.emptySet();
    }

    /** Matches any {@code Collection} or {@code null}. */
    public < T > Collection< T > anyCollection() {
        MatcherStack.push(ArgMatcher.any(Collection.class));
        return Collections.emptyList();
    }

    /** Matches any {@code Map} or {@code null}. */
    public < K, V > Map< K, V > anyMap() {
        MatcherStack.push(ArgMatcher.any(Map.class));
        return Collections.emptyMap();
    }

    // ---------------------------------------------------------------- equality and identity

    /**
     * Matches a value equal to {@code expected} ({@code Objects.deepEquals}, so arrays compare by content and
     * the same instance always matches). Plain values are compared the same way, so {@code eq} is only needed
     * next to other matchers:
     *
     * <pre>{@code
     * when(() -> service.find("x", 3));                       // plain values, no eq needed
     * when(() -> service.find(arg.eq("x"), arg.anyInt()));    // mixed: wrap the value
     * }</pre>
     */
    public < T > T eq(T expected) {
        MatcherStack.push(ArgMatcher.eq(expected));
        return expected;
    }

    /** Matches the very same instance ({@code ==}), where {@code equals} would be too lenient. */
    public < T > T same(T expected) {
        MatcherStack.push(ArgMatcher.same(expected));
        return expected;
    }

    /** Matches {@code null} only: {@code when(() -> dao.find(arg.isNull())).thenThrow(new IllegalArgumentException())}. */
    public < T > T isNull() {
        MatcherStack.push(ArgMatcher.isNull());
        return null;
    }

    /** Matches anything but {@code null}. */
    public < T > T notNull() {
        MatcherStack.push(ArgMatcher.notNull());
        return null;
    }

    // ---------------------------------------------------------------- predicates

    /**
     * The universal matcher: {@code arg.argThat((String s) -> s.length() > 5)}. The lambda parameter must be
     * explicitly typed, because the compiler has no other way to learn {@code T}; or give the type first with
     * {@link #argThat(Class, Predicate)}. A value of another type does not match (no exception).
     */
    public < T > T argThat(Predicate< T > predicate) {
        MatcherStack.push(ArgMatcher.argThat(null, predicate, "argThat(...)"));
        return null;
    }

    /** {@link #argThat(Predicate)} with the type given first: {@code arg.argThat(String.class, s -> s.isBlank())}. */
    public < T > T argThat(Class< T > type, Predicate< T > predicate) {
        MatcherStack.push(ArgMatcher.argThat(type, predicate, "argThat(" + type.getSimpleName() + ")"));
        return null;
    }

    /** A predicate on an {@code int} parameter: {@code arg.intThat(i -> i > 0)}. */
    public int intThat(IntPredicate predicate) {
        MatcherStack.push(ArgMatcher.argThat(int.class, (Integer i) -> predicate.test(i), "intThat(...)"));
        return 0;
    }

    /** A predicate on a {@code long} parameter. */
    public long longThat(LongPredicate predicate) {
        MatcherStack.push(ArgMatcher.argThat(long.class, (Long l) -> predicate.test(l), "longThat(...)"));
        return 0L;
    }

    /** A predicate on a {@code double} parameter. */
    public double doubleThat(DoublePredicate predicate) {
        MatcherStack.push(ArgMatcher.argThat(double.class, (Double d) -> predicate.test(d), "doubleThat(...)"));
        return 0d;
    }

    /** A predicate on a {@code boolean} parameter. */
    public boolean booleanThat(Predicate< Boolean > predicate) {
        MatcherStack.push(ArgMatcher.argThat(boolean.class, predicate, "booleanThat(...)"));
        return false;
    }

    /** Matches a {@code String} containing {@code part}; {@code null} does not match. */
    public String contains(String part) {
        MatcherStack.push(ArgMatcher.argThat(String.class, (String s) -> s != null && s.contains(part),
            "contains(\"" + part + "\")"));
        return null;
    }

    /** Matches a {@code String} starting with {@code prefix}: {@code when(() -> greeter.greet(arg.startsWith("B")))}. */
    public String startsWith(String prefix) {
        MatcherStack.push(ArgMatcher.argThat(String.class, (String s) -> s != null && s.startsWith(prefix),
            "startsWith(\"" + prefix + "\")"));
        return null;
    }

    /** Matches a {@code String} ending with {@code suffix}. */
    public String endsWith(String suffix) {
        MatcherStack.push(ArgMatcher.argThat(String.class, (String s) -> s != null && s.endsWith(suffix),
            "endsWith(\"" + suffix + "\")"));
        return null;
    }

    /** Matches a {@code String} matching the regular expression as a whole ({@code String.matches}). */
    public String matches(String regex) {
        MatcherStack.push(ArgMatcher.argThat(String.class, (String s) -> s != null && s.matches(regex),
            "matches(\"" + regex + "\")"));
        return null;
    }

    // ---------------------------------------------------------------- captors

    /**
     * Creates a captor for an argument the test wants to look at after the production code ran. Use
     * {@code captor.capture()} in place of the argument; it matches any value of the type and records it.
     *
     * <pre>{@code
     * Captor<Invoice> saved = arg.captor(Invoice.class);
     * verify(() -> dao.save(saved.capture())).thenDoNothing();
     * service.bill(order);
     * assertEquals(42L, saved.getValue().getCustomerId());   // the last recorded argument; getValues() for all
     * }</pre>
     */
    public < T > Captor< T > captor(Class< T > type) {
        return new Captor<>(type);
    }

    static void registerCaptor(Captor< ? > captor) {
        MatcherStack.push(ArgMatcher.capturing(captor));
    }
}
