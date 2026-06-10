plugins {
    id("uxmessentials.java-conventions")
    alias(libs.plugins.shadow)
    alias(libs.plugins.run.paper)
    alias(libs.plugins.minotaur)
    alias(libs.plugins.hangar.publish)
    alias(libs.plugins.paperweight) // offline /invsee reads player-data NBT through Mojang-mapped NMS
}

dependencies {
    implementation(project(":core"))
    implementation(project(":persistence-adapter"))
    implementation(project(":migration"))
    api(project(":api"))

    // The Mojang-mapped dev bundle supplies the Paper API *and* the server internals (net.minecraft,
    // org.bukkit.craftbukkit) the offline-inventory adapter needs; it replaces the plain paper-api
    // compile dependency for the main source set. Paper's runtime plugin remapper maps the shipped
    // Mojang-mapped jar back to the server's mappings at load (see shadowJar manifest below).
    paperweight.paperDevBundle(libs.versions.paper.get())
    compileOnly(libs.bundles.adventure) // Paper ships Adventure at runtime
    compileOnly(libs.luckperms.api) // optional soft-depend (Permissions port, ADR 0005)

    // Economy provider soft-depends — compileOnly: the outbound adapters bind to
    // these at runtime only if the plugin is present (ServicesManager, ADR 0004).
    compileOnly(libs.treasury.api)
    compileOnly(libs.vault.api)

    // PlaceholderAPI soft-depend — compileOnly: the expansion and the message bridge
    // touch these symbols only past the plugin-present guard, so the plugin runs fully
    // without PlaceholderAPI installed.
    compileOnly(libs.placeholderapi)

    // Map-plugin marker soft-depends — compileOnly: the Dynmap/squaremap marker publishers touch these
    // symbols only past the plugin-present guard, so the plugin runs fully with neither map plugin installed.
    // Dynmap splits its surface: dynmap-api carries the DynmapAPI plugin handle, DynmapCoreAPI the markers package.
    compileOnly(libs.squaremap.api)
    compileOnly(libs.dynmap.api)
    compileOnly(libs.dynmap.core.api)

    compileOnly(libs.bundles.configs)
    implementation(libs.bstats.bukkit)

    // uxmLib GUI toolkit (dogfood) — consumed from mavenLocal; pulls uxmlib-item + uxmlib-common
    // transitively. Configurate is loaded at runtime via Paper library loader.
    implementation("com.uxplima.uxmlib:uxmlib-gui:0.1.0-SNAPSHOT") {
        exclude(group = "org.spongepowered")
    }
    // uxmLib HUD toolkit (dogfood) — Titles for the teleport arrival banner. Pulls uxmlib-common only.
    implementation("com.uxplima.uxmlib:uxmlib-hud:0.1.0-SNAPSHOT") {
        exclude(group = "org.spongepowered")
    }
    // uxmLib integration toolkit (dogfood) — native-Display holograms for the holograms context.
    implementation("com.uxplima.uxmlib:uxmlib-integration:0.1.0-SNAPSHOT") {
        exclude(group = "org.spongepowered")
    }

    testImplementation(libs.mockbukkit)
    testImplementation(libs.archunit.junit)
    testImplementation(libs.paper.api)
    testImplementation(libs.bundles.adventure)
    testImplementation(libs.bundles.configs)
    testImplementation(libs.bundles.db)
    testImplementation(libs.bundles.db.mysql)
    testImplementation(libs.bundles.db.pg)
    testImplementation(libs.caffeine)
    testImplementation(libs.gson)
    testImplementation(libs.jedis)
    testImplementation(libs.configurate.yaml)
}

// The Mojang-mapped dev bundle (declared above via paperDevBundle) is needed only to compile the
// offline-inventory NMS adapter. Keep it off the test classpath: MockBukkit drives the plugin against the plain
// Paper API, and the full server's PaperRegistryAccess static initializer throws if its classes leak onto the
// unit-test runtime. compileOnly alone is what the adapter needs — net.minecraft is provided by the live server,
// and Paper's runtime remapper maps the shipped Mojang-mapped jar at load (shadowJar manifest above).
paperweight {
    addServerDependencyTo.set(listOf(configurations.compileOnly.get()))
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
    // Fold the message catalogs into the runtime jar. The messages source set has its own
    // resources output that is also on the runtime classpath, so the same catalog file can arrive
    // from both inputs; keep the folded copy.
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    from(sourceSets["messages"].resources)
}

// Parity gate: every locale must declare exactly en's MessageKey set.
val localeParityCheck by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Fail if any messages_<lang>.conf is missing or has extra keys vs messages_en.conf."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.uxplima.uxmessentials.i18n.LocaleParityCheck")
    // P9 ships LocaleParityCheck and activates the gate (the onlyIf self-skip is gone). The checker
    // needs the message catalogs and the test classes compiled, so depend on the test compile and the
    // folded resources.
    dependsOn(tasks.named("compileTestJava"), tasks.processResources)
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
    archiveBaseName.set("uxmEssentials")
    archiveClassifier.set("")
    // The plugin is compiled and shipped Mojang-mapped; this tells Paper's runtime plugin remapper to
    // map it to the running server's mappings at load. Without it the NMS calls in the offline-inventory
    // adapter would miss at runtime. shadowJar builds its own manifest, so the attribute is set here too.
    manifest { attributes("paperweight-mappings-namespace" to "mojang") }
    // Shade with relocation — see docs/04-build.md §16. Use a single per-plugin
    // namespace (`com.uxplima.uxmessentials.libs.<lib>`) so two plugins shading
    // the same library at different versions do not clash on the classpath. DO
    // NOT relocate Adventure / Kyori — Paper bundles them; relocating breaks
    // runtime symbol lookup.
    relocate("org.bstats", "com.uxplima.uxmessentials.libs.bstats")
    // uxmLib (dogfood) — relocate into our per-plugin namespace so two plugins shading it cannot clash.
    relocate("com.uxplima.uxmlib", "com.uxplima.uxmessentials.libs.uxmlib")
    mergeServiceFiles()
    minimize {
        // bStats uses reflection / service loading the minimizer can't see.
        exclude(dependency("org.bstats:.*:.*"))
        // uxmLib uses reflection (Brigadier/registry/MiniMessage) + a GuiListener the minimizer can't
        // trace from the few entry points the adapter touches; keep its modules whole.
        exclude(dependency("com.uxplima.uxmlib:.*:.*"))
        // The persistence adapter is the API surface the feature contexts build on — the generated
        // jOOQ tables/records and the repository/transaction/cache bases must survive even before a
        // consuming context references them, so keep the whole module out of dead-code elimination.
        exclude(project(":persistence-adapter"))
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
    )
    // Live hot-swap of changed classes without a server restart needs the JetBrains Runtime
    // (DCEVM). On a stock JDK these options are unrecognised and abort the JVM at launch, so they
    // are opt-in: run with -Photswap under a JBR toolchain to enable them.
    if (providers.gradleProperty("hotswap").isPresent) {
        jvmArgs(
            "-XX:+UnlockExperimentalVMOptions",
            "-XX:+AllowEnhancedClassRedefinition",
        )
    }
}
