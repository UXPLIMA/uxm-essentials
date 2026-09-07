package com.uxplima.uxmessentials.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.uxplima.uxmessentials.shared.adapter.outbound.integration.Integration;
import com.uxplima.uxmessentials.shared.adapter.outbound.integration.IntegrationCatalog;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * Keeps the integration catalog and the plugin manifest in exact bijection, in both directions.
 *
 * <p><strong>The bug this freezes out.</strong> Two lists of the same integrations drift, and each direction of
 * drift has its own failure. A plugin integrated in code but missing from {@code paper-plugin.yml} carries no
 * {@code load: BEFORE}, so it may enable after us and the present-guard reads it as absent: the integration is
 * silently dead on exactly the servers that installed the plugin for it. A plugin declared in the manifest with
 * no integration behind it is the reverse lie, telling an operator we support something we never call, and it
 * survives indefinitely because nothing fails when dead code is deleted and its declaration is left behind.
 * Both had happened before this guard existed: MiniPlaceholders and WorldEdit were undeclared, and NBTAPI was
 * declared for an integration with no consumer.
 *
 * <p><strong>The invariant.</strong> Every {@link IntegrationCatalog} entry is declared under
 * {@code dependencies.server}, every declared dependency is catalogued, and every catalogued seam names a file
 * that exists in production sources.
 *
 * <p><strong>Honest limits.</strong> The seam check proves the named file exists, not that it is the class that
 * owns the guard. {@code SoftDependSeamDriftTest} owns that half: one plugin, one probing file.
 *
 * <p>Each real-corpus assertion is paired with a teeth test firing the same detector on synthetic input, so a
 * green result is a checked fact rather than a vacuous pass.
 */
class IntegrationCatalogDriftTest {

    @Test
    void everyCatalogedIntegrationIsDeclaredInTheManifest() {
        assertThat(missingFrom(IntegrationCatalog.plugins(), declaredServerDependencies()))
                .as("an integration with no paper-plugin.yml entry has no 'load: BEFORE' ordering, so on a server "
                        + "that installs the plugin it may enable after us and the present-guard reads it as absent; "
                        + "declare it under dependencies.server")
                .isEmpty();
    }

    @Test
    void everyDeclaredDependencyIsCataloged() {
        assertThat(missingFrom(declaredServerDependencies(), new HashSet<>(IntegrationCatalog.plugins())))
                .as("a soft-depend is declared for a plugin the catalog does not know: either the integration was "
                        + "deleted and its declaration left behind (remove the manifest entry) or a new integration "
                        + "skipped the catalog (add it to IntegrationCatalog)")
                .isEmpty();
    }

    @Test
    void everyCatalogedSeamNamesAClassThatResolves() {
        List<String> missing = new ArrayList<>();
        for (Integration integration : IntegrationCatalog.all()) {
            if (!resolves(integration.seam())) {
                missing.add(integration.plugin() + " -> " + integration.seam());
            }
        }
        assertThat(missing)
                .as("a catalogued seam names a class that is not on the classpath; point the entry at the class "
                        + "that owns the present-guard today, or retire the entry with the integration")
                .isEmpty();
    }

    /** Whether {@code className} is on this module's compile classpath, wherever the module that carries it lives. */
    private static boolean resolves(String className) {
        try {
            Class.forName(className, false, IntegrationCatalogDriftTest.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError absent) {
            return false;
        }
    }

    @Test
    void theManifestGuardFiresOnAnUndeclaredIntegration() {
        assertThat(missingFrom(List.of("GhostPlugin"), declaredServerDependencies()))
                .containsExactly("GhostPlugin");
    }

    @Test
    void theCatalogGuardFiresOnAnUncatalogedDependency() {
        assertThat(missingFrom(Set.of("GhostPlugin"), new HashSet<>(IntegrationCatalog.plugins())))
                .containsExactly("GhostPlugin");
    }

    @Test
    void theSeamGuardFiresOnAMissingClass() {
        assertThat(resolves("com.uxplima.uxmessentials.GhostIntegrationSeam"))
                .as("a class that was never written must not resolve, or the seam check proves nothing")
                .isFalse();
    }

    /** The names in {@code required} that are absent from {@code declared}. The whole bijection detector. */
    private static List<String> missingFrom(Collection<String> required, Set<String> declared) {
        List<String> missing = new ArrayList<>();
        for (String name : required) {
            if (!declared.contains(name)) {
                missing.add(name);
            }
        }
        return missing;
    }

    /** The {@code dependencies.server} plugin names declared in the shipped {@code paper-plugin.yml}. */
    private static Set<String> declaredServerDependencies() {
        Map<?, ?> root = (Map<?, ?>) new Yaml().load(resource("/paper-plugin.yml"));
        Map<?, ?> dependencies = (Map<?, ?>) root.get("dependencies");
        Map<?, ?> server = (Map<?, ?>) dependencies.get("server");
        Set<String> names = new HashSet<>();
        for (Object key : server.keySet()) {
            names.add(String.valueOf(key));
        }
        return names;
    }

    private static String resource(String path) {
        try (InputStream in = IntegrationCatalogDriftTest.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("missing tracked resource on the classpath: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
