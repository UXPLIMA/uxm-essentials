package com.uxplima.uxmessentials.discord;

import java.util.List;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver;

import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.Exclusion;
import org.eclipse.aether.repository.RemoteRepository;
import org.jspecify.annotations.NullMarked;

/**
 * Runtime classpath hook required by {@code paper-plugin.yml}'s {@code loader:} directive for discord-adapter.
 * Resolves JDA and configurate-hocon dynamically at boot.
 */
@NullMarked
public final class UxmDiscordLoader implements PluginLoader {

    @Override
    public void classloader(PluginClasspathBuilder classpath) {
        MavenLibraryResolver resolver = new MavenLibraryResolver();

        resolver.addRepository(new RemoteRepository.Builder(
                        "paper-central", "default", MavenLibraryResolver.MAVEN_CENTRAL_DEFAULT_MIRROR)
                .build());
        resolver.addRepository(new RemoteRepository.Builder(
                        "paper-public", "default", "https://repo.papermc.io/repository/maven-public/")
                .build());

        // Voice/opus is dead weight for a text-only bridge — drop the native codec so its
        // platform-specific binary never gets resolved onto the runtime classpath.
        Exclusion opus = new Exclusion("club.minnced", "opus-java", "*", "*");
        resolver.addDependency(
                new Dependency(new DefaultArtifact("net.dv8tion:JDA:5.6.1"), null, false, List.of(opus)));
        resolver.addDependency(new Dependency(new DefaultArtifact("org.spongepowered:configurate-hocon:4.1.2"), null));

        classpath.addLibrary(resolver);
    }
}
