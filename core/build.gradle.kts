plugins {
    id("uxm.java")
}

dependencies {
    api(project(":api"))
}

// Prints nanoseconds per guard check on the compiled engine against asking every region in turn.
tasks.register<JavaExec>("guardBench") {
    description = "Times guard protection checks against a naive scan of every region."
    group = "verification"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.uxplima.uxmessentials.guard.GuardBench")
}
