# Stunt

[![CI](https://github.com/borisbrodski/stuntmock/actions/workflows/ci.yml/badge.svg)](https://github.com/borisbrodski/stuntmock/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/org.eu.stuntmock/stunt.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/org.eu.stuntmock/stunt)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

**A mocking framework for JUnit 5 that changes classes in place.**

Home: [stuntmock.eu.org](https://stuntmock.eu.org) · Source: [github.com/borisbrodski/stuntmock](https://github.com/borisbrodski/stuntmock) · Maven: `org.eu.stuntmock:stunt`

Most mocking frameworks hand you a mock object and leave it to you to get it into the code under test.
Stunt instruments the class instead. Whatever the production code does to obtain an object – `new`, a static
call, a locator, a factory – the calls on it can be stubbed and verified, because the class itself carries the
interception. Static methods, private methods, final classes, objects created inside the method under test,
and JDK classes such as `java.time.LocalDate` are all within reach, and nothing in the production code has to be
redesigned to make a test possible.

The name: a stunt double stands in for the real actor in the risky scenes only. Everything else stays real.

```java
@ExtendWith(StuntExtension.class)
@StubPartially(PriceRules.class)               // real code, but stubbable and verifiable
class InvoiceServiceTest implements WithStunt {

    @Stub CustomerDao dao;                     // full mock of every instance, however the SUT obtains one

    @Test
    void createsAnInvoice() {
        when(() -> dao.findById(42L)).thenReturn(customer);
        when(() -> PriceRules.rateFor("DE")).thenReturn(0.19);
        verify(Invoice.class, i -> i.persist());        // every Invoice created inside the SUT: exactly once

        new InvoiceService().createInvoice(42L);       // no mock is passed in
    }
}
```

## Getting started

```kotlin
testImplementation("org.eu.stuntmock:stunt:0.1.0")        // Gradle
```

```xml
<dependency>                                            <!-- Maven -->
    <groupId>org.eu.stuntmock</groupId>
    <artifactId>stunt</artifactId>
    <version>0.1.0</version>
    <scope>test</scope>
</dependency>
```

Requires Java 17+ and JUnit 5. Add `-XX:+EnableDynamicAgentLoading` to the test JVM on JDK 21+ to silence the
dynamic-agent warning (Gradle: `tasks.test { jvmArgs("-XX:+EnableDynamicAgentLoading") }`); Stunt attaches
its agent itself, the same way Mockito does.

Register the extension with `@ExtendWith(StuntExtension.class)` (or set
`junit.jupiter.extensions.autodetection.enabled=true` in `junit-platform.properties`: Stunt ships the service
file, and JUnit then applies it to every test class). Then either `import static org.eu.stuntmock.Stunt.*`
or let the test class `implements WithStunt`, which brings the whole API in as default methods with no import
at all.

## The model in four lines

1. A class is **declared** with a *policy* (`@Stub`: calls answer the default value, `@StubPartially`: the real
   code runs) and optionally made *strict* (`@Verify`, `@Audit`: a call no mapping covers fails the test).
   Four verbs, four annotations, one per cell:

   | | lenient | strict |
   |---|---|---|
   | default value | `stub` / `@Stub` | `verify` / `@Verify` |
   | real code | `stubPartially` / `@StubPartially` | `audit` / `@Audit` |

   Each works on a class (every instance, statics included) or on one object: `audit(new Account())`.
2. `when(...)` maps a call to an answer and asserts nothing; `verify(...)` maps it and asserts the count.
   Both come *before* the production code runs; a chain is a sequence with per-step counts:
   `verify(() -> dao.findById(1L)).thenReturn("a").times(2).thenThrow(new NotFound()).thenReturn("b")`.
3. Arguments are matched with `arg`: `arg.any()`, `arg.eq(1)`, `arg.startsWith("x")`, `arg.captor(Foo.class)`;
   groups of methods with `mtd`: `when(account, mtd.getters()).anyTimes()`. Private methods by name:
   `when(Invoice.class, "roundTotal", arg.anyInt())`.
4. `Stunt.dump()` shows everything Stunt knows about the current test; every failure message ends with it, and
   every line of it links to the test code that declared it.

## Why Stunt

- **No redesign for testability.** Legacy code with `new`, statics and service locators is testable as it is.
- **Explicit, not magic.** Only declared classes are touched; `@Verify` is never silent; interfaces without a
  resolver are an error, not a surprise; messages name the line that declared the mapping and the line that
  called it.
- **Readable failures.** "Expected 2 calls to `setText(any String)`, got 1 … declared at `OrderTest.java:42`",
  followed by the state of every declaration and mapping.
- **Project setup once.** Type resolvers (interface → implementation by convention), per-project defaults and
  conventions live in `StuntSettings` / a `StuntInitializer`, registered once per JVM.
- **A frozen clock** for `java.time`, as an extension built entirely on the public API; off by default, one
  setting turns it on for every test class.

## Stunt, JMockit and Mockito

Each of the three is the right tool for a different code base.

| | Mockito | JMockit | Stunt |
|---|:---:|:---:|:---:|
| Static methods | ✅ | ✅ | ✅ |
| Private methods | ❌ | ✅ | ✅ |
| Objects the code under test creates with `new` | ✅ replaced | ✅ | ✅ intercepted |
| Final classes and methods | ✅ inline mock maker | ✅ | ✅ |
| JDK classes (`java.time`) | partially | partially | ✅ |
| Interfaces | ✅ | ✅ | ✅ resolver or proxy |
| Mappings and expectations declared before the act | ❌ | ✅ | ✅ |
| Verification after the act | ✅ | ✅ | ❌ |
| Strict mocks | ✅ | ✅ | ✅ |
| Ordering assertions | ✅ | ✅ | ❌ |
| Unused-stub detection | ✅ | ❌ | ❌ |
| Failures name the declaration and call sites, with a state dump | ❌ | ❌ | ✅ |
| Frozen clock built in | ❌ | ❌ | ✅ |
| Runs without an agent | ✅ except inline | ❌ | ❌ |
| Current JDKs | ✅ 8+ | ❌ lags | ✅ 17+ |
| Mature, large community | ✅ | ❌ | ❌ new |

If your code takes its collaborators through constructors and you want the most widely known tool, use
Mockito. If you have a JMockit code base that must run on a current JDK, or production code with statics,
`new` and private calls that you cannot redesign for testability, Stunt is built for exactly that.

## Documentation

- [Reference](docs/reference.md) – every concept, form and rule, with the error messages Stunt raises
- [Migrating from JMockit](docs/migrating-from-jmockit.md) – a mechanical mapping table
- [Migrating from Mockito](docs/migrating-from-mockito.md) – the same for Mockito
- The Javadoc of [`WithStunt`](src/main/java/org/eu/stuntmock/WithStunt.java) is written as a tutorial
- [Changelog](CHANGELOG.md)

## How it works, in one paragraph

A ByteBuddy agent (self-attached, as Mockito does it) retransforms a declared class and inlines an advice at the
top of every method body; no fields or methods are added, so loaded classes and existing objects are unaffected,
and line numbers survive for the debugger. A tiny dispatcher injected into the bootstrap class loader makes the
same mechanism work for JDK classes. A `when`/`verify` closure is inspected (its lambda bytecode) to find the
call, then executed in capture mode: the call is intercepted and recorded, never run. Everything else, matching,
counting, answering, is plain Java in the current test's scope.

## Credits

Stunt was built entirely by AI: the design dialogue, the code, the tests and the documentation were produced by
Claude (Anthropic), working from the requirements, the review and the decisions of its architect.

- **Architect:** Boris Brodski ([@borisbrodski](https://github.com/borisbrodski))
- **Implementation:** Claude (Anthropic)

More names will be added here as people contribute.

## License

[Apache License 2.0](LICENSE). Dependencies: [ByteBuddy](https://bytebuddy.net) and
[Objenesis](https://objenesis.org), both Apache 2.0.
