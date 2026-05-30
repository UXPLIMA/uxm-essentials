plugins {
    id("uxmessentials.java-conventions")
    alias(libs.plugins.shadow)
}

// P0 stub: the proxy-side cross-server bus broker lands in a later phase. The
// module compiles and produces an (empty) shaded jar so the build graph is
// complete; the @Plugin descriptor and bus wiring arrive with the velocity work.

dependencies {
    implementation(project(":core"))
    api(project(":api"))
    compileOnly(libs.velocity.api)
    annotationProcessor(libs.velocity.api) // @Plugin descriptor generation
}

tasks.shadowJar { archiveBaseName.set("uxmessentials-velocity") }
