plugins {
    id("uxm.paper-plugin")
    // Offline /invsee reads player-data NBT through Mojang-mapped server internals.
    id("io.papermc.paperweight.userdev")
}

uxplima {
    slug = "uxmessentials"
}

repositories {
    maven("https://repo.codemc.org/repository/maven-public/") // Treasury
    maven("https://jitpack.io") // Vault, Lands, GriefPrevention, SimpleClaimSystem, RClaim
    maven("https://repo.extendedclip.com/releases/") // PlaceholderAPI
    maven("https://repo.mikeprimm.com/") // Dynmap
    maven("https://repo.opencollab.dev/main/") // Floodgate and Cumulus
}

dependencies {
    implementation(project(":core"))
    implementation(project(":persistence-adapter"))
    implementation(project(":migration"))
    api(project(":api"))
    api(project(":bukkit-api"))

    // The Mojang-mapped dev bundle supplies the Paper API and the server internals (net.minecraft,
    // org.bukkit.craftbukkit) the offline-inventory adapter needs. Paper's plugin remapper maps the shipped jar back
    // to the server's mappings at load.
    paperweight.paperDevBundle(uxm.versions.paper.get())
    compileOnly(libs.bundles.adventure)
    compileOnly(libs.luckperms.api)

    // Soft dependencies. The adapters bind to these only past a plugin-present guard.
    compileOnly(libs.treasury.api)
    compileOnly(libs.vault.api)
    compileOnly(libs.hook.lands)
    compileOnly(libs.hook.griefprevention)
    compileOnly(libs.hook.simpleclaimsystem)
    compileOnly(libs.hook.rclaim)
    compileOnly(uxm.placeholderapi)
    compileOnly(libs.miniplaceholders)
    compileOnly(libs.squaremap.api)
    compileOnly(libs.dynmap.api)
    compileOnly(libs.dynmap.core.api)
    compileOnly(libs.floodgate.api)

    // Provisioned by UxmEssentialsLoader at boot, never shaded.
    compileOnly(libs.configurate.yaml)
    compileOnly(libs.gson)
    compileOnly(libs.bundles.db)
    compileOnly(libs.bundles.db.mysql)
    compileOnly(libs.bundles.db.pg)

    // Shaded and relocated.
    implementation(uxm.configurate.hocon)
    implementation(libs.caffeine)
    implementation(libs.hikari)
    implementation(uxm.bstats.bukkit)
    implementation(uxm.uxmlib.common)
    implementation(uxm.uxmlib.command)
    implementation(uxm.uxmlib.gui)
    implementation(uxm.uxmlib.bedrock)
    implementation(uxm.uxmlib.item)
    implementation(uxm.uxmlib.menu)
    implementation(uxm.uxmlib.hud)
    implementation(uxm.uxmlib.condition)
    implementation(uxm.uxmlib.integration)
    implementation(uxm.uxmlib.nametags)
    implementation(uxm.uxmlib.packet)
    implementation(uxm.uxmlib.pipeline)
    implementation(uxm.uxmlib.update)
    // The shared leaderboards, over the same uxmLib storage persistence-adapter brings in. Neither pulls its own
    // drivers or pool: the server's library loader supplies those.
    implementation(uxm.uxmlib.board) { isTransitive = false }
    implementation(uxm.uxmlib.storage) { isTransitive = false }
}

// The dev bundle is needed only to compile the offline-inventory adapter against server internals.
paperweight {
    addServerDependencyTo.set(listOf(configurations.compileOnly.get()))
}

tasks.shadowJar {
    archiveBaseName = "uxmEssentials"
    // Tells Paper's plugin remapper to map this Mojang-mapped jar to the running server's mappings at load.
    manifest { attributes("paperweight-mappings-namespace" to "mojang") }
}

tasks.runServer {
    jvmArgs("-Xmx4G")
}
