plugins {
    id("java-library")
    id("maven-publish")
}

// The developer API resolves from a static Maven repository we publish to GitHub Pages, so a release only
// has to copy a directory: there is no build-on-demand service in the path that can silently stop working.
// That is the lesson from the uxmLib coordinate, which was broken for two months because the build itself
// always consumed the library through includeBuild and never once resolved the published artifact.
// The sources jar comes from java-conventions' withSourcesJar().
publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "com.uxplima.uxmessentials"
            artifactId = "uxmessentials-${project.name}"
            from(components["java"])
            pom {
                name.set("uxmessentials-${project.name}")
                description.set("uxmEssentials developer API")
                url.set("https://docs.uxplima.com/minecraft/uxmessentials/developer/overview/")
                licenses {
                    license {
                        name.set("MIT")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
                developers {
                    developer {
                        id.set("uxplima")
                        name.set("UXPLIMA")
                    }
                }
                scm {
                    url.set("https://github.com/UXPLIMA/uxmEssentials")
                    connection.set("scm:git:https://github.com/UXPLIMA/uxmEssentials.git")
                }
            }
        }
    }
    repositories {
        // The release workflow builds this directory and uploads it whole; keeping it under the root build
        // directory means one upload carries every published module.
        maven {
            name = "localRepo"
            url = uri(rootProject.layout.buildDirectory.dir("maven-repo"))
        }
    }
}
