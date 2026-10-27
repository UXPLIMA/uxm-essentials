import org.jooq.meta.jaxb.Logging

plugins {
    id("uxm.java")
    alias(libs.plugins.jooq.codegen)
}

// jOOQ generates its classes by parsing the Flyway migrations through the
// DDLDatabase: no live database is contacted at build time. It reads every
// V*.sql under db/migration in version order, so the generated sources describe
// the same tables the migrations create at runtime and the typed DSL can never
// drift from the actual schema. SQLite is the default backend; the DDLDatabase
// parses against the portable subset MySQL/MariaDB and PostgreSQL also accept.

dependencies {
    implementation(project(":core"))
    api(project(":api"))

    compileOnly(libs.bundles.db) // Hikari, SQLite, Flyway, jOOQ: the default backend
    compileOnly(libs.bundles.db.mysql) // MySQL and MariaDB drivers, activated by modules.conf
    compileOnly(libs.bundles.db.pg) // PostgreSQL driver, activated by modules.conf
    compileOnly(libs.caffeine)
    compileOnly(libs.gson)
    compileOnly(libs.slf4j.api)

    // jOOQ's render settings carry XML binding annotations. The compiler reads them and warns, under -Werror,
    // that the annotation types are missing. Nothing runs them.
    compileOnly(libs.jakarta.xml.bind)

    // The code generator parses the DDL through jOOQ's meta-extensions DDLDatabase, on the codegen classpath only.
    jooqCodegen(libs.jooq.meta.ext)

    // The copy of a SQLite file's rows into a network database is the library's.
    implementation(uxm.uxmlib.storage) { isTransitive = false }
}

jooq {
    configuration {
        logging = Logging.WARN
        generator {
            database {
                name = "org.jooq.meta.extensions.ddl.DDLDatabase"
                properties {
                    // The migrations write every name under the ${prefix} placeholder that Flyway fills in at
                    // startup. The parser reads a copy with the placeholder taken out, so the generated names are
                    // the bare ones and the prefix is added when a query is rendered.
                    property {
                        key = "scripts"
                        value = layout.buildDirectory
                            .dir("ddl")
                            .get()
                            .asFile.path + "/*.sql"
                    }
                    // Parse the scripts the way Flyway lays them out: numbered files
                    // applied in version order, semicolon-delimited statements. The glob
                    // picks up every V*.sql, so a new context's migration regenerates its
                    // tables without touching this build.
                    property {
                        key = "sort"
                        value = "flyway"
                    }
                    // Force lowercase generated identifiers. The migrations declare tables/columns
                    // unquoted in lowercase, but the jOOQ DDL parser folds unquoted names to UPPER
                    // which `as_is` then preserves. SQLite (default) and case-insensitive MySQL images
                    // tolerate the mismatch, but a real case-sensitive MySQL/Linux (lower_case_table_names=0)
                    // has lowercase tables and rejects the uppercased names ("table doesn't exist").
                    // Lowercasing the generated names matches the migrations on every backend.
                    property {
                        key = "defaultNameCase"
                        value = "lower"
                    }
                }
            }
            generate {
                isRecords = true
                isFluentSetters = true
                isJavaTimeTypes = true
            }
            target {
                packageName = "com.uxplima.uxmessentials.persistence.jooq"
                directory =
                    layout.buildDirectory
                        .dir("generated-src/jooq/main")
                        .get()
                        .asFile.path
            }
        }
    }
}

// Make the generated sources part of the main compilation and order codegen first.
sourceSets {
    main {
        java {
            srcDir(layout.buildDirectory.dir("generated-src/jooq/main"))
        }
    }
}

val bareMigrations =
    tasks.register<Sync>("bareMigrations") {
        from("src/main/resources/db/migration")
        include("*.sql")
        into(layout.buildDirectory.dir("ddl"))
        filter { line -> line.replace("\${prefix}", "") }
    }

tasks.named("jooqCodegen") {
    dependsOn(bareMigrations)
    inputs.files(bareMigrations)
}

tasks.named<JavaCompile>("compileJava") {
    dependsOn(tasks.named("jooqCodegen"))
}

// The generated jOOQ classes are not hand-written: keep them out of Spotless so
// the formatter does not fight the generator. The convention plugin already
// disables Error Prone warnings in generated code; this excludes the generated
// directory from the formatter too.
spotless {
    java {
        targetExclude("build/generated-src/**")
    }
}
