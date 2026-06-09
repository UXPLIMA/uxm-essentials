package com.uxplima.uxmessentials.bootstrap;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver;

import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;
import org.jspecify.annotations.NullMarked;

/**
 * Runtime classpath hook required by {@code paper-plugin.yml}'s {@code loader:} directive.
 * Resolves and downloads third-party dependencies from Maven Central dynamically at boot.
 */
@NullMarked
public final class UxmEssentialsLoader implements PluginLoader {

    @Override
    public void classloader(PluginClasspathBuilder classpath) {
        MavenLibraryResolver resolver = new MavenLibraryResolver();

        resolver.addRepository(new RemoteRepository.Builder(
                        "paper-central", "default", MavenLibraryResolver.MAVEN_CENTRAL_DEFAULT_MIRROR)
                .build());
        resolver.addRepository(new RemoteRepository.Builder(
                        "paper-public", "default", "https://repo.papermc.io/repository/maven-public/")
                .build());

        resolver.addDependency(new Dependency(new DefaultArtifact("com.zaxxer:HikariCP:6.2.1"), null));
        resolver.addDependency(new Dependency(new DefaultArtifact("org.xerial:sqlite-jdbc:3.49.1.0"), null));
        resolver.addDependency(new Dependency(new DefaultArtifact("org.mariadb.jdbc:mariadb-java-client:3.5.3"), null));
        resolver.addDependency(new Dependency(new DefaultArtifact("org.postgresql:postgresql:42.7.4"), null));
        resolver.addDependency(new Dependency(new DefaultArtifact("org.flywaydb:flyway-core:10.19.0"), null));
        resolver.addDependency(
                new Dependency(new DefaultArtifact("org.flywaydb:flyway-database-postgresql:10.19.0"), null));
        resolver.addDependency(new Dependency(new DefaultArtifact("org.flywaydb:flyway-mysql:10.19.0"), null));
        resolver.addDependency(new Dependency(new DefaultArtifact("org.jooq:jooq:3.19.16"), null));
        resolver.addDependency(
                new Dependency(new DefaultArtifact("com.github.ben-manes.caffeine:caffeine:3.1.8"), null));
        resolver.addDependency(new Dependency(new DefaultArtifact("org.spongepowered:configurate-hocon:4.1.2"), null));
        resolver.addDependency(new Dependency(new DefaultArtifact("org.spongepowered:configurate-yaml:4.1.2"), null));
        resolver.addDependency(new Dependency(new DefaultArtifact("redis.clients:jedis:5.1.0"), null));
        resolver.addDependency(new Dependency(new DefaultArtifact("com.google.code.gson:gson:2.11.0"), null));

        classpath.addLibrary(resolver);
    }
}
