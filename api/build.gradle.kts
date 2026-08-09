plugins {
    id("uxmessentials.java-conventions")
    id("uxmessentials.publish-conventions")
}

dependencies {
    compileOnly(libs.jspecify)
    // :api has no other runtime dependencies.
}
