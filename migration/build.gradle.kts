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

    // The tests parse EssentialsX fixtures, so they need the YAML reader the server would otherwise provide.
    testImplementation(libs.configurate.yaml)
    // The JDBC sources are read from real H2 (LiteBans, AxPlayerWarps) and SQLite (Olzie) fixture databases.
    testImplementation(libs.h2)
    testImplementation(libs.sqlite.jdbc)
}

// sqlite-jdbc loads its native library through System::load, which JDK 24 and later report as a restricted
// method call unless the test JVM grants native access.
tasks.named<Test>("test") {
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}
