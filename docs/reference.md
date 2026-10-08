# Stunt reference

Stunt is a mocking framework for JUnit 5 that changes classes in place instead of handing mock objects to the
code under test. Whatever the production code does to obtain an object – `new`, a static call, a locator, a
DAO factory – the calls on it can be stubbed and verified, because the class itself carries the interception.
That covers static methods, private methods, final classes, instances created inside the method under test, and
JDK classes such as `java.time.LocalDate`.

The name: a stunt double stands in for the real actor in the risky scenes only. Everything else stays real.

```java
@ExtendWith(StuntExtension.class)
@StubPartially(PriceRules.class)                       // real code, but stubbable and verifiable
class InvoiceServiceTest {

    @Stub CustomerDao dao;                    // full mock of every instance, resolved via the DAO convention

    @Test
    void createsAnInvoice() {
        when(() -> dao.findLastImportDate()).thenReturn(LocalDate.of(2026, 1, 1));
        when(() -> PriceRules.rateFor(arg.eq("DE"))).thenReturn(0.19);
        verify(Invoice.class, i -> i.persist());        // every Invoice created inside the SUT: exactly once

        new InvoiceService().createInvoice(42L);       // no mock is passed in
    }
}
```

## Contents

1. [Concepts](#concepts)
2. [Declaring classes](#declaring-classes)
3. [Mapping calls: `when` and `verify`](#mapping-calls-when-and-verify)
4. [Answers and sequences](#answers-and-sequences)
5. [Counts](#counts)
6. [Matchers](#matchers)
7. [Closures: `thenDo`](#closures-thendo)
8. [Type resolvers: interfaces and located types](#type-resolvers-interfaces-and-located-types)
9. [Lifecycle](#lifecycle)
10. [Frozen clock](#frozen-clock)
11. [Diagnostics](#diagnostics)
12. [Errors Stunt raises](#errors-stunt-raises)
13. [How it works](#how-it-works)
14. [Limitations](#limitations)
15. [Building and running](#building-and-running)

## Concepts

| Term | Meaning |
|---|---|
| **Declaration** | A class is declared with a policy and optionally made strict. Only declared classes are touched; everything else is real. |
| **Policy** | What a call gets when nothing says otherwise: `@Stub` the default value, `@StubPartially` the real code. Applies to unmapped calls and to mapped calls without a terminal. |
| **Strictness** | `@Verify` on top of either policy: a call no `when`/`verify` maps fails the test. `@Stub @Verify` is a strict mock, `@StubPartially @Verify` a strict audit. `@Verify` alone is rejected. |
| **Handle** | The object a declared class is represented by in `when`/`verify` closures. A field, parameter or the return value of the declaring statement. It stands for *every* instance of the class. |
| **Mapping** | `when(...)` maps a call to an answer. `verify(...)` does the same and asserts the call count. Both come **before** the production code runs. |
| **Chain** | The steps behind one mapping: `thenReturn("a").times(2).thenThrow(e)`. |
| **Type resolver** | Maps an interface to the class that implements it (and optionally to the instance the code under test will see). |

Two scopes exist: the **class scope** (class-level declarations, `@BeforeAll`) and the **test scope** (everything
per test). Both are bound to the test thread.

## Two ways to call the API, and `StuntSettings`

`import static org.eu.de.stuntmock.Stunt.*;` brings in every verb, `arg`, `mtd`, `any()` and
`eq()`. Alternatively a test class `implements WithStunt`, which carries the same API as default methods
and the namespaces as constants, so that no import at all is needed; its Javadoc is written as a tutorial.
Register it once with `StuntSettings.ignoreCallSites(WithStunt.class)` so that messages point at test
lines.

Everything that is configured once per JVM lives in `StuntSettings`: type resolvers, ignored call-site
frames and depth, the getter/setter conventions, `infrastructure(...)` for extensions. Nothing in it is meant
to be called from a test method.

## Project setup: `initialization`, `defaults`, `StuntInitializer`

Setup has two moments, and the API keeps them apart:

- **Once per JVM, before the first test class opens**: resolvers, ignored call sites, conventions. Register a
  block with `StuntSettings.initialization(() -> { ... })`; several blocks are allowed and run in order. Stunt
  runs them right before the first class scope opens, which is also before any static `@Stub` field is bound.
  Registering after that moment is an error ("Too late …") because the first class already ran without it.
- **At the start of every test class**: the declarations and mappings a project always wants, such as a
  service locator redirected to the test container, reference data, a frozen clock. Register them with
  `StuntSettings.defaults("label", () -> { ... })` from an initialization block; Stunt runs the block inside each
  class scope before the class's own declarations, labelled as infrastructure, so dumps collapse it to one line.
  A block must not be called from a static initializer directly: `infrastructure`, `stub` and `when` need an
  open class scope, and the message says so.

```java
abstract class UnitTest implements WithStunt {
    static {
        StuntSettings.initialization(() -> {
            StuntSettings.addTypeResolver(type -> isDao(type) ? DAO.get(type) : null);
            StuntSettings.ignoreCallSites(WithStunt.class);
            StuntSettings.defaults("project", () -> {
                stubPartially(ServiceLocator.class);
                when(() -> ServiceLocator.get(any())).thenDo((Invocation inv) -> TestContainer.lookup(inv.arg(0)));
            });
        });
    }
}
```

A static initializer of a shared base class is early enough because Java initialises the test class, and with
it its base classes, before JUnit does anything with it. Without a base class, implement `StuntInitializer`
and list it in `META-INF/services/org.eu.de.stuntmock.StuntInitializer`: Stunt loads it at the same
moment, whatever the class-loading order. (JUnit's own `org.junit.jupiter.api.extension.Extension` service file
only works with `junit.jupiter.extensions.autodetection.enabled=true`, which is why registering a custom
extension that way appears to do nothing.)

## Declaring classes

Two questions decide what a declared class does: the **policy**, what a call gets when nothing says otherwise
(`stub`: the default value, `stubPartially`: the real code), and the **strictness**, whether a call no
`when`/`verify` maps is allowed or fails. Four verbs, four annotations, one per cell:

| | lenient (unmapped allowed) | strict (unmapped fails) |
|---|---|---|
| default value | `stub(X.class)` / `@Stub` | `verify(X.class)` / `@Verify` (strict mock) |
| real code | `stubPartially(X.class)` / `@StubPartially` | `audit(X.class)` / `@Audit` (strict audit) |

Each annotation has three placements, each verb a class and an object form:

```java
@Stub(TaxTables.class)                            // on the test class: for every test
@StubPartially(PriceRules.class)                  // real code unless mapped
@Verify(AuditLog.class)                           // strict mock: every call on AuditLog must be mapped, nothing runs
@Audit(Account.class)                           // strict audit: real code, every call must be mapped
class OrderTest {

    @Stub static Clock clock;                     // static field: every test, the field is the handle
    @Audit Greeter greeter;                         // instance field

    @Test
    @Stub(Mailer.class)                           // on the test method: this test only
    void sends(@Verify Ledger ledger) {           // parameter: this test only
        Counter counter = stubPartially(Counter.class);        // statements: from here on
        var cache = verify(new Cache());                       // strict mock of this object only
        var account = audit(new Account());                          // strict audit of this object only
    }
}
```

`verify` and `audit` are the strictness added to a policy, so the composed forms stay valid and mean the same:
`verify(stub(X.class))` is `verify(X.class)`, `verify(stubPartially(X.class))` is `audit(X.class)`,
`@StubPartially @Verify` is `@Audit`. On a class already declared, `verify(...)` keeps its policy and adds the
strictness; `audit(...)` on a stub is a contradiction and says so. In Mockito's vocabulary: `mock` is `stub`, `spy` is `stubPartially`, a strict mock is `verify`;
`audit` has no Mockito counterpart.

Within any cell, single methods are overridden: `when(() -> x.m()).thenReturn(v)` replaces a method of an audit,
`when(() -> x.m()).thenCallOriginal()` lets a method of a mock through, `when(x, mtd.getters()).anyTimes()`
allows a whole group. `@Verify` or `@Audit` on one test method makes a class declared at class level strict for
that test only (with the same policy; `@Verify` against a class-level `@StubPartially` is a conflict, write
`@Audit`).

Rules:

- The annotations are repeatable: `@Stub(A.class) @Stub(B.class)` equals `@Stub({A.class, B.class})`.
- A class is declared once per test. Declaring it again with the same policy returns the same handle; a
  different policy is an error, so a class-level `@Stub` cannot be turned partial by a test. Strictness can be
  added (`verify`, `audit`), never removed.
- A declaration covers the class, its superclasses and its interfaces (default methods). Calls on **instances of
  the declared class** and on **its static methods** are intercepted, including private and final ones.
- Constructors are never intercepted: `new Foo()` inside the code under test still runs the real constructor.
- `toString()`, `hashCode()` and `equals(Object)` are never mocked implicitly, in any mode: an unmapped call runs
  the class's own implementation, so declared objects work in sets and messages. An explicit mapping still wins:
  `when(x::hashCode).thenReturn(42)`, `verify(x::toString).never()`.
- A collaborator that must not run (a DAO) is a strict mock, `@Stub @Verify`, not a strict audit; with
  `@StubPartially @Verify` a bare `verify(() -> dao.save(arg.any()))` runs the real method, loudly.
- A handle is an uninitialized instance of the class (no constructor ran). Use it only in `when`/`verify`
  closures; calling real methods on it directly is undefined. `toString`, `hashCode` and `equals` on a handle are
  identity-based and never reach the class.
- An interface or abstract class is declared through a [type resolver](#type-resolvers-interfaces-and-located-types).
  Without one it is an error, unless an instance mock is requested explicitly with `@Stub(proxy = true)` /
  `stubProxy(...)`: then only calls on that object are intercepted, and you must pass it to the code under test.

## Mapping calls: `when` and `verify`

Five forms, identical for `when` and `verify`:

```java
when(() -> dao.findById(1L)).thenReturn(entity);                    // closure with one call
when(Invoice.class, i -> i.total(arg.anyInt())).thenReturn(BigDecimal.TEN); // every instance of the class
when(Invoice::persist).thenDoNothing();                             // every instance, unbound reference
when(Invoice.class, "roundTotal", arg.anyInt()).thenReturn(10);     // private method, by name
when(invoice, mtd.getters()).anyTimes();                            // a group of methods, see below
```

- The closure contains exactly **one** call. Stunt reads the lambda's bytecode to find it before executing the
  closure, so the call is never executed, and an undeclared class is reported before anything runs.
- A **concrete receiver** in the closure maps that instance only: `when(() -> myGreeter.greet("x"))` with a
  `Greeter` the test created. A **handle** or the `Type.class, it -> ...` form maps every instance.
- A parameterless method may be written as a method reference, and an unbound reference to an instance method
  needs no class: `when(Tools::version)`, `verify(Greeter::getGreeting)`, `verify(Invoice::persist)`. This works
  for methods with a unique name; an overloaded name does not compile without the class, a static or bound
  reference with a parameter is rejected with the lambda to write instead. Method references cannot take
  matchers; use a lambda for that.
- Arguments are either all plain values (compared with `equals`) or all matchers; see [Matchers](#matchers).
- Overloads are resolved by the compiler in the closure. In the by-name form, matchers such as `any(String.class)`
  select the overload; an ambiguous choice is an error that lists the candidates.

The scope of a mapping follows from where the receiver is written, not from any extra argument:

| you want | write | not |
|---|---|---|
| this instance only | `verify(() -> myClass.persist())` or `verify(myClass::persist)` | `verify(MyClass.class, () -> myClass.persist())` (does not compile: the class form takes a one-parameter closure) |
| every instance | `verify(MyClass::persist)` or `verify(MyClass.class, m -> m.persist())` | `verify(() -> handle.persist())` on a handle also means every instance |
| a static method | `verify(() -> Tools.version())` or `verify(Tools::version)` | |
| a private method | `verify(MyClass.class, "roundTotal", arg.anyInt())` | |
| a group of methods | `when(myClass, mtd.getters())`, `verify(MyClass.class, mtd.setters())` | |

A method reference never takes parentheses: `myClass::persist`, not `myClass::persist()`. Methods inherited
from an abstract superclass are mapped the same way; the superclass is instrumented with the declared class.

`when` only stubs: it never asserts anything. `verify` stubs and asserts the count: by default exactly once.

```java
verify(() -> auditLog.write(arg.any())).thenDoNothing();      // exactly one call, or the test fails
verify(() -> mailer.send(arg.any())).never();                 // any call fails immediately
verify(Invoice.class, i -> i.persist()).times(2);
```

All `verify` calls belong **before** the production code runs. A `verify` chain fails at the call when the count
is exceeded and at the end of the test when it was not reached.

When several chains match a call, the most recently declared one decides: it answers while it has steps left;
once exhausted, a `verify` chain fails the call (its count is an assertion) and a `when` chain defers to the next
older matching chain. This is how a test overrides a class-level default with a `when`, and why a
`verify(...).times(2)` declared in the test wins over a general class-level `when(...)` and still fails the
third call.

## Method groups: `mtd`

A method matcher selects a group of methods, and `when`/`verify` with one produce a single chain that covers
every method in the group, with any arguments. The typical use is a `@Verify` object whose getters do not
matter:

```java
var m2 = audit(new Entity());  // strict audit: every call on m2 must be mapped
when(m2, mtd.getters()).anyTimes();            // the getters run as usual, however often
when(m2, mtd.setters().except(mtd.named("setText"))).anyTimes();
verify(() -> m2.setText(arg.any()));           // the one call the test is about
```

| matcher | matches |
|---|---|
| `mtd.getters()` | Java Beans by default, tolerant of the usual mix: `getX()` with no parameter and any result (`boolean` included), `isX()` returning `boolean`/`Boolean`; `StuntSettings.getterConvention(LENIENT)` adds `hasX()` and any result for `isX()`, `StuntSettings.getters(predicate)` replaces the definition |
| `mtd.setters()` | `setX(one parameter)`, whatever it returns; `StuntSettings.setters(predicate)` replaces the definition |
| `mtd.named("get*", "find?")` | by name with `*`/`?` wildcards, all overloads |
| `mtd.anyMethod()` | every method (`when(obj, mtd.anyMethod()).anyTimes()` spells out `stubPartially(obj)`) |
| `mtd.declaredIn(BaseEntity.class)` | the methods a base class or interface contributes |
| `mtd.staticMethods()`, `mtd.privateMethods()`, `mtd.returningVoid()` | by modifier or return type |
| `mtd.methodThat((Method m) -> ...)` | the universal one |

Combine with `and`, `or`, `except` and `mtd.not(...)`. Groups include private methods and all overloads; on a
class (`when(Entity.class, mtd.setters())`) they include statics, on an object they never do. Counts on a group
apply to the matching calls in total: `verify(m2, mtd.setters()).times(2)` means two setter calls altogether.
`thenDoNothing`, `thenCallOriginal`, `thenThrow`, `thenDo` with `()` or `(Invocation)` and all counts work on a
group; `thenReturn`, `thenReturns` and typed `thenDo` need one return type or parameter list and are rejected
with a message to map the single method. A matcher that matches no method of the class is an error. Groups show
in the dump as `when(Entity@1a2b.<getters>)`.

**Precedence.** A chain for one signature beats a chain for a group, whatever the order they were declared in;
among equals the newest wins; an exhausted `verify` fails the call, an exhausted `when` defers to the next older
match. So the broad allowance and the specific verification above can be written in either order.

## Answers and sequences

Every terminal appends a step to the chain; the steps answer calls in order.

| Terminal | Effect |
|---|---|
| `thenReturn(v)` | returns `v`, type-checked at definition time; numeric widening (`thenReturn(1)` on a `long` method) is applied |
| `thenReturn(a, b, c)` | three steps of one call each; the last repeats (in `when`) |
| `thenReturns(iterable)` | one value per call from the iterable; the step ends when the iterable ends |
| `thenThrow(t)` | throws `t`; checked exceptions need no `throws` clause |
| `thenDo(closure)` | runs the closure and returns its value; see [Closures](#closures-thendo) |
| `thenDoNothing()` | returns the default value (`null`, `0`, `false`, empty collection); skips the body |
| `thenCallOriginal()` | runs the original method body; the way to let one method of a `@Stub` class through |

```java
verify(() -> dao.findById(1L))
    .thenReturn("test").times(2)          // calls 1–2
    .thenThrow(new NotFoundException())   // call 3
    .thenReturn("test2").times(3);        // calls 4–6; call 7 fails (verify)
```

Step rules:

- A count modifier applies to the step before it. One count per step. A count written before its terminal
  completes that step: `anyTimes().thenCallOriginal()` is `thenCallOriginal().anyTimes()`, and
  `verify(...).times(2).thenReturn(5)` answers 5 exactly twice.
- A step without a count answers once, except the last step: in `when` it repeats forever, in `verify` it is
  exactly once.
- A step advances after its maximum; a step with only a minimum (`minTimes`) never advances, so a step after it
  is an error.
- When the last step is exhausted, `when` falls back to the class's mode (default value or original), `verify`
  fails the next call.
- A chain without a terminal keeps the mode's behaviour and only counts: `verify(() -> x.m()).times(2)`.
- A chain cannot be changed once the test used it.

## Counts

| Modifier | `when` | `verify` |
|---|---|---|
| `times(n)` | the step answers `n` calls | exactly `n` calls |
| `maxTimes(n)` | the step answers up to `n` calls | at most `n` |
| `minTimes(n)` | rejected (`when` does not assert) | at least `n`, unbounded |
| `never()` | rejected | the first call fails |
| `anyTimes()` | states the default of the last step | any number of calls, nothing asserted (`minTimes(0)`) |

Like `minTimes`, `anyTimes()` has to be the last step. `when(m2::getId).anyTimes()` on a `@Verify` object lets
`getId()` run freely; `when(m2, mtd.getters()).anyTimes()` does it for every getter.

## Matchers: `arg`

Argument matchers live in the `arg` namespace (`Stunt.arg`, a static import of `Stunt.*` brings it in; a
delegating test interface can carry it as a constant). Within one call use either only values or only
matchers; wrap values in `arg.eq(...)` to mix.

| matcher | notes |
|---|---|
| `arg.any()` | anything, including `null`; **not for primitive parameters** (it returns `null`) |
| `arg.any(Foo.class)` | any `Foo` or `null`; also selects the overload |
| `arg.anyInt()`, `anyLong()`, `anyDouble()`, `anyFloat()`, `anyBoolean()`, `anyShort()`, `anyByte()`, `anyChar()` | for primitive parameters |
| `arg.anyString()`, `anyList()`, `anySet()`, `anyCollection()`, `anyMap()` | typed conveniences |
| `arg.eq(v)`, `same(v)`, `isNull()`, `notNull()` | equality (`Objects.deepEquals`), identity, null checks |
| `arg.argThat((String s) -> s.length() > 5)` | the universal matcher; type the lambda parameter or use `arg.argThat(String.class, s -> ...)` |
| `arg.intThat`, `longThat`, `doubleThat`, `booleanThat` | primitive predicates |
| `arg.contains`, `startsWith`, `endsWith`, `matches` | string matchers |
| `arg.captor(Foo.class)` / `captor.capture()` | records the argument; `captor.getValue()` / `getValues()` after the act |

`any()`, `any(Class)`, the `any*` primitive and collection forms and `eq(...)` also exist unprefixed in `Stunt` and
`WithStunt`, because they are written so often.
Why a namespace for the rest: tests that reach Stunt through a delegating interface (default methods forwarding
to `Stunt.*`, no imports needed) would otherwise have every matcher name in the class scope, where it shadows a
static import such as Hamcrest's `startsWith`. With `arg.` and `mtd.` only those two names, plus the deliberate
`any`/`eq`, enter the scope. A matcher whose type cannot fit the parameter (`arg.anyInt()` for a `String`) is rejected when the
mapping is declared.

## Closures: `thenDo`

Three shapes. Parameters must be explicitly typed and use the **boxed** types (`Integer`, not `int`); the
compiler then resolves void versus value-returning closures on its own.

```java
when(() -> counter.next()).thenDo(() -> ++calls);                              // no parameters
when(() -> log.write(arg.any())).thenDo(() -> { lines.clear(); });              // no parameters, void
when(() -> calc.add(arg.anyInt(), arg.anyInt())).thenDo((Integer a, Integer b) -> a + b); // the method's parameters
when(() -> counter.next()).thenDo((Invocation inv) -> (Integer) inv.callOriginal() + 100);
when(() -> dao.remove(arg.any())).thenDo((Invocation inv) -> deleted.add(inv.<Entity>arg(0).getId()));
```

`Invocation` offers `receiver()`, `method()`, `args()`, `arg(i)` and `callOriginal()`. The closure's arity and
parameter types are validated against the method when the mapping is declared; the returned value is
type-checked at the call. A void closure on a non-void method returns the default value. Calls the closure makes
to other declared classes are dispatched normally, so a closure can use other stubs.

## Type resolvers: interfaces and located types

An interface has no method bodies to intercept, so declaring one needs the object the production code will
obtain, and through it the class that holds the code. A `TypeResolver` provides the object:

```java
public interface TypeResolver {
    Object instanceOf(Class<?> type);                                   // null if this resolver does not know the type
    default Class<?> implementationOf(Class<?> type, Object instance) { // the class holding the code: by default
        return instance.getClass();                                     // the instance's class
    }
}
```

```java
StuntSettings.addTypeResolver(type -> isDaoInterface(type) ? DAO.get(type) : null);   // FooDAO -> the located FooDAOImpl
StuntSettings.addTypeResolver(type -> isEjbLocalInterface(type) ? TestContainer.lookup(type) : null);
```

Resolvers are asked for every declared type outside the JDK, in registration order; the first non-null object
wins. Classes are offered as well, so that `@Stub FooDAOImpl` gets the same located object as handle that
`@Stub FooDAO` gets. Override `implementationOf` only for proxied instances (Hibernate, CGLIB, JDK proxies),
whose runtime class is not the class holding the code; name that class so that fresh instances of it are
intercepted too. Stunt checks the answer: the class must be concrete, the instance must be one of it, and it
must be a subtype of the declared type. An exception inside a resolver fails the test, naming the resolver, the
type and the method, with the exception as cause; nothing is swallowed. Register resolvers once per JVM with
`StuntSettings.addTypeResolver(...)` before the first test class opens (a static initializer or guarded init
routine), or list the class in `META-INF/services/org.eu.de.stuntmock.TypeResolver`.

An interface no resolver knows is **not** declared silently: `@Stub SomeInterface x` fails with a message that
names the ways out, including the ordering trap (a static field is bound before any `@BeforeAll` runs, so a
resolver registered there comes too late; use a static initializer or `META-INF/services`). For the genuine
case, a service the test hands to the code under test, ask for an **instance mock** explicitly:
`@Stub(proxy = true) PriceService prices` or `stubProxy(PriceService.class)`. It is a proxy that intercepts calls
on itself only; fresh instances of an implementation are not intercepted, which the dump shows as
`@Stub(proxy) PriceService -> instance mock, calls on this object only`. `@Verify` composes as usual;
`@StubPartially` is not possible for a proxy (no original code).

## Lifecycle

`StuntExtension` is registered with `@ExtendWith(StuntExtension.class)` on the test class or a base class.
The jar also ships a `META-INF/services/org.junit.jupiter.api.extension.Extension` file, but JUnit only reads
it when autodetection is switched on, with `junit.jupiter.extensions.autodetection.enabled=true` in
`src/test/resources/junit-platform.properties` (or as a system property); then every test class gets the
extension and the annotation can be dropped. Without that property the annotation is required.

| Step | What Stunt does |
|---|---|
| before all | installs the agent (once per JVM); opens the class scope; instruments class-level declarations; binds static fields, and instance fields in `PER_CLASS` lifecycle |
| `@BeforeAll` methods | `when`/`verify` here become class-level chain templates; static methods use the same static API |
| test instance created | binds instance fields (`PER_METHOD` lifecycle) |
| before each | opens the test scope with a fresh copy of every class-level chain; declares method-level annotations and parameters |
| `@BeforeEach`, test body | `when`/`verify` create per-test chains; the production code runs |
| after each | rethrows fail-fast errors that production code swallowed; checks every `verify` chain reached its minimum; closes the test scope |
| after all | closes the class scope |

Class-level `verify` chains apply to every test of the class. Sequence counters and `thenReturns` iterators
restart for every test. Instrumentation of a class is permanent for the JVM but inert outside a scope.

Both JUnit instance lifecycles are supported, and `@Nested` classes: a nested class opens its scope inside the
enclosing class's scope, sees its declarations and class-level chains, and leaving it returns to the enclosing
scope (the frozen clock included). Tests of one class run on one thread; parallel execution is not supported.

Scopes cannot leak from one class into the next. A `beforeAll` or `beforeEach` that fails inside Stunt (a bad
declaration, say) closes the scope it opened before rethrowing. Should a class scope nevertheless still be
open when an unrelated class starts (its `afterAll` never ran, which a custom engine or a crash can cause),
Stunt closes it with a warning in the log and starts the new class clean. `FrozenClock` reads the real time
from `System.currentTimeMillis()`, which is never intercepted, and a mapping of `now()` that fires on a thread
whose clock is not frozen answers the real time instead of failing.

## Frozen clock

`org.eu.de.stuntmock.time.FrozenClock` freezes `java.time` for a test class; it is built entirely on the public
API. **Off by default.** Two ways to turn it on:

- for every test class at once, from an initialization block: `StuntSettings.freezeClock(true)`; the extension
  then drives the clock, nothing to register per class;
- per class, by registering it after `StuntExtension`: `now()` on `ZonedDateTime`, `LocalDate`, `LocalDateTime`, `LocalTime` and
`Instant`, with or without a `Clock` or `ZoneId`, derives from one pinned moment. Register it after
`StuntExtension`, typically once on a base test class:

```java
@ExtendWith({StuntExtension.class, FrozenClock.class})
@PretendRunningAt(year = 2000, month = 6)                 // optional: pin components for every test
class OrderTest {

    @Test
    @PretendRunningAt(year = 2030)                         // overrides the class level component by component
    void expires() {
        assertEquals(2030, ZonedDateTime.now().getYear());
        setNow(2025, 12, 24);                              // moves the clock for the rest of this test
        assertEquals(LocalDate.of(2025, 12, 24), today());
    }
}
```

The base moment is the real time at the start of the class, truncated to milliseconds, pinned before any
`@BeforeAll` runs. `FrozenClock.now()`, `today()` and `setNow(...)` are the test-side API.
`@RealClock` on a class keeps the real clock (with either way of turning it on). The rest of the five classes stays real,
and any test may still add its own `when` on them. `FrozenClockTest` is the specification.

## Diagnostics

- `Stunt.dump()` returns the state of the current thread's scope as text: declarations with where they were
  declared, every chain with its steps and progress (`thenReturn("a") [1/2]`), and the calls made so far on
  declared classes with the chain that answered each. `Stunt.dump(Foo.class)` filters. Callable from production
  code or from a debugger's expression view.
- Runs of identical consecutive calls are shown as one line with a count; the trace keeps 200 entries per class,
  so a noisy class cannot crowd out the others.
- Declarations made inside `StuntSettings.infrastructure("label", () -> { ... })` (as `FrozenClock` does for its five
  `java.time` classes and fourteen chains) are collapsed to one line per label: classes, chain count, call count.
  They are shown in full when a failure concerns one of them or the dump is filtered to their class
  (`Stunt.dump(LocalDate.class)`). A test's own `when` on such a class is never collapsed. Calls the
  infrastructure's own closures make are not traced.
- `Stunt.printMocks()` prints the dump to standard output.
- Every failure message ends with the dump.
- Every `when`, `verify` and declaration records where it was written, rendered like a stack trace
  (`at de.acme.OrderTest.createsAnInvoice(OrderTest.java:42)`) so that IDE consoles and JUnit views link it.
  Two frames by default, so a mapping made in a helper method also names the test that called the helper;
  `StuntSettings.callSiteDepth(n)` changes that. Frames of Stunt, the JDK and JUnit are skipped, and the walk stops at
  the reflective call that invoked the test. If your tests reach Stunt through a delegating interface or base
  class (default methods forwarding to `Stunt.*` to save imports), register it once next to the type resolvers
  with `StuntSettings.ignoreCallSites(WithStunt.class)` so that the site is the test line, not the delegator.
- The logger `org.eu.de.stuntmock` (`System.Logger`, so any logging backend can bind it) traces
  each dispatch decision at `DEBUG`: which chain and step answered a call, or why none matched.

## Errors Stunt raises

| Error | When |
|---|---|
| `StuntException` | a misuse while declaring or mapping: wrong return type, `null` for a primitive, closure arity, unreachable step, values mixed with matchers, chain changed after use, `minTimes`/`never` on `when`, a class-level `verify` with a lazy iterable, … Always at the line that wrote it. |
| `UndeclaredClassException` | a `when`/`verify` on a class that is not declared. Raised before the closure runs. |
| `UnexpectedCallError` | production code made a call the test did not allow: unmapped call on a `@Verify` class, or a `verify` chain exceeded. Thrown at the call, an `Error` so `catch (Exception)` cannot swallow it, and recorded to resurface at the end of the test even if `Throwable` is caught. |
| `MissingCallError` | at the end of the test, a `verify` chain received fewer calls than declared. Names the declaration line. |

## How it works

1. **Agent.** `ByteBuddyAgent.install()` obtains a `java.lang.instrument.Instrumentation` by self-attach (the
   same route Mockito uses). On JDK 21+ the JVM warns about dynamically loaded agents; the build adds
   `-XX:+EnableDynamicAgentLoading` to the test JVM, which silences the warning and is the flag the JDK will
   require in the future. A `-javaagent:byte-buddy-agent.jar` argument works as well and is picked up
   automatically.
2. **Bootstrap dispatcher.** Three tiny classes (`org.eu.de.stuntmock.dispatch`) are injected into
   the bootstrap class loader at installation. Advice inlined into a class calls only these, which is what makes
   instrumenting `java.time.LocalDate` possible. Nothing in application code may reference that package
   directly.
3. **Retransformation.** Declaring a class retransforms it (and its non-JDK superclasses and interfaces) with a
   ByteBuddy advice at the top of every method body. No fields or methods are added, so already loaded classes
   and existing instances are unaffected: their next call runs the new bytecode. Line numbers are preserved;
   stepping through instrumented code in a debugger works. A failure inside the transformation (which the JVM
   itself would swallow) is caught by Stunt's listener and reported as an error with its cause.
4. **Dispatch.** The advice asks the dispatcher whether the call belongs to a declaration in the current thread's
   scope. If not, the body runs. If so, the newest matching chain answers, or the mode decides.
5. **Capture.** A `when`/`verify` closure is inspected (SerializedLambda plus the lambda's bytecode) to find the
   call, then executed in capture mode: only that call is intercepted and recorded with its receiver and
   arguments; every other call in the closure (argument expressions) is dispatched normally.

Dependencies: `byte-buddy`, `byte-buddy-agent`, `objenesis`. No Mockito.

## Limitations

- Constructors run. Stub what a heavy constructor calls, or use a resolver that provides the instance.
- A declaration intercepts the class whose method bodies execute. A subclass that overrides a method must itself
  be declared; declaring only the superclass does not intercept the override.
- With two declared subclasses of one superclass, a mapping belongs to the declaration of its receiver
  (`b::myMethod` is about `B` even though `myMethod` is declared in the superclass). An unbound reference to the
  shared method, `verify(Abstract::myMethod)`, is ambiguous and rejected; name the class
  (`verify(B.class, Abstract::myMethod)`) or declare the superclass itself, which covers every subclass.
- Static methods inherited from a superclass are intercepted as part of the declared class's hierarchy, whichever
  class name the call site uses.
- Interfaces without a resolver need `@Stub(proxy = true)` / `stubProxy(...)` and are then instance mocks;
  `@StubPartially` is not available for them.
- JDK classes can be declared, but their hierarchy is not walked (declaring `LocalDate` does not touch
  `Object`); `java.lang.Object` and classes the JVM refuses to modify cannot be declared.
- One test thread. Calls from threads the code under test spawns are real.
- No ordering assertions between different calls. The call trace in the dump is numbered, so an in-order
  verifier can be added on top without changing the chain model.

## Building and running

```
./gradlew test
```

The Gradle build targets Java 21, pulls ByteBuddy, Objenesis and JUnit from Maven Central and sets
`-XX:+EnableDynamicAgentLoading` for the test JVM. `scripts/build-without-gradle.sh` compiles and runs the suite with plain `javac` and
the JUnit console launcher against jars in `/usr/share/java`, for environments without Gradle.

The test suite under `src/test/java/.../stunt/unit` doubles as the specification: one class per feature
(`DeclarationTest`, `SequenceTest`, `VerifyTest`, `ThenDoTest`, `MatcherTest`, `InheritanceTest`,
`ResolverTest`, `LifecycleTest`, `JdkClassTest`, `MisuseTest`, `DiagnosticsTest`, …), fake production classes in
`stunt/fakes`.
