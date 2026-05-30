plugins { id("uxmessentials.java-conventions") }

dependencies {
    compileOnly(libs.jspecify)
    // :api has no other runtime dependencies.
}
