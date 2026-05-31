plugins {
    id("uxmessentials.java-conventions")
}

// The migration adapter is the seventh adapter module. It reads a foreign plugin's
// data tree (EssentialsX YAML for the v1 source), maps it through an Anti-Corruption
// Layer into the existing domain aggregates, and writes through the same application
// services live commands use. It depends on :core (the domain aggregates and the
// shared ports it drives) and :api (the published contracts); the Configurate YAML
// loader (which transitively pulls SnakeYAML) is a migration-only dependency and
// never reaches :core's classpath, exactly as the Bukkit fence keeps :core free of
// org.bukkit. Foreign-format parsing lives strictly here, never in :core.

dependencies {
    implementation(project(":core"))
    api(project(":api"))

    implementation(libs.configurate.yaml) // EssentialsX userdata/warps/kits YAML — adapter-only
    compileOnly(libs.slf4j.api)

    testImplementation(libs.jqwik)
    testImplementation(libs.configurate.hocon) // config-version ladder fixtures
}
