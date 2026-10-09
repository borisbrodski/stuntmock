# Migrating from JMockit to Stunt

JMockit and Stunt share a model: a mocked *type* rather than a mock *object*, expectations recorded before the
code under test runs, interception of statics, privates and fresh instances. Most JMockit tests translate
line by line. This guide is written so that a code-generation tool can apply it mechanically; the last section
lists what needs a human.

## Setup

```java
import static org.eu.stuntmock.Stunt.*;           // or: class MyTest implements WithStunt
import org.eu.stuntmock.*;

@ExtendWith(StuntExtension.class)
class MyTest { ... }
```

## Declaring mocked types

| JMockit | Stunt | note |
|---|---|---|
| `@Mocked Foo foo` (field or parameter) | `@Stub Foo foo` | every instance and every static of `Foo`, as in JMockit |
| `@Injectable Foo foo` | `@Stub Foo foo`, passed to the code under test as before | Stunt has no injection; the handle is the object |
| `@Tested Sut sut` | construct the SUT yourself | |
| `@Capturing Base base` | `@Stub Base`, plus `@Stub` for each subclass whose own code should be intercepted | a declaration covers the class whose method bodies execute; see the reference on inheritance |
| `new MockUp<Foo>() { @Mock int bar() { return 1; } }` | `@StubPartially(Foo.class)` + `when(Foo.class, f -> f.bar()).thenReturn(1)` | a MockUp is a partial mock with one method replaced |
| `new MockUp<Foo>() { @Mock void bar() { } }` | `when(Foo.class, f -> f.bar()).thenDoNothing()` | |
| `@Mock` method with `Invocation inv` parameter, `inv.proceed()` | `thenDo((Invocation inv) -> inv.callOriginal())` | |
| mocking an interface `@Mocked Service s` where the SUT uses an implementation | declare the implementation (`@Stub ServiceImpl`) or register a `TypeResolver`; an interface alone needs `@Stub(proxy = true)` and must be passed in | Stunt does not guess implementations |

## Expectations and verifications

Both `Expectations` and `Verifications` become declarations *before* the call of the production method.

| JMockit | Stunt |
|---|---|
| `new Expectations() {{ foo.bar("x"); result = 1; }}` | `verify(() -> foo.bar("x")).thenReturn(1);` (strict: exactly once) |
| `new NonStrictExpectations() {{ foo.bar("x"); result = 1; }}` (or `minTimes = 0`) | `when(() -> foo.bar("x")).thenReturn(1);` |
| `new Expectations() {{ foo.bar("x"); }}` (void, no result) | `verify(() -> foo.bar("x"));` |
| `times = 2` | `.times(2)` |
| `minTimes = 2` / `maxTimes = 3` | `.minTimes(2)` / `.maxTimes(3)` |
| `minTimes = 0` (allowed, not checked) | `when(() -> foo.bar()).anyTimes()` |
| `result = 1; result = 2;` | `.thenReturn(1, 2)` |
| `returns(1, 2, 3)` | `.thenReturn(1, 2, 3)` |
| `result = new SomeException()` | `.thenThrow(new SomeException())` |
| `result = new Delegate() { int bar(String s) { return s.length(); } }` | `.thenDo((String s) -> s.length())` (parameters typed, boxed types) |
| `result = new Delegate() { int bar(Invocation inv, String s) { ... } }` | `.thenDo((Invocation inv) -> ...)`; `inv.arg(0)`, `inv.callOriginal()` |
| `new Foo().bar()` inside a block | `when(Foo.class, f -> f.bar())` (every instance, including ones the SUT creates) |
| `onInstance(x).bar()` | `when(() -> x.bar())` with the concrete object `x` |
| `invoke(foo, "privateMethod", arg)` / `Deencapsulation.invoke` | `when(Foo.class, "privateMethod", arg)` |
| `new Verifications() {{ foo.bar(); times = 2; }}` after the call | move it **before** the call: `verify(() -> foo.bar()).times(2);` |
| `new Verifications() {{ foo.bar((String s) = withCapture()); }}` | `Captor<String> c = arg.captor(String.class); verify(() -> foo.bar(c.capture()));` then `c.getValue()` after the call |
| `new FullVerifications() {{ ... }}` | declare the class `@Verify` (unmapped calls fail) and `verify(...)` each call |
| `new VerificationsInOrder() {{ ... }}` | not available, see below |

Each `when`/`verify` closure contains exactly one call. A mapping without a terminal keeps the class's
behaviour: default values for `@Stub`, the real code for `@StubPartially`.

## Argument matchers

All matchers carry the `arg.` prefix; `any()` and `eq()` also exist unprefixed.

| JMockit | Stunt |
|---|---|
| `any` / `anyString` / `anyInt` / `anyLong` / `anyBoolean` … | `arg.any()` / `arg.anyString()` / `arg.anyInt()` / `arg.anyLong()` / `arg.anyBoolean()` … |
| `withEqual(3)` / `with(3)` | `arg.eq(3)` |
| `withAny(x)` | `arg.any(x.getClass())` |
| `withNull()` / `withNotNull()` | `arg.isNull()` / `arg.notNull()` |
| `withSameInstance(x)` | `arg.same(x)` |
| `withInstanceOf(Foo.class)` | `arg.any(Foo.class)` |
| `withSubstring("a")` / `withPrefix` / `withSuffix` / `withMatch(regex)` | `arg.contains("a")` / `arg.startsWith` / `arg.endsWith` / `arg.matches(regex)` |
| `with(new Delegate() { boolean check(String s) { ... } })` | `arg.argThat((String s) -> ...)` |
| `withCapture()` | `arg.captor(T.class)` + `captor.capture()` |

JMockit's rule that all arguments must be matchers once one is, holds in Stunt too: `foo.bar(arg.eq("x"), arg.anyInt())`.
Use `arg.anyInt()` and friends for primitive parameters, never `arg.any()`.

## Partial mocking

| JMockit | Stunt |
|---|---|
| `new Expectations(Foo.class) {{ ... }}` (dynamic partial mocking of a class) | `@StubPartially(Foo.class)` + the mappings |
| `new Expectations(obj) {{ ... }}` (partial mocking of one object) | `stubPartially(obj)` + the mappings |
| strict partial mocking (every call must be recorded) | `@Audit(Foo.class)` / `audit(obj)` |

## What needs a human

- **`VerificationsInOrder`**: Stunt has no ordering assertions. Keep the count assertions, drop the order.
- **Mocking constructors**: a JMockit `@Mocked` type skips constructors; in Stunt `new Foo()` always runs the
  real constructor. If the constructor has side effects, map what it calls.
- **`@Capturing` across unknown subclasses**: declare each subclass whose own methods matter.
- **Mocked interfaces**: Stunt needs the class that holds the code; a `TypeResolver` or `@Stub(proxy = true)`.
- **Expectations on `java.lang` types** (`System`, `Thread`, `String`): not supported.

## A complete example

JMockit:

```java
@Test
void sendsInvoice(@Mocked CustomerDao dao, @Mocked AuditLog log) {
    new Expectations() {{
        dao.findById(42L); result = customer;
        new AuditLog().write(anyString); times = 2;
    }};

    new InvoiceService().createInvoice(42L);
}
```

Stunt:

```java
@Test
void sendsInvoice(@Stub CustomerDao dao, @Stub AuditLog log) {
    verify(() -> dao.findById(42L)).thenReturn(customer);
    verify(AuditLog.class, l -> l.write(arg.anyString())).times(2);

    new InvoiceService().createInvoice(42L);
}
```

When a test fails after the conversion, add `printMocks();` before the production call and read the output: it
lists the declared classes, every mapping with its progress, and every intercepted call.
