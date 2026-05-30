plugins { id("uxmessentials.java-conventions") }

// P0 stub: this module compiles and pulls in the persistence stack, but jOOQ
// codegen is intentionally not enabled yet — there is no DB schema until a later
// phase. The generated sources, the jooq-codegen-gradle plugin, and the Flyway
// migration wiring land when the first bounded context's tables are defined.

dependencies {
    implementation(project(":core"))
    api(project(":api"))

    implementation(libs.bundles.db) // Hikari + SQLite + Flyway + jOOQ (default backend)
    implementation(libs.bundles.db.mysql) // MySQL/MariaDB driver — activated via modules.conf
    implementation(libs.bundles.db.pg) // PostgreSQL driver — activated via modules.conf
    implementation(libs.caffeine)
    compileOnly(libs.slf4j.api)

    testImplementation(libs.tc.junit)
    testImplementation(libs.tc.postgres) // network-backend integration tests
    testImplementation(libs.tc.mysql) // network-backend integration tests
    // SQLite needs no Testcontainer — the embedded file db runs in-process.
}
