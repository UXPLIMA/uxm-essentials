plugins {
    id("uxm.java")
}

dependencies {
    // The event classes hand out the views declared in :api, so a consumer needs one coordinate.
    api(project(":api"))
    compileOnly(uxm.paper.api)
}
