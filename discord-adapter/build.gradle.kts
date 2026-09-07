import java.util.zip.ZipFile

plugins {
    id("uxmessentials.java-conventions")
    alias(libs.plugins.shadow)
}

// The optional Discord bridge (docs/09-deployment.md Path C). It is its own Paper
// plugin jar (uxmEssentials-discord) that consumes the host plugin's ports/events
// through Bukkit's ServicesManager: there is no compile-time link to :bukkit-adapter.
// JDA is the only backend. It is not shaded into the jar: the thin plugin jar declares
// JDA compileOnly and UxmDiscordLoader downloads it from Maven Central at boot via the
// paper-plugin.yml loader directive. opus-java is dropped at the loader level because the
// bridge only posts text and never touches voice.

dependencies {
    implementation(project(":core"))
    api(project(":api"))
    compileOnly(libs.paper.api)

    compileOnly(libs.jda)
    compileOnly(libs.bundles.configs)

    testImplementation(libs.bundles.testing)
    testImplementation(libs.paper.api)
    testImplementation(libs.jda)
    testImplementation(libs.bundles.configs)
}

tasks.processResources {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filesMatching("paper-plugin.yml") { expand(props) }
}

tasks.shadowJar {
    archiveBaseName.set("uxmEssentials-discord")
    archiveClassifier.set("")
    mergeServiceFiles()
}

tasks.assemble { dependsOn(tasks.shadowJar) }

// The bridge is a thin plugin jar. It carries its own classes, the :core and :api classes it shades, and
// nothing else. JDA is the point of the list: UxmDiscordLoader downloads it at boot, so the version an
// operator gets is the one the loader names, never one frozen into this jar.
val forbiddenJarEntries =
    listOf(
        "net/dv8tion/" to "JDA is downloaded at boot by UxmDiscordLoader. A shaded copy would freeze its version.",
        "okhttp3/" to "OkHttp arrives with JDA at boot, so it must not ride along here.",
        "com/neovisionaries/" to "The websocket client arrives with JDA at boot, so it must not ride along here.",
        "net/kyori/" to "Paper owns Adventure. A shaded copy breaks the server's own serializers.",
        "org/slf4j/" to "Paper owns slf4j. A second API with no binding behind it makes logging go quiet.",
        "org/bukkit/" to "The server API belongs to the server. It is compileOnly and is never shipped.",
        "io/papermc/" to "Paper's own classes come from the running server, never from a plugin jar.",
        "net/minecraft/" to "No jar of ours may carry the server. This bridge never touches NMS at all.",
        "com/uxplima/uxmlib/" to "uxmLib must be relocated. This entry is un-relocated.",
        "org/spongepowered/" to "Configurate is provisioned at boot by the loader, never shaded.",
        "com/typesafe/" to "Typesafe Config arrives with Configurate through that loader.",
        "io/leangen/" to "geantyref arrives with Configurate through that loader.",
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
