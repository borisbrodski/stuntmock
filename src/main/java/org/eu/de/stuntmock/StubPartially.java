package org.eu.de.stuntmock;

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
 * Declares classes as mocked in mode {@link Mode#PARTIAL}: the real code of the class runs unless a {@code when}/{@code verify} maps the call.
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
 * Repeatable: {@code @StubPartially(A.class) @StubPartially(B.class)} equals {@code @StubPartially({A.class, B.class})}.
 * The programmatic twin is {@code Stunt.stubPartially(Class)}.
 */
@Documented
@Retention(RUNTIME)
@Target({TYPE, METHOD, FIELD, PARAMETER})
@Repeatable(StubPartially.List.class)
public @interface StubPartially {

    /** The classes to declare when the annotation is placed on a type or a method; unused on fields and parameters. */
    Class< ? >[] value() default {};

    /** Container for repeated {@code @StubPartially} annotations; never written by hand. */
    @Documented
    @Retention(RUNTIME)
    @Target({TYPE, METHOD, FIELD, PARAMETER})
    @interface List {
        StubPartially[] value();
    }
}
