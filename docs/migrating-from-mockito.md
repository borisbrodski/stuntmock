# Migrating from Mockito to Stunt

The vocabulary is deliberately close to Mockito's (`when`, `verify`, `thenReturn`, `times`, `any`, `eq`, a
captor), so most lines translate one to one. Two things are different in kind and are worth reading first:

1. **A declaration is about a type, not an object.** `@Stub CustomerDao dao` does not create a mock to pass
   around; it makes *every* `CustomerDao` behave as a mock, wherever the production code gets one. The field
   is a handle you use in `when`/`verify`. Nothing has to be injected, so `@InjectMocks` has no counterpart:
   the code under test obtains its collaborators the way it always did.
2. **Everything is declared before the act.** `verify` is written before the production method runs and
   asserts the count at the end of the test (or fails at the call when the count is exceeded). There is no
   post-hoc verification.

## Setup

```java
import static org.eu.de.stuntmock.Stunt.*;           // or: class MyTest implements WithStunt
import org.eu.de.stuntmock.*;

@ExtendWith(StuntExtension.class)                    // replaces MockitoExtension
class MyTest { ... }
```

## Creating mocks

| Mockito | Stunt | note |
|---|---|---|
| `@Mock Foo foo` | `@Stub Foo foo` | every instance of `Foo`, statics included |
| `@Spy Foo foo` / `spy(obj)` | `@StubPartially Foo foo` / `stubPartially(obj)` | real code unless mapped; `stubPartially(obj)` is for one object |
| `mock(Foo.class)` | `stub(Foo.class)` | type-scoped: covers objects the SUT creates itself |
| `mock(Foo.class, withSettings().strictness(STRICT_STUBS))` | no counterpart (no unused-stub check) | |
| `@InjectMocks Sut sut` | construct the SUT yourself | collaborators are intercepted wherever the SUT gets them |
| `mockStatic(Foo.class)` (try-with-resources) | `@Stub(Foo.class)` or `@StubPartially(Foo.class)` | statics are part of a class declaration; nothing to close |
| `mockConstruction(Foo.class)` | `@Stub(Foo.class)` | fresh instances are covered; the constructor still runs |
| mock of an interface `mock(Service.class)` | `@Stub(proxy = true) Service s` / `stubProxy(Service.class)`, or declare the implementation | an interface without a resolver is an error, not a silent proxy |
| `verifyNoMoreInteractions(foo)` / `verifyNoInteractions(foo)` | `@Verify Foo foo` / `verify(obj)` (strict: unmapped calls fail) | declared up front instead of asserted afterwards |
| `Mockito.reset(foo)` | nothing: every test starts clean | |

## Stubbing

| Mockito | Stunt |
|---|---|
| `when(foo.bar(any())).thenReturn(1)` | `when(() -> foo.bar(arg.any())).thenReturn(1)` |
| `when(foo.bar("x")).thenReturn(1, 2)` | `when(() -> foo.bar("x")).thenReturn(1, 2)` |
| `when(foo.bar()).thenThrow(new E())` | `when(() -> foo.bar()).thenThrow(new E())` |
| `when(foo.bar()).thenAnswer(inv -> inv.getArgument(0))` | `when(() -> foo.bar(arg.any())).thenDo((Invocation inv) -> inv.arg(0))` |
| `when(foo.add(anyInt(), anyInt())).thenAnswer(inv -> (int) inv.getArgument(0) + (int) inv.getArgument(1))` | `.thenDo((Integer a, Integer b) -> a + b)` (parameters typed, boxed) |
| `when(foo.bar()).thenCallRealMethod()` | `.thenCallOriginal()` |
| `doReturn(1).when(spy).bar()` | `when(() -> spy.bar()).thenReturn(1)` (no special form for spies) |
| `doNothing().when(foo).bar()` | `when(() -> foo.bar()).thenDoNothing()` |
| `doThrow(new E()).when(foo).bar()` | `when(() -> foo.bar()).thenThrow(new E())` |
| `lenient().when(...)` | `when(...)` (there is no strict-stubs mode) |
| `when(foo.bar()).thenReturn(1)` on a `mockStatic` | `when(() -> Foo.bar()).thenReturn(1)` |
| stubbing a private method | `when(Foo.class, "privateMethod", arg.anyInt()).thenReturn(1)` (not possible in Mockito) |

Mockito's rule that all arguments must be matchers once one is, holds in Stunt too; wrap plain values in
`arg.eq(...)`. `any()` and `eq()` are also available unprefixed.

## Verification

Move each `verify` **above** the production call; the count is checked at the end of the test.

| Mockito (after the act) | Stunt (before the act) |
|---|---|
| `verify(foo).bar("x")` | `verify(() -> foo.bar("x"))` |
| `verify(foo, times(2)).bar(any())` | `verify(() -> foo.bar(arg.any())).times(2)` |
| `verify(foo, never()).bar()` | `verify(() -> foo.bar()).never()` |
| `verify(foo, atLeast(2)).bar()` / `atMost(3)` | `.minTimes(2)` / `.maxTimes(3)` |
| `verify(foo).bar(captor.capture())` then `captor.getValue()` | `Captor<T> c = arg.captor(T.class); verify(() -> foo.bar(c.capture()));` then `c.getValue()` after the act |
| `verify(foo).bar()` where the call should also be stubbed | `verify(() -> foo.bar()).thenReturn(v)` — one chain stubs and verifies |
| `InOrder inOrder = inOrder(a, b); inOrder.verify(a)...` | not available, see below |

## Argument matchers

| Mockito (`ArgumentMatchers`) | Stunt (`arg`) |
|---|---|
| `any()` / `any(Foo.class)` | `arg.any()` / `arg.any(Foo.class)` |
| `anyInt()`, `anyLong()`, `anyBoolean()`, `anyString()`, `anyList()`, … | the same names under `arg.` |
| `eq(x)` / `same(x)` / `isNull()` / `notNull()` | the same names under `arg.` |
| `argThat(s -> s.length() > 5)` | `arg.argThat((String s) -> s.length() > 5)` (type the parameter) |
| `intThat`, `longThat`, `doubleThat`, `booleanThat` | the same under `arg.` |
| `contains("a")`, `startsWith`, `endsWith`, `matches` | the same under `arg.` |
| `ArgumentCaptor.forClass(T.class)` | `arg.captor(T.class)` |
| `refEq`, `isA`, `nullable` | `arg.argThat(...)`, `arg.any(T.class)`, `arg.any()` |

## What needs a human

- **`InOrder`**: Stunt has no ordering assertions. Keep the counts, drop the order.
- **Strict stubs / `UnnecessaryStubbingException`**: Stunt does not report unused stubs.
- **`RETURNS_DEEP_STUBS`, `RETURNS_SELF`, custom default answers**: not available; map the calls.
- **Mocks of interfaces** passed around as objects: `@Stub(proxy = true)` works, but consider declaring the
  implementation instead, which also covers instances the SUT creates.
- **Constructor mocking that must skip the constructor**: Stunt runs constructors; map what the constructor
  calls instead.
- **Mocking `java.lang` types** (`System`, `String`): not supported; `java.time` and most other JDK classes
  are.

## A complete example

Mockito:

```java
@ExtendWith(MockitoExtension.class)
class InvoiceServiceTest {
    @Mock CustomerDao dao;
    @Mock AuditLog log;
    @InjectMocks InvoiceService service;

    @Test
    void sendsInvoice() {
        when(dao.findById(42L)).thenReturn(customer);

        service.createInvoice(42L);

        verify(log, times(2)).write(anyString());
    }
}
```

Stunt:

```java
@ExtendWith(StuntExtension.class)
class InvoiceServiceTest implements WithStunt {
    @Stub CustomerDao dao;
    @Stub AuditLog log;

    @Test
    void sendsInvoice() {
        when(() -> dao.findById(42L)).thenReturn(customer);
        verify(() -> log.write(arg.anyString())).times(2);

        new InvoiceService().createInvoice(42L);      // however it obtains its DAO and its log
    }
}
```
