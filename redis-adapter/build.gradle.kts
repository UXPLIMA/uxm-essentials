import java.util.zip.ZipFile

plugins {
    id("uxmessentials.java-conventions")
    alias(libs.plugins.shadow)
}

// The optional Redis transport for the cross-server bus (docs/09-deployment.md). It is its own Paper plugin jar
// (uxmEssentials-redis), not a library: its RedisBusTransportAdapter implements the host jar's BusTransport, and
// that instance crosses into the host's bus core, so both sides must share the SAME BusTransport class. A library
// (or a jar shading its own core copy) would yield a loader-constraint LinkageError. So core and paper-api are
// compileOnly, never shaded, and the companion sees the host's copies at runtime through the join-classpath
// dependency declared in paper-plugin.yml. Only Lettuce (+ Netty/Reactor + uxmlib-redis) is shaded here.

dependencies {
    // The BusTransport SPI + the RedisTransportFactory this companion implements live in :core. compileOnly so
    // no second copy is shaded: at runtime the companion resolves them to the host jar's classes via the joined
    // classpath. Same reason paper-api is compileOnly, the host (and Paper) provide it.
    compileOnly(project(":core"))
    compileOnly(libs.paper.api)
    compileOnly(libs.jspecify)
    // The byte[] Redis pub/sub channel lives in uxmlib-redis (lean. No storage deps); this adapter provides
    // the Lettuce runtime it compiles against and ships it relocated below.
    implementation(libs.uxmlib.redis)
    implementation(libs.lettuce)

    testImplementation(project(":core"))
    testImplementation(libs.bundles.testing)
    testImplementation(libs.testcontainers)
    testImplementation(libs.tc.junit)
}

tasks.processResources {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filesMatching("paper-plugin.yml") { expand(props) }
}

tasks.shadowJar {
    archiveClassifier.set("")
    archiveBaseName.set("uxmEssentials-redis")
    // Lettuce drags in Netty + Reactor. Relocate them so this companion jar never clashes with Paper's own
    // (differently-versioned) bundled Netty when it lands on a backend classpath. Lettuce itself stays at
    // io.lettuce. Shadow rewrites Lettuce's internal references to the relocated Netty/Reactor packages
    // automatically. Use the same per-plugin namespace the main jar uses.
    relocate("io.netty", "com.uxplima.uxmessentials.libs.netty")
    relocate("reactor", "com.uxplima.uxmessentials.libs.reactor")
    relocate("org.reactivestreams", "com.uxplima.uxmessentials.libs.reactivestreams")
    // uxmlib-redis (and its uxmlib-common transitive) is shaded. Relocate it to the same coordinates the main
    // jar uses so this companion never clashes on the classes with another plugin that bundles uxmlib.
    relocate("com.uxplima.uxmlib", "com.uxplima.uxmessentials.libs.uxmlib")
    // Paper owns slf4j, and a second copy of the API with no binding behind it makes logging go quiet rather
    // than fail. Lettuce drags slf4j-api in, so it has to be dropped here. This is the same exclusion the
    // other plugins in the estate carry, and CONTRACT.md section 14 names slf4j directly.
    dependencies { exclude(dependency("org.slf4j:.*:.*")) }
    // Netty ships native-transport metadata + ServiceLoader files that must be merged, not dropped, or the
    // relocated classes fail to resolve at runtime.
    mergeServiceFiles()
}

tasks.assemble { dependsOn(tasks.shadowJar) }

// ShadowJarNettyRelocationTest inspects the built jar, so the jar must exist before the test task runs
// (during `check` as well as a bare `test`).
tasks.test { dependsOn(tasks.shadowJar) }

// This companion joins the host jar's classpath, so what it must not carry is decided twice over: by the
// relocations above, and by the LinkageError that a second copy of a shared class causes. The list agrees with
// ShadowJarNettyRelocationTest, which asserts the other half of the same rule: that the relocated copies are
// present, and that io/lettuce/ and our own redis package stay where they are.
val forbiddenJarEntries =
    listOf(
        "org/slf4j/" to "Paper owns slf4j. A second API with no binding makes logging go quiet.",
        "io/netty/" to "Netty is relocated on purpose. Bare, it clashes with Paper's own differently-versioned Netty.",
        "reactor/" to "Reactor is relocated with Netty, and clashes the same way when it is not.",
        "org/reactivestreams/" to "Reactive Streams is relocated with Reactor, under the same rule.",
        "com/uxplima/uxmlib/" to "uxmLib must be relocated. This entry is un-relocated.",
        "com/uxplima/uxmessentials/shared/" to "core is the host's. A second copy is a loader-constraint LinkageError.",
        "com/uxplima/uxmessentials/api/" to "The published API is the host's too. Shading it splits one class in two.",
        "org/bukkit/" to "The server API belongs to the server. It is compileOnly and is never shipped.",
        "io/papermc/" to "Paper's own classes come from the running server, never from a plugin jar.",
        "net/kyori/" to "Paper owns Adventure. A shaded copy breaks the server's own serializers.",
        "net/minecraft/" to "No jar of ours may carry the server. This transport never touches NMS at all.",
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
