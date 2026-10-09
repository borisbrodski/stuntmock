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
 * Declares classes as strict audits: the real code runs unless a {@code when}/{@code verify} maps the call, and
 * a call no mapping covers fails the test. Short for {@code @StubPartially @Verify} on the same element.
 *
 * <pre>{@code
 * @Audit Account account;                 // field: every Account, real but every call must be mapped
 * @Audit(Reviewer.class)                     // class or method level
 * }</pre>
 *
 * The four declaring annotations: {@link Stub} (defaults, lenient), {@link StubPartially} (real code, lenient),
 * {@link Verify} (defaults, strict), {@code @Audit} (real code, strict). Unlike Mockito's spy, a Stunt spy is
 * strict; the lenient partial mock is {@link StubPartially}. Repeatable; the programmatic twin is
 * {@code Stunt.audit(Class)} / {@code Stunt.audit(Object)}.
 */
@Documented
@Retention(RUNTIME)
@Target({TYPE, METHOD, FIELD, PARAMETER})
@Repeatable(Audit.List.class)
public @interface Audit {

    /** The classes to declare when the annotation is placed on a type or a method; unused on fields and parameters. */
    Class< ? >[] value() default {};

    /** Container for repeated {@code @Audit} annotations; never written by hand. */
    @Documented
    @Retention(RUNTIME)
    @Target({TYPE, METHOD, FIELD, PARAMETER})
    @interface List {
        Audit[] value();
    }
}
