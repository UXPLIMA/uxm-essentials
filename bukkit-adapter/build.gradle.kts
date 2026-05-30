plugins {
    id("uxmessentials.java-conventions")
    alias(libs.plugins.shadow)
    alias(libs.plugins.run.paper)
    alias(libs.plugins.minotaur)
    alias(libs.plugins.hangar.publish)
    // alias(libs.plugins.paperweight)  // uncomment only if you touch NMS
}

dependencies {
    implementation(project(":core"))
    implementation(project(":persistence-adapter"))
    api(project(":api"))

    compileOnly(libs.paper.api)
    compileOnly(libs.bundles.adventure) // Paper ships Adventure at runtime
    compileOnly(libs.luckperms.api) // optional soft-depend (Permissions port, ADR 0005)

    // Economy provider soft-depends — compileOnly: the outbound adapters bind to
    // these at runtime only if the plugin is present (ServicesManager, ADR 0004).
    compileOnly(libs.treasury.api)
    compileOnly(libs.vault.api)

    implementation(libs.bundles.configs)
    implementation(libs.bstats.bukkit)

    testImplementation(libs.mockbukkit)
    testImplementation(libs.archunit.junit)
}

// Locale catalogs live in a dedicated source set so they have their own
// resources output and can drive the locale-parity gate (docs/04-build.md §8.1).
sourceSets {
    create("messages") {
        resources.srcDir("src/messages/resources") // messages_en.conf, messages_de.conf, ...
    }
    // JMH benchmarks live in their own source set so they never ship in the jar
    // and never run during normal `check` (docs/04-build.md §8.2).
    create("jmh") {
        compileClasspath += sourceSets.main.get().output
        runtimeClasspath += sourceSets.main.get().output
    }
}

dependencies {
    "jmhImplementation"(libs.bundles.jmh)
    "jmhAnnotationProcessor"(libs.jmh.ap)
}

tasks.processResources {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filesMatching("paper-plugin.yml") { expand(props) }
    // Fold the message catalogs into the runtime jar.
    from(sourceSets["messages"].resources)
}

// Parity gate: every locale must declare exactly en's MessageKey set.
val localeParityCheck by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Fail if any messages_<lang>.conf is missing or has extra keys vs messages_en.conf."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.uxplima.uxmessentials.i18n.LocaleParityCheck")
    // The i18n catalog and its parity checker arrive with the message-catalog work;
    // until that class is on the classpath the gate has nothing to check, so it
    // self-skips rather than failing the build. It re-activates automatically the
    // moment LocaleParityCheck ships.
    onlyIf { task ->
        (task as JavaExec).classpath.any {
            it.isDirectory && File(it, "com/uxplima/uxmessentials/i18n/LocaleParityCheck.class").exists()
        }
    }
}
tasks.named("check") { dependsOn(localeParityCheck) }

val jmh by tasks.registering(JavaExec::class) {
    group = "benchmark"
    description = "Run JMH micro-benchmarks (baltop ordering, rtp safe-search, teleport resolution)."
    classpath = sourceSets["jmh"].runtimeClasspath
    mainClass.set("org.openjdk.jmh.Main")
    // Persist results for the perf-regression CI job to diff against the baseline.
    args("-rf", "json", "-rff", "build/reports/jmh/result.json")
}

tasks.shadowJar {
    archiveClassifier.set("")
    // Shade with relocation — see docs/04-build.md §16. Use a single per-plugin
    // namespace (`com.uxplima.uxmessentials.libs.<lib>`) so two plugins shading
    // the same library at different versions do not clash on the classpath. DO
    // NOT relocate Adventure / Kyori — Paper bundles them; relocating breaks
    // runtime symbol lookup.
    relocate("org.bstats", "com.uxplima.uxmessentials.libs.bstats")
    relocate("com.zaxxer.hikari", "com.uxplima.uxmessentials.libs.hikari")
    relocate("org.sqlite", "com.uxplima.uxmessentials.libs.sqlite")
    relocate("org.mariadb.jdbc", "com.uxplima.uxmessentials.libs.mariadb")
    relocate("org.postgresql", "com.uxplima.uxmessentials.libs.postgresql")
    relocate("org.flywaydb", "com.uxplima.uxmessentials.libs.flyway")
    relocate("org.jooq", "com.uxplima.uxmessentials.libs.jooq")
    relocate("com.github.benmanes.caffeine", "com.uxplima.uxmessentials.libs.caffeine")
    relocate("org.spongepowered.configurate", "com.uxplima.uxmessentials.libs.configurate")
    mergeServiceFiles()
    minimize {
        // bStats and Configurate use reflection / service loading the minimizer
        // can't see; never the JDBC drivers (loaded reflectively by Hikari).
        exclude(dependency("org.bstats:.*:.*"))
        exclude(dependency("org.spongepowered:.*:.*"))
        exclude(dependency("org.xerial:.*:.*"))
        exclude(dependency("org.mariadb.jdbc:.*:.*"))
        exclude(dependency("org.postgresql:.*:.*"))
    }
}

tasks.assemble { dependsOn(tasks.shadowJar) }

tasks.runServer {
    minecraftVersion(
        libs.versions.paper
            .get()
            .substringBefore("-"),
    )
    jvmArgs(
        "-Xmx4G",
        "-Djdk.tracePinnedThreads=full",
        "-XX:+UnlockExperimentalVMOptions",
        "-XX:+AllowEnhancedClassRedefinition",
    )
}
