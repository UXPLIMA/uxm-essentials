import java.util.zip.ZipFile

plugins {
    id("uxmessentials.java-conventions")
    alias(libs.plugins.shadow)
}

// The optional REST add-on. Its own Paper plugin jar (uxmEssentials-rest), dormant until an operator turns it on.
//
// It compiles against the published developer API and nothing else: :bukkit-api for the front door and the events,
// :api for the views, the queries and the actions. That restriction is the point. If a REST endpoint cannot be
// written without reaching into :core or :bukkit-adapter, the published API has a hole in it, and the hole gets
// filled there (with its guard and its documentation) rather than worked around here.
//
// Everything is compileOnly. The host jar carries these classes, and paper-plugin.yml joins its classpath, so
// shading a second copy would give two class objects with the same name and a LinkageError the first time one
// crossed the boundary. Gson and configurate-hocon arrive at boot through UxmRestLoader.

dependencies {
    compileOnly(project(":bukkit-api"))
    compileOnly(project(":api"))
    compileOnly(libs.paper.api)
    compileOnly(libs.gson)
    compileOnly(libs.bundles.configs)

    testImplementation(project(":bukkit-api"))
    testImplementation(project(":api"))
    // :core is on the test classpath only, for the guard that keeps UxmRestLoader's pinned coordinates in step
    // with the ones every other loader resolves. No production class here may import it.
    testImplementation(project(":core"))
    testImplementation(libs.paper.api)
    testImplementation(libs.gson)
    testImplementation(libs.bundles.configs)
    testImplementation(libs.archunit.junit)
}

tasks.processResources {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filesMatching("paper-plugin.yml") { expand(props) }
}

tasks.shadowJar {
    archiveBaseName.set("uxmEssentials-rest")
    archiveClassifier.set("")
    // Nothing to bundle: every dependency is either the host's or the loader's. The shadow task is here so the
    // add-on produces the same one-jar artifact the other three do.
    mergeServiceFiles()
}

tasks.assemble { dependsOn(tasks.shadowJar) }

// The add-on bundles nothing. Every dependency it has is either the host's, joined through paper-plugin.yml,
// or the loader's, fetched at boot by UxmRestLoader. So the jar holds its own rest package and two resources,
// and every prefix below is a class that would exist twice on one classpath if it ever appeared here.
val forbiddenJarEntries =
    listOf(
        "com/uxplima/uxmessentials/api/" to "The published API is the host's. Two copies of an event class is a LinkageError.",
        "com/uxplima/uxmessentials/shared/" to "core is the host's, and no production class here may even import it.",
        "com/uxplima/uxmlib/" to "uxmLib must be relocated. This entry is un-relocated.",
        "com/google/gson/" to "gson is provisioned at boot by UxmRestLoader.",
        "org/spongepowered/" to "Configurate is provisioned at boot by UxmRestLoader.",
        "com/typesafe/" to "Typesafe Config arrives with Configurate through that loader.",
        "io/leangen/" to "geantyref arrives with Configurate through that loader.",
        "org/bukkit/" to "The server API belongs to the server. It is compileOnly and is never shipped.",
        "io/papermc/" to "Paper's own classes come from the running server, never from a plugin jar.",
        "net/kyori/" to "Paper owns Adventure. A shaded copy breaks the server's own serializers.",
        "org/slf4j/" to "Paper owns slf4j. A second API with no binding behind it makes logging go quiet.",
        "net/minecraft/" to "No jar of ours may carry the server. This add-on never touches NMS at all.",
        "org/bstats/" to "Only the host jar reports metrics. A companion carrying bStats would double-count a server.",
    )

val verifyJar =
    tasks.register("verifyJar") {
        description = "Fail the build when the shaded jar carries a package it must not."
        group = "verification"
        // The shaded jar has its own base name here, so it never overwrites the plain jar and only
        // shadowJar has to run first.
        dependsOn(tasks.shadowJar)
        val jar = tasks.shadowJar.flatMap { shadow -> shadow.archiveFile }
        inputs.file(jar)
        doLast {
            val names = mutableListOf<String>()
            ZipFile(jar.get().asFile).use { archive ->
                val entries = archive.entries()
                while (entries.hasMoreElements()) {
                    names.add(entries.nextElement().name)
                }
            }
            val found = mutableListOf<String>()
            for ((prefix, why) in forbiddenJarEntries) {
                var count = 0
                for (name in names) {
                    if (name.startsWith(prefix)) {
                        count++
                    }
                }
                if (count > 0) {
                    found.add("  " + prefix + " (" + count + " entries): " + why)
                }
            }
            if (found.isNotEmpty()) {
                throw GradleException("The jar carries packages it must not:\n" + found.joinToString("\n"))
            }
        }
    }

tasks.check { dependsOn(verifyJar) }
