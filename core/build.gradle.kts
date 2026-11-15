plugins {
    id("uxm.java")
}

dependencies {
    api(project(":api"))
}

// The permission literal guard reads the main sources of every module, so it needs the repository root, and a
// change in any module's sources has to rerun it rather than leave the test task up to date.
tasks.test {
    systemProperty("uxm.repositoryRoot", rootDir.absolutePath)
    inputs
        .files(fileTree(rootDir) { include("*/src/main/java/**/*.java") })
        .withPropertyName("allMainSources")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}

// Prints nanoseconds per guard check on the compiled engine against asking every region in turn.
tasks.register<JavaExec>("guardBench") {
    description = "Times guard protection checks against a naive scan of every region."
    group = "verification"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.uxplima.uxmessentials.guard.GuardBench")
}
