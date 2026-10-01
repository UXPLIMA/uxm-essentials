package com.uxplima.uxmessentials.discord;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The bridge reads the host's {@code :api} classes, never a copy of its own.
 *
 * <p>Paper loads a plugin's own classes first. The bridge carried its own {@code :api}, so a service the host
 * registered was, to the bridge, a different class with the same name and the lookup came back empty: that is how
 * {@code /link} stayed dead until linking left the plugin, and the audit feed would go the same way. The jar no
 * longer carries {@code :api} (the build refuses one), and the manifest joins
 * the host's classpath, so the one copy is the host's. {@code load: BEFORE} names when the host loads relative to
 * the bridge: a boot on 2026-09-29 loaded a plugin declaring {@code AFTER} ahead of the host.
 */
class BridgeSharesTheHostClassesTest {

    @Test
    @DisplayName("the manifest joins the host's classpath, loads the host first, and still runs without it")
    void theManifestJoinsTheHost() throws IOException {
        ConfigurationSection host;
        try (InputStream in = Objects.requireNonNull(
                getClass().getClassLoader().getResourceAsStream("paper-plugin.yml"), "paper-plugin.yml")) {
            YamlConfiguration manifest =
                    YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
            host = manifest.getConfigurationSection("dependencies.server.uxmEssentials");
        }

        assertThat(host).isNotNull();
        assertThat(host.getBoolean("join-classpath")).isTrue();
        assertThat(host.getString("load")).isEqualTo("BEFORE");
        assertThat(host.getBoolean("required", true)).isFalse();
    }
}
