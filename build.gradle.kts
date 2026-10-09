plugins {
    `java-library`
    id("com.vanniktech.maven.publish") version "0.37.0"
}

group = "org.eu.stuntmock"
version = providers.gradleProperty("version").orElse("0.1.0-SNAPSHOT").get()
description = "Stunt - a mocking framework for JUnit 5 that changes classes in place"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    // sources and javadoc jars come from the maven-publish plugin (a second pair here would write the same files)
}

tasks.assemble {
    dependsOn(tasks.withType<org.gradle.jvm.tasks.Jar>()) // build/libs holds the sources and javadoc jars too, and `build` checks the javadoc
}

repositories {
    mavenCentral()
}

val byteBuddyVersion = "1.18.14"
val objenesisVersion = "3.6"
val junitVersion = "6.1.3"

dependencies {
    api("net.bytebuddy:byte-buddy:$byteBuddyVersion")
    api("net.bytebuddy:byte-buddy-agent:$byteBuddyVersion")
    implementation("org.objenesis:objenesis:$objenesisVersion")
    compileOnly("org.junit.jupiter:junit-jupiter-api:$junitVersion")

    testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")
    testImplementation("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(17)          // runs on Java 17 and newer
    // -classfile: ByteBuddy's classes carry @SuppressFBWarnings, whose annotation type is an optional dependency
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-processing", "-Xlint:-classfile", "-parameters"))
}

tasks.withType<Javadoc>().configureEach {
    (options as StandardJavadocDocletOptions).apply {
        addStringOption("Xdoclint:all,-missing", "-quiet")
        // JDK types link to the Java 17 API docs; the package index is checked in (gradle/javadoc/jdk17/element-list,
        // generated from the JDK's java.* modules) so that the build never needs network access
        linksOffline("https://docs.oracle.com/en/java/javase/17/docs/api/", layout.projectDirectory.dir("gradle/javadoc/jdk17").asFile.path)
    }
}

tasks.test {
    useJUnitPlatform()
    // run the suite on another JDK than the toolchain compiles with: ./gradlew test -PtestJdk=17
    val testJdk = providers.gradleProperty("testJdk").map { it.toInt() }.orElse(21)
    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(testJdk.get()))
    })
    // fixture classes under ...unit.fixtures are run by tests through the launcher, not by the suite
    filter { excludeTestsMatching("org.eu.stuntmock.unit.fixtures.*") }
    // Stunt attaches its agent dynamically; this flag silences the JDK 21+ warning and keeps dynamic
    // attach working should a future JDK disable it by default (JEP 451).
    jvmArgs("-XX:+EnableDynamicAgentLoading")
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = false
    }
    // Guard against silently losing tests (a refactoring that drops a block): the suite must not shrink.
    // Raise the number when tests are added; lower it only in a commit that deliberately removes tests.
    val expectedMinTests = 286
    var executed = 0L
    addTestListener(object : TestListener {
        override fun beforeSuite(suite: TestDescriptor) = Unit
        override fun afterSuite(suite: TestDescriptor, result: TestResult) = Unit
        override fun beforeTest(testDescriptor: TestDescriptor) = Unit
        override fun afterTest(testDescriptor: TestDescriptor, result: TestResult) {
            executed++
        }
    })
    doLast {
        if (executed < expectedMinTests) {
            throw GradleException("Test suite shrank: $executed tests executed, expected at least $expectedMinTests")
        }
    }
}

tasks.jar {
    manifest {
        attributes(
            "Implementation-Title" to "Stunt",
            "Implementation-Version" to project.version,
            "Automatic-Module-Name" to "org.eu.stuntmock"
        )
    }
}

mavenPublishing {
    publishToMavenCentral(automaticRelease = true)
    signAllPublications()
    coordinates("org.eu.stuntmock", "stunt", version.toString())

    pom {
        name.set("Stunt")
        description.set("A mocking framework for JUnit 5 that changes classes in place: statics, privates, fresh instances, JDK classes.")
        url.set("https://stuntmock.eu.org")
        inceptionYear.set("2026")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("borisbrodski")
                name.set("Boris Brodski")
                email.set("Boris@Brodski.name")
                url.set("https://github.com/borisbrodski")
                roles.set(listOf("architect"))
            }
        }
        scm {
            url.set("https://github.com/borisbrodski/stuntmock")
            connection.set("scm:git:https://github.com/borisbrodski/stuntmock.git")
            developerConnection.set("scm:git:ssh://git@github.com/borisbrodski/stuntmock.git")
        }
    }
}
