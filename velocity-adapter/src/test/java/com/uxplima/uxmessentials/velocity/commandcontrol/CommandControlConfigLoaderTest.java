package com.uxplima.uxmessentials.velocity.commandcontrol;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import com.uxplima.uxmessentials.commandcontrol.application.CommandControlConfig;
import com.uxplima.uxmessentials.commandcontrol.domain.RuleMode;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.spongepowered.configurate.ConfigurationNode;

/**
 * Verifies the proxy config pipeline: the bundled default extracts on first run and, wrapped by
 * {@link NodeConfigStore} and scoped to {@code command-control}, feeds the {@code :core}
 * {@link CommandControlConfig} parser so the proxy reuses the backend's config shape verbatim. The shipped
 * default is off (opt-in) with the proxy-native hidden-command list; an edited file flips it on.
 */
class CommandControlConfigLoaderTest {

    private static CommandControlConfig read(Path dataDirectory) throws Exception {
        ConfigurationNode root = CommandControlConfigLoader.load(dataDirectory);
        ConfigStore store = new NodeConfigStore(root).scoped("command-control");
        return CommandControlConfig.from(store);
    }

    @Test
    void firstLoadExtractsTheOptInDefault(@TempDir Path dataDirectory) throws Exception {
        CommandControlConfig config = read(dataDirectory);

        assertThat(Files.exists(dataDirectory.resolve("config.conf"))).isTrue();
        assertThat(config.enabled()).isFalse();
        assertThat(config.mode()).isEqualTo(RuleMode.BLACKLIST);
        assertThat(config.hiddenCommands()).contains("server", "glist", "send", "find");
    }

    @Test
    void editedFileEnablesAWhitelist(@TempDir Path dataDirectory) throws Exception {
        Files.writeString(dataDirectory.resolve("config.conf"), """
                command-control {
                  enabled = true
                  mode = whitelist
                  commands { default = [ "server" ] }
                  command-spam { enabled = true, max-per-window = 5, window-seconds = 3, action = kick }
                }
                """);

        CommandControlConfig config = read(dataDirectory);

        assertThat(config.enabled()).isTrue();
        assertThat(config.mode()).isEqualTo(RuleMode.WHITELIST);
        assertThat(config.defaultCommands()).containsExactly("server");
        assertThat(config.toRateLimiter().isEnabled()).isTrue();
    }
}
