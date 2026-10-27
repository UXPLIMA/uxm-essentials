plugins {
    id("uxm.java")
}

dependencies {
    implementation(project(":core"))
    api(project(":api"))

    // The foreign plugin's YAML and its LiteBans JDBC source are read here only. bukkit-adapter provides them.
    compileOnly(libs.configurate.yaml)
    compileOnly(libs.slf4j.api)
    compileOnly(libs.h2)
}
