package org.eu.de.stuntmock.internal;

import java.util.LinkedHashSet;
import java.util.Set;

import org.eu.de.stuntmock.Mode;

/**
 * One declared class: what the user wrote, the class whose method bodies execute for it, the mode, and the
 * handle the test uses in {@code when}/{@code verify}. Internal.
 */
public final class Declaration {

    private final Class< ? > declaredType;
    private final Class< ? > implementation;
    private final Mode mode;
    private final Object handle;
    private final boolean proxy;
    private final boolean instance;
    private final String declaredAt;
    private final Scope scope;
    private final Set< String > hierarchyNames;
    private final String group;

    Declaration(Class< ? > declaredType, Class< ? > implementation, Mode mode, Object handle,
                boolean proxy, String declaredAt, Scope scope, String group) {
        this(declaredType, implementation, mode, handle, proxy, false, declaredAt, scope, group);
    }

    Declaration(Class< ? > declaredType, Class< ? > implementation, Mode mode, Object handle,
                boolean proxy, boolean instance, String declaredAt, Scope scope, String group) {
        this.group = group;
        this.declaredType = declaredType;
        this.implementation = implementation;
        this.mode = mode;
        this.handle = handle;
        this.proxy = proxy;
        this.instance = instance;
        this.declaredAt = declaredAt;
        this.scope = scope;
        this.hierarchyNames = new LinkedHashSet<>();
        for (Class< ? > c : Hierarchy.of(implementation)) {
            hierarchyNames.add(c.getName());
        }
    }

    /** The type as written by the test. */
    public Class< ? > declaredType() {
        return declaredType;
    }

    /** The class whose method bodies execute; equals {@link #declaredType()} unless a resolver or a proxy is involved. */
    public Class< ? > implementation() {
        return implementation;
    }

    public Mode mode() {
        return mode;
    }

    /** The object standing for the type in {@code when}/{@code verify} closures. */
    public Object handle() {
        return handle;
    }

    /** True when only calls on {@link #handle()} itself are intercepted: a proxy or an instance declaration. */
    public boolean instanceScoped() {
        return proxy || instance;
    }

    /** True for a proxy of an unresolvable interface; it has no original code. */
    public boolean proxy() {
        return proxy;
    }

    /** True for a declaration of one real object ({@code verify(m2)}); the class keeps its own mode for the rest. */
    public boolean instance() {
        return instance;
    }

    public String declaredAt() {
        return declaredAt;
    }

    public Scope scope() {
        return scope;
    }

    /** The infrastructure label this declaration was made under ({@code StuntSettings.infrastructure}), or {@code null}. */
    public String group() {
        return group;
    }

    /** Whether a call on {@code receiver} belongs to this declaration. */
    boolean covers(Object receiver) {
        if (instanceScoped()) {
            return receiver == handle;
        }
        return implementation.isInstance(receiver);
    }

    /** Whether a static call declared in the named class belongs to this declaration. */
    boolean coversStatic(String declaringTypeName) {
        return !instanceScoped() && hierarchyNames.contains(declaringTypeName);
    }

    /** Whether the class named in a {@code when}/{@code verify} closure relates to this declaration. */
    boolean relatesTo(Class< ? > owner) {
        return owner.isAssignableFrom(implementation) || implementation.isAssignableFrom(owner)
            || owner.isAssignableFrom(declaredType);
    }

    /**
     * True if this declaration is at least as specific as {@code other}: a subclass wins over its superclass,
     * and a declaration of one object wins over the declaration of its class.
     */
    /**
     * Whether a chain declared on this declaration applies to a call dispatched under {@code called}: the same
     * declaration, or an instance declaration of an object this (type-level) declaration covers. A class-level
     * {@code when} therefore keeps applying to an object that was later given its own mode with
     * {@code verify(obj)}.
     */
    boolean appliesTo(Declaration called) {
        return called == this || (!instance && called.instance && covers(called.handle));
    }

    public boolean moreSpecificThan(Declaration other) {
        return other.implementation.isAssignableFrom(implementation) && (instance || !other.instance);
    }

    public String describe() {
        return describe(Scope.current() != null && Scope.current().isStrict(this));
    }

    public String describe(boolean strict) {
        StringBuilder sb = new StringBuilder(mode.annotation()).append(proxy ? "(proxy)" : "")
            .append(strict ? " @Verify " : " ");
        if (instance) {
            sb.append("instance ").append(Types.identity(handle)).append(" of ").append(implementation.getName());
        }
        else {
            sb.append(declaredType.getName());
            if (implementation != declaredType) {
                sb.append(" -> ").append(proxy ? "instance mock, calls on this object only" : implementation.getName());
            }
        }
        if (group != null) {
            sb.append(" [").append(group).append(']');
        }
        return sb.append(", declared ").append(declaredAt).toString();
    }

    @Override
    public String toString() {
        return describe();
    }
}
