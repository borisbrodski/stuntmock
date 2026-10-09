package org.eu.stuntmock;

/**
 * The whole Stunt test API as default methods, so that a test class gets it by implementing this interface,
 * with no static import:
 *
 * <pre>{@code
 * @ExtendWith(StuntExtension.class)
 * class InvoiceServiceTest implements WithStunt {
 *
 *     @Stub CustomerDao dao;                 // full mock of every instance
 *
 *     @Test
 *     void createsAnInvoice() {
 *         when(() -> dao.findLastImportDate()).thenReturn(LocalDate.of(2026, 1, 1));
 *         verify(Invoice.class, i -> i.persist());    // every Invoice the SUT creates: exactly once
 *
 *         new InvoiceService().createInvoice(42L);   // no mock is handed in; the classes are changed in place
 *     }
 * }
 * }</pre>
 *
 * <p>Register the interface once with {@code StuntSettings.ignoreCallSites(WithStunt.class)} so that the
 * line a failure message points at is the test line, not the delegating default method.
 *
 * <h2>The model in five lines</h2>
 * <ol>
 * <li>A class is <b>declared</b> with a policy, what a call gets when nothing says otherwise: {@code @Stub}
 *     the default value, {@code @StubPartially} the real code. {@code @Verify} is a modifier on top of either:
 *     a call that no {@code when}/{@code verify} maps fails the test. Declare by annotation (class, method,
 *     field, parameter) or with the four verbs {@link #stub(Class)}, {@link #stubPartially(Class)},
 *     {@link #verify(Class)} (strict mock), {@link #audit(Class)} (strict audit); one object alone with the same
 *     four verbs on the object.</li>
 * <li>{@link #when(Call) when} maps a call to an answer and asserts nothing; {@link #verify(Call) verify}
 *     maps it and asserts the count, exactly once by default. Both come <em>before</em> the production code runs.</li>
 * <li>A mapping without a terminal keeps the class's natural behaviour; terminals are {@code thenReturn},
 *     {@code thenReturns}, {@code thenThrow}, {@code thenDo}, {@code thenDoNothing}, {@code thenCallOriginal};
 *     counts are {@code times}, {@code minTimes}, {@code maxTimes}, {@code anyTimes}, {@code never}. Terminals
 *     chain into a sequence: {@code thenReturn("a").times(2).thenThrow(e)}.</li>
 * <li>Arguments are matched with {@link #arg}: {@code arg.any()}, {@code arg.eq(1)}, {@code arg.startsWith("x")};
 *     groups of methods with {@link #mtd}: {@code when(m2, mtd.getters()).anyTimes()}.</li>
 * <li>{@link #dump()} shows everything Stunt knows about the current test; every failure message ends with it.</li>
 * </ol>
 *
 * <p>{@link #any()} and {@link #eq(Object)} are also available unprefixed because they are used so often; note
 * that as members of the test class they shadow a statically imported {@code any}/{@code eq} of another
 * library, which the prefixed forms never do.
 */
public interface WithStunt {

    /** The argument matchers: {@code arg.any()}, {@code arg.eq(1)}, {@code arg.anyInt()}, {@code arg.captor(Foo.class)}. See {@link Args}. */
    Args arg = Stunt.arg;

    /** The method matchers: {@code mtd.getters()}, {@code mtd.named("set*")}, {@code mtd.declaredIn(Base.class)}. See {@link Mtd}. */
    Mtd mtd = Stunt.mtd;

    // ---------------------------------------------------------------- declaring classes

    /**
     * Declares {@code type} as a full mock: every instance (including the ones the production code creates
     * itself) and every static method return the default value ({@code null}, {@code 0}, {@code false}, an
     * empty collection) unless a {@code when}/{@code verify} maps the call. Returns the handle, an object that
     * stands for <em>every</em> instance in closures.
     *
     * <pre>{@code
     * TaxTables tables = stub(TaxTables.class);
     * when(() -> tables.rateFor("DE")).thenReturn(0.19);   // for whatever TaxTables object the SUT uses
     * }</pre>
     *
     * Same as {@code @Stub} on a field, parameter, method or class. A class is declared once per test; declaring
     * it again in the same mode returns the same handle, in another mode is an error.
     */
    default < T > T stub(Class< T > type) {
        return Stunt.stub(type);
    }

    /**
     * Declares an interface (or abstract class) that no {@code TypeResolver} maps as an instance mock: a proxy
     * that intercepts calls on itself only. Hand it to the code under test; a fresh instance of some
     * implementation is not intercepted. Without a resolver, {@code stub(SomeInterface.class)} is an error, so
     * that this difference is always visible.
     *
     * <pre>{@code
     * PriceService prices = stubProxy(PriceService.class);
     * when(() -> prices.rateFor("DE")).thenReturn(0.19);
     * new TaxCalculator(prices).total(order);              // the proxy is passed in
     * }</pre>
     */
    default < T > T stubProxy(Class< T > type) {
        return Stunt.stubProxy(type);
    }

    /**
     * Declares {@code type} as a partial mock: the real code runs unless a {@code when}/{@code verify} maps the
     * call. The handle stands for every instance.
     *
     * <pre>{@code
     * Greeter greeter = stubPartially(Greeter.class);
     * when(() -> greeter.greet("Bob")).thenReturn("stubbed");   // only this argument; greet("Ann") stays real
     * }</pre>
     */
    default < T > T stubPartially(Class< T > type) {
        return Stunt.stubPartially(type);
    }

    /**
     * A strict mock: unmapped calls fail the test, mapped calls without terminal answer the default value.
     * Short for {@code verify(stub(type))}; on a class declared with either policy it only adds the strictness,
     * so {@code verify(stubPartially(type))} is a strict audit, which {@link #audit(Class)} spells in one word.
     *
     * <pre>{@code
     * AuditLog log = verify(AuditLog.class);                // strict mock: nothing runs, everything must be mapped
     * verify(() -> log.write(arg.any()));                   // exactly one write
     * when(() -> log.read()).thenCallOriginal();            // one method let through, case by case
     * }</pre>
     *
     * The four declaring verbs: {@link #stub(Class)} (defaults, lenient), {@link #stubPartially(Class)} (real
     * code, lenient), {@code verify} (defaults, strict), {@link #audit(Class)} (real code, strict). The
     * annotations: {@code @Stub}, {@code @StubPartially}, {@code @Verify}, {@code @Audit}. Returns the handle.
     */
    default < T > T verify(Class< T > type) {
        return Stunt.verify(type);
    }

    /**
     * A strict audit: unmapped calls fail the test, mapped calls without terminal run the real code. Short for
     * {@code verify(stubPartially(type))}.
     *
     * <pre>{@code
     * Account account = audit(Account.class);                       // every Account: real, but every call must be mapped
     * when(account, mtd.getters()).anyTimes();                // the getters run freely
     * verify(() -> account.book(arg.anyLong()));            // the call the test is about, real code runs
     * when(() -> account.balance()).thenReturn(ZERO);           // one method replaced, case by case
     * }</pre>
     *
     * Mockito's {@code audit} is {@link #stubPartially(Class)}: lenient. An audit has no Mockito counterpart.
     */
    default < T > T audit(Class< T > type) {
        return Stunt.audit(type);
    }

    /**
     * A strict mock of one object: unmapped calls on <em>this</em> object fail, every other instance of its
     * class stays as it is. Returns the object, so it can be written inline:
     *
     * <pre>{@code
     * var m1 = new MyClass();
     * var m2 = verify(new MyClass());               // strict mock of m2 only
     * var m3 = audit(new MyClass());                  // strict audit of m3 only
     * when(m3::getId).anyTimes();
     * verify(() -> m3.setText(arg.any()));
     * m1.setTitle("x");                             // m1 is not declared: real code
     * m3.setTitle("x");                             // UnexpectedCallError
     * }</pre>
     *
     * An instance declaration wins over the declaration of its class, so {@code stubPartially(m1)} exempts one
     * object from a strict class-level declaration. Statics are never covered by an instance declaration.
     */
    default < T > T verify(T instance) {
        return Stunt.verify(instance);
    }

    /** A strict audit of one object: real code, every call on it must be mapped. See {@link #verify(Object)}. */
    default < T > T audit(T instance) {
        return Stunt.audit(instance);
    }

    /**
     * Declares one object as a full mock: unmapped calls on <em>this</em> object return the default value, every
     * other instance of its class stays as it is. Returns the object; see {@link #verify(Object)} for the scope
     * rules and for making it strict.
     */
    default < T > T stub(T instance) {
        return Stunt.stub(instance);
    }

    /**
     * Declares one object as a partial mock: the real code runs unless mapped, only on <em>this</em> object.
     * Returns the object; see {@link #verify(Object)} for the scope rules and for making it strict.
     */
    default < T > T stubPartially(T instance) {
        return Stunt.stubPartially(instance);
    }

    // ---------------------------------------------------------------- when

    /**
     * Maps the call made in the closure and asserts nothing about it. The closure contains exactly one call,
     * which is intercepted and never executed; argument expressions are evaluated normally.
     *
     * <pre>{@code
     * when(() -> dao.findById(1L)).thenReturn(entity);          // this argument
     * when(() -> dao.findById(arg.anyLong())).thenReturn(entity); // any argument
     * when(() -> AppProperties.isSomething()).thenReturn(false); // a static
     * when(() -> myObject.persist()).thenDoNothing();            // this instance only: a real object in the closure
     * when(() -> handle.persist()).thenDoNothing();              // every instance: a handle in the closure
     * when(dao::findLastImportDate).thenReturn(today);           // a bound method reference, no parentheses
     * }</pre>
     *
     * Without a terminal the class's natural behaviour applies (defaults for {@code @Stub}, real code
     * otherwise); the last step of a {@code when} answers any number of calls. The class must be declared.
     */
    default Stubbing when(Call call) {
        return Stunt.when(call);
    }

    /**
     * Maps a method of every instance of its class, given as an unbound method reference:
     * {@code when(Greeter::getGreeting).thenReturn("x")}. For methods with a unique name; an overloaded name
     * needs the class form {@link #when(Class, CallOn)}.
     */
    default < T > Stubbing when(CallOn< T > call) {
        return Stunt.when(call);
    }

    /**
     * Maps a call for every instance of the class; the closure receives the handle. The form for methods with
     * arguments and for overloads, which the compiler resolves inside the closure.
     *
     * <pre>{@code
     * when(Invoice.class, i -> i.total(arg.anyInt())).thenReturn(BigDecimal.TEN);
     * when(Greeter.class, g -> g.pick(arg.any(String.class))).thenReturn("the String overload");
     * }</pre>
     */
    default < T > Stubbing when(Class< T > type, CallOn< T > call) {
        return Stunt.when(type, call);
    }

    /**
     * Maps a private method by name, for every instance: {@code when(Invoice.class, "roundTotal", arg.anyInt())}.
     * Arguments are plain values or matchers, never mixed; a matcher such as {@code arg.any(String.class)}
     * picks the overload.
     */
    default Stubbing when(Class< ? > type, String methodName, Object... args) {
        return Stunt.when(type, methodName, args);
    }

    /**
     * Maps a group of methods of one object at once, with any arguments: the way to say that some calls do not
     * matter on a strict audit.
     *
     * <pre>{@code
     * when(m2, mtd.getters()).anyTimes();                               // every getter runs freely
     * when(m2, mtd.setters().except(mtd.named("setText"))).thenDoNothing();
     * }</pre>
     *
     * One chain covers the whole group; counts apply to the matching calls in total. A chain for one signature
     * always wins over a group. Statics are never part of an object's group. {@code thenReturn},
     * {@code thenReturns} and typed {@code thenDo} are not available on a group (no common type).
     */
    default Stubbing when(Object instance, MethodMatcher methods) {
        return Stunt.when(instance, methods);
    }

    /** Maps a group of methods for every instance of the class, statics included: {@code when(Entity.class, mtd.setters()).thenDoNothing()}. */
    default Stubbing when(Class< ? > type, MethodMatcher methods) {
        return Stunt.when(type, methods);
    }

    // ---------------------------------------------------------------- verify

    /**
     * Maps the call like {@link #when(Call)} and asserts its count: exactly once unless a count modifier says
     * otherwise. Declared <em>before</em> the production code runs. Too many calls fail at the call, with the
     * caller's stack trace; too few fail at the end of the test, naming this line.
     *
     * <pre>{@code
     * verify(() -> mailer.send(arg.any()));                       // exactly once, real code runs
     * verify(() -> auditLog.write(arg.any())).thenDoNothing();    // exactly once, body skipped
     * verify(() -> dao.findById(1L))
     *     .thenReturn("a").times(2)                               // calls 1 and 2
     *     .thenThrow(new NotFoundException())                     // call 3
     *     .thenReturn("b").times(3);                              // calls 4 to 6; call 7 fails
     * verify(() -> cache.evict(arg.any())).never();               // the first call fails
     * }</pre>
     */
    default Stubbing verify(Call call) {
        return Stunt.verify(call);
    }

    /** {@code verify(Invoice::persist)}: every instance, exactly once in total; see {@link #when(CallOn)}. */
    default < T > Stubbing verify(CallOn< T > call) {
        return Stunt.verify(call);
    }

    /** {@code verify(Invoice.class, i -> i.persist()).times(2)}; see {@link #when(Class, CallOn)}. */
    default < T > Stubbing verify(Class< T > type, CallOn< T > call) {
        return Stunt.verify(type, call);
    }

    /** {@code verify(Invoice.class, "roundTotal", arg.eq(7))}: a private method; see {@link #when(Class, String, Object...)}. */
    default Stubbing verify(Class< ? > type, String methodName, Object... args) {
        return Stunt.verify(type, methodName, args);
    }

    /** {@code verify(m2, mtd.setters()).times(2)}: two setter calls on m2 in total; see {@link #when(Object, MethodMatcher)}. */
    default Stubbing verify(Object instance, MethodMatcher methods) {
        return Stunt.verify(instance, methods);
    }

    /** {@code verify(Entity.class, mtd.setters()).never()}: no setter of any Entity may be called. */
    default Stubbing verify(Class< ? > type, MethodMatcher methods) {
        return Stunt.verify(type, methods);
    }

    // ---------------------------------------------------------------- the two most used matchers

    /**
     * Short for {@code arg.any()}: matches anything, including {@code null}. Not for primitive parameters, it
     * returns {@code null}; use {@code arg.anyInt()} and friends there. Within one call, either every argument
     * is a matcher or none is.
     */
    default < T > T any() {
        return Stunt.any();
    }

    /**
     * Short for {@code arg.eq(expected)}: matches a value equal to {@code expected}. Only needed next to other
     * matchers: {@code service.find(eq("x"), any())}.
     */
    default < T > T eq(T expected) {
        return Stunt.eq(expected);
    }

    /** Short for {@code arg.any(type)}: any instance of {@code type} or {@code null}; also selects the overload. */
    default < T > T any(Class< T > type) {
        return Stunt.any(type);
    }

    default int anyInt() {
        return Stunt.anyInt();
    }

    default long anyLong() {
        return Stunt.anyLong();
    }

    default double anyDouble() {
        return Stunt.anyDouble();
    }

    default float anyFloat() {
        return Stunt.anyFloat();
    }

    default boolean anyBoolean() {
        return Stunt.anyBoolean();
    }

    default short anyShort() {
        return Stunt.anyShort();
    }

    default byte anyByte() {
        return Stunt.anyByte();
    }

    default char anyChar() {
        return Stunt.anyChar();
    }

    default String anyString() {
        return Stunt.anyString();
    }

    default < T > java.util.List< T > anyList() {
        return Stunt.anyList();
    }

    default < T > java.util.Set< T > anySet() {
        return Stunt.anySet();
    }

    default < T > java.util.Collection< T > anyCollection() {
        return Stunt.anyCollection();
    }

    default < K, V > java.util.Map< K, V > anyMap() {
        return Stunt.anyMap();
    }

    // ---------------------------------------------------------------- diagnostics

    /**
     * Everything Stunt knows about the current test as text: declarations with where they were declared, every
     * chain with its steps and progress, and the calls made so far. Call it from a test, from production code
     * while debugging, or from a debugger's expression view. Every failure message ends with it.
     */
    default String dump() {
        return Stunt.dump();
    }

    /** {@link #dump()} restricted to one class. */
    default String dump(Class< ? > type) {
        return Stunt.dump(type);
    }

    /** Prints {@link #dump()} to standard output. */
    default void printMocks() {
        Stunt.printMocks();
    }
}
