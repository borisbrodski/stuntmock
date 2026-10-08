# Contributing

Issues and pull requests are welcome. A few things that keep the project consistent:

- **One vocabulary.** The public API is small on purpose: four declaring verbs, `when`/`verify`, six terminals,
  five count modifiers, the `arg` and `mtd` namespaces. A feature that needs a new word needs a reason that
  survives the question "could this be a combination of existing words?".
- **Tests are the specification.** Every behaviour and every error message has a test; `src/test/java/.../unit`
  is organised one class per feature, with fake production classes in `.../fakes`. A change in behaviour comes
  with the test that pins it. The test task fails if the suite shrinks; raise `expectedMinTests` in
  `build.gradle.kts` (and `EXPECTED_MIN_TESTS` in `scripts/build-without-gradle.sh`) when you add tests.
- **Messages are part of the API.** A failure message names the rule, the line that declared the mapping and
  what to write instead. Keep that standard.
- **Build.** `./gradlew build` (needs Maven Central). Without network access, `scripts/build-without-gradle.sh`
  compiles and runs the suite against distribution jars of ByteBuddy, Objenesis and the JUnit console launcher.
- **Style.** Four-space indentation, 120 columns, Javadoc on every public member; see `.editorconfig`.

Releases: tag `vX.Y.Z` on `main`; the release workflow builds, signs, publishes to Maven Central and creates the
GitHub release. The changelog entry for the version goes in the same commit as the tag.
