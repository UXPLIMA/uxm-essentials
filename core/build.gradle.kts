plugins {
    id("uxm.java")
}

dependencies {
    api(project(":api"))
}

// The permission literal guard reads the main sources of every module, so it needs the repository root, and a
// change in any module's sources has to rerun it rather than leave the test task up to date. The root reaches the
// test JVM through an argument provider whose path is internal, so the result stays cacheable across checkouts in
// different places.
class RepositoryRootArgument(
    @get:Internal val root: File,
) : CommandLineArgumentProvider {
    override fun asArguments(): Iterable<String> = listOf("-Duxm.repositoryRoot=${root.absolutePath}")
}

tasks.test {
    jvmArgumentProviders.add(RepositoryRootArgument(rootDir))
    inputs
        .files(fileTree(rootDir) { include("*/src/main/java/**/*.java") })
        .withPropertyName("allMainSources")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    // The reference pages are compared with what the catalogues render, and the command page quotes the English
    // description lines, so an edit to either has to rerun the comparison.
    inputs
        .files(
            fileTree(rootDir) { include("docs/user/*.md") },
            rootDir.resolve("bukkit-adapter/src/main/resources/messages/messages_en.conf"),
        ).withPropertyName("referenceInputs")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}

// Writes the command, permission and placeholder pages under docs/user from the code catalogues. The test that
// compares them runs in every build; this runs the same test in the mode that writes instead of compares.
tasks.register<Test>("generateReference") {
    description = "Writes the command, permission and placeholder reference under docs/user from the catalogues."
    group = "documentation"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform()
    filter { includeTestsMatching("com.uxplima.uxmessentials.docs.ReferencePagesTest") }
    jvmArgumentProviders.add(RepositoryRootArgument(rootDir))
    systemProperty("uxm.writeReference", "true")
    outputs.upToDateWhen { false }
}

// Prints nanoseconds per guard check on the compiled engine against asking every region in turn.
tasks.register<JavaExec>("guardBench") {
    description = "Times guard protection checks against a naive scan of every region."
    group = "verification"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.uxplima.uxmessentials.guard.GuardBench")
}
