package org.eu.stuntmock;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Declares classes as mocked in mode {@link Mode#STUB}: every instance of the class and all of its static methods return default values ({@code null}, {@code 0}, {@code false}, empty collections) unless a {@code when}/{@code verify} maps the call.
 *
 * <p>Placement decides the lifetime of the declaration:
 * <ul>
 * <li>on the test class with {@link #value()}: every test of the class;</li>
 * <li>on a static field: every test of the class, the field holds the handle;</li>
 * <li>on an instance field: every test (per-class lifecycle) or the current test (per-method lifecycle),
 *     the field holds the handle;</li>
 * <li>on a test method with {@link #value()} or on a test method parameter: the current test only.</li>
 * </ul>
 * Combine with {@code @Verify} on the same element to make the declaration strict (unmapped calls fail).
 * {@code @Stub(proxy = true)} asks for an instance mock of an interface without resolver, see {@link #proxy()}.
 * Repeatable: {@code @Stub(A.class) @Stub(B.class)} equals {@code @Stub({A.class, B.class})}.
 * The programmatic twin is {@code Stunt.stub(Class)}.
 */
@Documented
@Retention(RUNTIME)
@Target({TYPE, METHOD, FIELD, PARAMETER})
@Repeatable(Stub.List.class)
public @interface Stub {

    /** The classes to declare when the annotation is placed on a type or a method; unused on fields and parameters. */
    Class< ? >[] value() default {};

    /**
     * For an interface or abstract class that no {@link TypeResolver} maps: creates an <em>instance mock</em>, a
     * proxy object that intercepts calls on itself only. The test has to hand it to the code under test; fresh
     * instances of an implementation are not intercepted. Without this flag, declaring such a type is an error,
     * so that an instance mock is never obtained by accident (a resolver registered too late, for example).
     * Not allowed for concrete classes, which are intercepted in place.
     */
    boolean proxy() default false;

    /** Container for repeated {@code @Stub} annotations; never written by hand. */
    @Documented
    @Retention(RUNTIME)
    @Target({TYPE, METHOD, FIELD, PARAMETER})
    @interface List {
        Stub[] value();
    }
}
