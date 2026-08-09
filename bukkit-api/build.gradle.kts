plugins {
    id("uxmessentials.java-conventions")
    id("uxmessentials.publish-conventions")
}

dependencies {
    // The pure API travels with this artifact so a consumer needs a single coordinate: the event classes
    // here hand out the views declared in :api.
    api(project(":api"))
    compileOnly(libs.paper.api)
    compileOnly(libs.jspecify)
    // The front door hands out its write surface against a Plugin, so the tests need Paper on their classpath too.
    testImplementation(libs.paper.api)
}
