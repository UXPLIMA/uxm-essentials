// Re-import the root version catalog so `libs.*` works inside the precompiled
// script plugin. Without this, `the<LibrariesForLibs>()` is unresolved.
dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}
