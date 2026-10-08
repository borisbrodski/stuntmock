#!/bin/sh
# Build and test without Gradle, for environments where Gradle cannot run (no access to Maven Central).
# Needs the jars of ByteBuddy, byte-buddy-agent, Objenesis and the JUnit Platform console launcher; on Debian/Ubuntu:
#   apt-get install openjdk-21-jdk-headless libbyte-buddy-java libobjenesis-java junit5
# Usage: scripts/build-without-gradle.sh [extra console-launcher arguments]   (DETAILS=summary for a short report)
set -e
cd "$(dirname "$0")/.."
CP=/usr/share/java/byte-buddy.jar:/usr/share/java/byte-buddy-agent.jar:/usr/share/java/objenesis.jar:/usr/share/java/junit-platform-console-standalone.jar
rm -rf build/classes build/test-classes && mkdir -p build/classes build/test-classes
javac --release 17 -parameters -Xlint:all -Xlint:-processing -Xlint:-classfile -d build/classes -cp $CP $(find src/main/java -name '*.java')
cp -r src/main/resources/* build/classes/
javac --release 17 -parameters -Xlint:all -Xlint:-processing -Xlint:-classfile -d build/test-classes -cp build/classes:$CP $(find src/test/java -name '*.java')
cp -r src/test/resources/* build/test-classes/ 2>/dev/null || true
java -XX:+EnableDynamicAgentLoading -Djava.util.logging.config.file=${LOGCFG:-/dev/null} -jar /usr/share/java/junit-platform-console-standalone.jar execute -cp build/classes:build/test-classes:/usr/share/java/byte-buddy.jar:/usr/share/java/byte-buddy-agent.jar:/usr/share/java/objenesis.jar --scan-classpath --include-classname ".*Test$" --details=${DETAILS:-tree} --disable-banner "$@" | tee build/test-summary.txt

# Guard against silently losing tests: the suite must not shrink. Update the number when tests are
# deliberately removed, in the same commit.
EXPECTED_MIN_TESTS=286
FOUND=$(grep -oE "[0-9]+ tests found" build/test-summary.txt 2>/dev/null | grep -oE "^[0-9]+")
if [ -n "$FOUND" ] && [ "$FOUND" -lt "$EXPECTED_MIN_TESTS" ]; then
    echo "SUITE SHRANK: $FOUND tests found, expected at least $EXPECTED_MIN_TESTS" >&2
    exit 1
fi
