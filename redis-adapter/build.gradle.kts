plugins {
    id("uxm.paper-plugin")
}

uxplima {
    slug = "uxmessentials"
}

// The optional Redis transport for the cross-server bus. Its RedisBusTransportAdapter implements the host jar's
// BusTransport, and that instance crosses into the host's bus core, so both sides must share the same class. :core
// is therefore compileOnly and paper-plugin.yml joins the host's classpath. Only Lettuce, Netty, Reactor and
// uxmlib-redis are shaded here.
dependencies {
    compileOnly(project(":core"))
    implementation(uxm.uxmlib.redis)
    implementation(libs.lettuce)
}

tasks.shadowJar {
    archiveBaseName = "uxmEssentials-redis"
    // Lettuce drags in Netty and Reactor. Relocated, they never clash with Paper's own differently-versioned Netty.
    relocate("io.netty", "com.uxplima.uxmessentials.libs.netty")
    relocate("reactor", "com.uxplima.uxmessentials.libs.reactor")
    relocate("org.reactivestreams", "com.uxplima.uxmessentials.libs.reactivestreams")
}
