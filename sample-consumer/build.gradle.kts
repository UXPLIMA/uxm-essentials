plugins {
    id("java")
}

// Where the API comes from. The default is the repository the release workflow uploads, which is what a real
// consumer uses. CI overrides it with the local one the current tree publishes, so a coordinate or a POM that
// stopped working fails on the commit that broke it rather than on the next release.
val uxmRepo: String = (project.findProperty("uxmRepo") as String?)
        ?: "https://raw.githubusercontent.com/UXPLIMA/uxmEssentials/maven"

// Which version to resolve. A release pins this; CI passes the version the tree currently builds.
val uxmVersion: String = (project.findProperty("uxmVersion") as String?) ?: "0.8.0"

repositories {
    maven(uxmRepo) { name = "uxmEssentials" }
    maven("https://repo.papermc.io/repository/maven-public/") { name = "papermc" }
    mavenCentral()
}

dependencies {
    // One coordinate. The pure view types in uxmessentials-api arrive with it, through the POM, which is
    // exactly the arrangement this build exists to prove.
    compileOnly("com.uxplima.uxmessentials:uxmessentials-bukkit-api:$uxmVersion")
    compileOnly("io.papermc.paper:paper-api:26.2.build.112-stable")
}

java {
    toolchain { languageVersion = JavaLanguageVersion.of(25) }
}

tasks.withType<JavaCompile>().configureEach {
    // The published API is built for the same Java the server runs, so a consumer targets it too.
    options.release = 25
    options.encoding = "UTF-8"
}

// Prints what actually resolved, so a CI log shows the coordinate and the version rather than only a green tick.
tasks.register("showResolvedApi") {
    val classpath = configurations.compileClasspath
    doLast {
        classpath.get().resolvedConfiguration.resolvedArtifacts
                .filter { it.moduleVersion.id.group == "com.uxplima.uxmessentials" }
                .forEach { println("resolved ${it.moduleVersion.id} from ${it.file.name}") }
    }
}
