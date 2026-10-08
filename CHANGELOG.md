# Changelog

All notable changes to Stunt are documented here. The format follows [Keep a Changelog](https://keepachangelog.com/);
versions follow [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Fixed

- `FrozenClock` left a stale "frozen class" behind when a `static` nested test class ran before its enclosing
  class (Gradle's order); the enclosing class then started with an unfrozen clock. The frozen clock is now tied
  to the Stunt class scope instead of the lexical nesting of test classes.

## [0.1.0] – 2026-10-07

First public release.

- Declarations: `@Stub`, `@StubPartially`, `@Verify`, `@Audit`, on classes, methods, fields and parameters, and
  the four verbs on classes and single objects; explicit instance mocks with `@Stub(proxy = true)` / `stubProxy`.
- Mapping: `when` / `verify` with closures, bound and unbound method references, the class-and-closure form,
  private methods by name, and method groups (`mtd.getters()`, `mtd.named("set*")`, …).
- Chains: `thenReturn`, `thenReturns`, `thenThrow`, `thenDo`, `thenDoNothing`, `thenCallOriginal`; counts
  `times`, `minTimes`, `maxTimes`, `anyTimes`, `never`, per step; a specific chain beats a group, newest wins.
- Argument matchers in the `arg` namespace, with `any()` and `eq()` also unprefixed.
- Project setup: `StuntSettings` (type resolvers, call-site filtering, conventions, `initialization`, `defaults`),
  `StuntInitializer` via `META-INF/services`.
- Diagnostics: `Stunt.dump()`, failure messages with declaration and call sites rendered as stack-trace lines,
  infrastructure collapsing, `System.Logger` trace.
- `FrozenClock` for `java.time`, with `@PretendRunningAt` and `@RealClock`; off by default, enabled for every
  class with `StuntSettings.freezeClock(true)` or per class with `@ExtendWith(FrozenClock.class)`.
- JUnit 5 integration: both instance lifecycles, `@Nested` classes, leak-proof scopes.
