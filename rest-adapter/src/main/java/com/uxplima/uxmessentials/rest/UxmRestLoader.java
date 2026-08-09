package com.uxplima.uxmessentials.rest;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver;

import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;
import org.jspecify.annotations.NullMarked;

/**
 * The runtime classpath this add-on needs, resolved at boot by Paper's library manager.
 *
 * <p>Two libraries and nothing else: gson to read and write the JSON that is every request and every answer, and
 * configurate-hocon to read {@code rest.conf}. Both are already resolved by the host's own loader, and Paper's
 * library manager downloads a coordinate once, so naming them here costs nothing at boot and means this jar keeps
 * working if the host ever stops needing one of them.
 *
 * <p>The coordinates are written out rather than read from the host's shared pin, because a {@code PluginLoader}
 * runs before any plugin classloader exists and cannot borrow a class from another plugin. A test compares these
 * strings to the shared pin, so the two cannot drift apart quietly.
 */
@NullMarked
public final class UxmRestLoader implements PluginLoader {

    /** Kept in step with the host's pin by {@code LoaderPinDriftTest}. */
    public static final String GSON = "com.google.code.gson:gson:2.11.0";

    /** Kept in step with the host's pin by {@code LoaderPinDriftTest}. */
    public static final String CONFIGURATE_HOCON = "org.spongepowered:configurate-hocon:4.1.2";

    @Override
    public void classloader(PluginClasspathBuilder classpath) {
        MavenLibraryResolver resolver = new MavenLibraryResolver();
        resolver.addRepository(new RemoteRepository.Builder(
                        "paper-central", "default", MavenLibraryResolver.MAVEN_CENTRAL_DEFAULT_MIRROR)
                .build());
        resolver.addDependency(new Dependency(new DefaultArtifact(GSON), null));
        resolver.addDependency(new Dependency(new DefaultArtifact(CONFIGURATE_HOCON), null));
        classpath.addLibrary(resolver);
    }
}
