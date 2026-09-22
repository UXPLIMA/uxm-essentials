import net.ltgt.gradle.errorprone.errorprone
import net.ltgt.gradle.nullaway.nullaway

plugins {
    id("java-library")
    id("com.diffplug.spotless")
    id("net.ltgt.errorprone")
    id("net.ltgt.nullaway")
}

val libs = the<org.gradle.accessors.dm.LibrariesForLibs>()

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
        vendor = JvmVendorSpec.ADOPTIUM
    }
    withSourcesJar()
}

dependencies {
    "compileOnly"(libs.jspecify)
    "testCompileOnly"(libs.jspecify)
    // Error Prone's own annotations, so a type can declare itself immutable where the checker cannot see it.
    // compileOnly: they are @Retention(CLASS) markers the compiler reads and nothing needs at runtime.
    "compileOnly"(libs.errorprone.annotations)
    "testCompileOnly"(libs.errorprone.annotations)
    "errorprone"(libs.errorprone.core)
    "errorprone"(libs.nullaway)

    "testImplementation"(platform(libs.junit.bom))
    "testImplementation"(libs.bundles.testing)
    // Gradle 9 no longer bundles junit-platform-launcher in the test runtime; declare it explicitly.
    "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 25
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf(
        "-Xlint:all",
        "-Xlint:-processing",
        "-Xlint:-serial",
        // JDK 23+ pedantic lint: flags /** */ comments used as section markers (not attached to a
        // declaration). Stylistic, not a correctness signal: off, like -serial/-processing above.
        "-Xlint:-dangling-doc-comments",
        "-Werror",
        "-parameters"
    ))
    options.errorprone {
        disableWarningsInGeneratedCode.set(true)
        // New default check in Error Prone 2.50 (the JDK 21+ unnamed-variable feature). It would rewrite
        // the project's deliberate descriptive unused-catch-variable names (e.g. `catch (… badName)`) to
        // `_`, contradicting the established readability convention, so it stays off.
        disable("UnnamedVariable")
        // Other purely-stylistic checks newly enabled-by-default in Error Prone 2.50; they fire on
        // existing, correct code (arrow-switch suggestions, etc.). Kept off to keep the 2.37→2.50 bump a
        // JDK-25 enabler rather than a codebase-wide restyle. Correctness checks remain on.
        disable("RefactorSwitch")
        // Fires on the codebase's deliberate identity comparisons (Bukkit Player/World handles, lock
        // stripes) where `==` is correct and `.equals` is meaningless; newly enabled in 2.50, off here.
        disable("ReferenceEquality")
        // Javadoc-only check newly enabled in 2.50; a handful of pre-existing stale {@link} targets trip it.
        // Off to keep the bump scoped to JDK 25 / Paper 26; the broken links are a separate doc-pass follow-up.
        disable("InvalidLink")
        // Style check newly enabled in 2.50. Flags public/protected members that are effectively private
        // (mostly test helpers). Stylistic, off to keep the bump scoped.
        disable("EffectivelyPrivate")
    }
}

extensions.configure<net.ltgt.gradle.nullaway.NullAwayExtension> {
    onlyNullMarked.set(true)
}

tasks.withType<JavaCompile>().configureEach {
    options.errorprone.nullaway {
        // CheckSeverity is reused from the errorprone plugin, the nullaway plugin
        // does not define its own enum.
        severity.set(net.ltgt.gradle.errorprone.CheckSeverity.ERROR)
    }
}

configure<com.diffplug.gradle.spotless.SpotlessExtension> {
    java {
        palantirJavaFormat(libs.versions.palantir.fmt.get())
        removeUnusedImports()
        formatAnnotations()
        importOrder("java", "javax", "org.bukkit", "io.papermc", "net.kyori", "")
        trimTrailingWhitespace()
        endWithNewline()
        toggleOffOn()
    }
    kotlinGradle { ktlint("1.5.0") }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = false
    }
    systemProperty("junit.jupiter.execution.parallel.enabled", "true")
    // Pinned, because an unset max heap is a quarter of the host's RAM: the same suite got ~5 GB on a
    // workstation and ~1.7 GB on a 7 GB CI runner, where the parallel MockBukkit servers then ran out of
    // heap. With a fixed ceiling every machine runs the test JVM the same way, and one runner-sized value
    // is what the build is verified against.
    maxHeapSize = "2g"
    // A guard reads files the compiler never turns into a class: the text of the sources, comments
    // included, the documents, and the version catalogue. Gradle reruns a test only when one of its
    // inputs changes, and none of those were inputs, so a change to a comment or a document left the
    // test up to date and the build green without running the guards that read it. Proved in uxmGlow
    // on 2026-09-22: a comment naming ActionContext.builder( broke three guards and the build passed.
    // The whole repository's text is declared, because a guard in one module may read another.
    inputs.files(
            rootProject.fileTree(rootProject.projectDir) {
                include("**/src/main/java/**", "**/src/test/java/**", "docs/**", "gradle/libs.versions.toml")
                exclude("**/build/**", ".gradle/**", "buildSrc/**")
            })
        .withPathSensitivity(PathSensitivity.RELATIVE)
        .withPropertyName("guardedText")
}

// A skipped test protects nothing, and it reports as green. MockBukkit turns an unimplemented mock into a
// TestAbortedException, which JUnit records as a skip, so a guard can stop running and nobody is told. There
// is no legitimate skip in this repository, so a skip fails the build.
val verifyNoSkippedTests =
    tasks.register("verifyNoSkippedTests") {
        description = "Fail the build when a test was skipped instead of run."
        group = "verification"
        dependsOn(tasks.named("test"))
        val results = layout.buildDirectory.dir("test-results/test")
        doLast {
            val folder = results.get().asFile
            if (!folder.isDirectory) {
                return@doLast
            }
            val skippedSuites = mutableListOf<String>()
            val counter = Regex("""skipped="(\d+)"""")
            for (file in folder.listFiles().orEmpty()) {
                if (!file.name.startsWith("TEST-") || !file.name.endsWith(".xml")) {
                    continue
                }
                // The first ">" in the file closes the XML declaration, not the testsuite tag.
                val head = file.readText().substringAfter("<testsuite").substringBefore(">")
                val skipped = counter.find(head)?.groupValues?.get(1)?.toInt() ?: 0
                if (skipped > 0) {
                    skippedSuites.add("  " + file.name.removePrefix("TEST-").removeSuffix(".xml") + ": " + skipped)
                }
            }
            if (skippedSuites.isNotEmpty()) {
                throw GradleException(
                    "A test was skipped rather than run, so it guards nothing:\n" +
                        skippedSuites.joinToString("\n"),
                )
            }
        }
    }

tasks.named("check") { dependsOn(verifyNoSkippedTests) }

// Taken from uxmLib, where it was written first, on 2026-09-22.
// MockBukkit answers a method it has not implemented with UnimplementedOperationException, which extends
// JUnit's TestAbortedException. A test that reaches one is therefore recorded as skipped rather than failed:
// the build stays green, and every assertion after that call goes unrun without anybody being told. A skip
// somebody wrote on purpose is a decision and stays allowed, an @Disabled with a reason or an integration
// test with nothing to connect to; this one is a test that quietly stopped testing, so it fails the build.
val verifyNoAbortedTests =
    tasks.register("verifyNoAbortedTests") {
        description = "Fails when a test was aborted by a MockBukkit method that is not implemented."
        val results = layout.buildDirectory.dir("test-results/test")
        doLast {
            val abortedCase =
                Regex(
                    "<testcase name=\"([^\"]*)\" classname=\"([^\"]*)\"[^>]*>\\s*" +
                        "<skipped[^>]*type=\"org\\.mockbukkit\\.mockbukkit\\.exception\\." +
                        "UnimplementedOperationException"
                )
            val files = results.get().asFile.listFiles { file -> file.name.endsWith(".xml") } ?: emptyArray()
            val aborted =
                files.flatMap { file ->
                    abortedCase.findAll(file.readText()).map { "${it.groupValues[2]}.${it.groupValues[1]}" }
                }
                    .sorted()
            if (aborted.isNotEmpty()) {
                throw GradleException(
                    aborted.joinToString(
                        prefix = "Aborted by an unimplemented MockBukkit method, so nothing the test " +
                            "claims to check was checked:\n",
                        separator = "\n",
                        postfix = "\nAssert against what the mock implements, or mark the test @Disabled " +
                            "with the reason, so the gap is one a reader can see.",
                    ) { "  $it" }
                )
            }
        }
    }

tasks.named<Test>("test") { finalizedBy(verifyNoAbortedTests) }
