plugins {
    id("uxmessentials.java-conventions")
    alias(libs.plugins.shadow)
}

dependencies {
    // :core ships the BusTransport SPI + NetworkMessageCodec — pure Java, no Bukkit imports. The Redis
    // transport satisfies the same SPI the plugin-messaging transport does, reusing the identical binary codec
    // that lives above the seam in BusCore.
    implementation(project(":core"))
    // The byte[] Redis pub/sub channel lives in uxmlib-redis (lean — no storage deps); this adapter provides
    // the Lettuce runtime it compiles against and ships it relocated below.
    implementation(libs.uxmlib.redis)
    implementation(libs.lettuce)

    testImplementation(libs.bundles.testing)
    testImplementation(libs.testcontainers)
    testImplementation(libs.tc.junit)
}

tasks.shadowJar {
    archiveClassifier.set("")
    archiveBaseName.set("uxmEssentials-redis")
    // Lettuce drags in Netty + Reactor. Relocate them so this companion jar never clashes with Paper's own
    // (differently-versioned) bundled Netty when it lands on a backend classpath. Lettuce itself stays at
    // io.lettuce — Shadow rewrites Lettuce's internal references to the relocated Netty/Reactor packages
    // automatically. Use the same per-plugin namespace the main jar uses.
    relocate("io.netty", "com.uxplima.uxmessentials.libs.netty")
    relocate("reactor", "com.uxplima.uxmessentials.libs.reactor")
    relocate("org.reactivestreams", "com.uxplima.uxmessentials.libs.reactivestreams")
    // :core pulls uxmlib-redis (and its uxmlib-common transitive) into the shade. Relocate it to the same
    // coordinates the main jar uses so this companion never clashes on the classes with another plugin that
    // bundles uxmlib.
    relocate("com.uxplima.uxmlib", "com.uxplima.uxmessentials.libs.uxmlib")
    // Netty ships native-transport metadata + ServiceLoader files that must be merged, not dropped, or the
    // relocated classes fail to resolve at runtime.
    mergeServiceFiles()
}

tasks.assemble { dependsOn(tasks.shadowJar) }

// ShadowJarNettyRelocationTest inspects the built jar, so the jar must exist before the test task runs
// (during `check` as well as a bare `test`).
tasks.test { dependsOn(tasks.shadowJar) }
