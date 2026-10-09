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
 * Declares classes as strict mocks: calls answer the default value unless a {@code when}/{@code verify} maps
 * them, and a call no mapping covers fails the test. Short for {@code @Stub @Verify} on the same element; on
 * an element that also carries {@code @StubPartially} it adds the strictness to the real-code policy, which
 * {@link Audit} spells in one word.
 *
 * <pre>{@code
 * @Verify CustomerDao dao;                // strict mock: every call must be mapped, nothing runs
 * @StubPartially @Verify Account account;        // strict audit, same as @Audit
 * @Verify(Dao.class)                               // class or method level
 * }</pre>
 *
 * The four declaring annotations: {@link Stub} (defaults, lenient), {@link StubPartially} (real code, lenient),
 * {@code @Verify} (defaults, strict), {@link Audit} (real code, strict). On a test method the strictness applies
 * to that test only. Repeatable; the programmatic twin is {@code Stunt.verify(Class)} / {@code Stunt.verify(Object)}.
 */
@Documented
@Retention(RUNTIME)
@Target({TYPE, METHOD, FIELD, PARAMETER})
@Repeatable(Verify.List.class)
public @interface Verify {

    /** The classes to declare when the annotation is placed on a type or a method; unused on fields and parameters. */
    Class< ? >[] value() default {};

    /** Container for repeated {@code @Verify} annotations; never written by hand. */
    @Documented
    @Retention(RUNTIME)
    @Target({TYPE, METHOD, FIELD, PARAMETER})
    @interface List {
        Verify[] value();
    }
}
