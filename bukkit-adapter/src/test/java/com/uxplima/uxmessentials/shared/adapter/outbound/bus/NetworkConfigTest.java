package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.uxplima.uxmessentials.shared.adapter.outbound.config.ConfigurateConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.LocalServer;
import com.uxplima.uxmessentials.shared.domain.ServerScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * This backend's network name is server-name, and a file that still says server-id is read the same way. The block
 * is read only from the root file, and names a scope cannot hold are reported rather than breaking the server.
 */
class NetworkConfigTest {

    private static final Logger QUIET = new Logger() {
        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    };

    @TempDir
    Path folder;

    private NetworkConfig read(String network) throws IOException {
        Path file = folder.resolve("config.conf");
        Files.writeString(file, "network {\n" + network + "\n}\n");
        return NetworkConfig.from(ConfigurateConfigStore.load(file, QUIET));
    }

    @Test
    void theServerNameIsRead() throws IOException {
        assertThat(read("server-name = \"survival-1\"").serverId()).isEqualTo("survival-1");
    }

    @Test
    void anOldFileThatSaysServerIdIsReadTheSameWay() throws IOException {
        assertThat(read("server-id = \"survival-2\"").serverId()).isEqualTo("survival-2");
    }

    @Test
    void aModulesOwnConfigIsRefusedSinceItHasNoNetworkBlock() throws IOException {
        Path file = folder.resolve("config.conf");
        Files.writeString(file, """
                network {
                  enabled = true
                  server-name = "lobby-1"
                }
                modules {
                  teleport {
                    enabled = true
                  }
                }
                """);
        ConfigurateConfigStore root = ConfigurateConfigStore.load(file, QUIET);

        assertThatThrownBy(() -> NetworkConfig.from(root.scoped("modules.teleport")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(NetworkConfig.from(root).localServer().serverScope()).contains(ServerScope.server("lobby-1"));
    }

    @Test
    void theSharedSetsAreReadAndANameNoScopeCanHoldIsDroppedWithAWarning() throws IOException {
        Path file = folder.resolve("config.conf");
        Files.writeString(file, "network {\n  shared-sets = [\"lobby\", \"bad set\", \"lobby\", \"hubs\"]\n}\n");
        List<String> problems = new ArrayList<>();

        NetworkConfig network = NetworkConfig.from(ConfigurateConfigStore.load(file, QUIET), problems::add);

        assertThat(network.sharedSets()).containsExactly("lobby", "hubs");
        assertThat(problems).singleElement().asString().contains("bad set");
    }

    @Test
    void aServerNameNoScopeCanHoldIsReportedAndLeavesTheServerWithoutAScopeOfItsOwn() throws IOException {
        Path file = folder.resolve("config.conf");
        Files.writeString(file, "network {\n  server-name = \"Lobby 1\"\n  shared-sets = [\"lobby\"]\n}\n");
        List<String> problems = new ArrayList<>();

        LocalServer local = NetworkConfig.from(ConfigurateConfigStore.load(file, QUIET), problems::add)
                .localServer();

        assertThat(problems).singleElement().asString().contains("Lobby 1");
        assertThat(local.serverScope()).isEmpty();
        assertThat(local.loadedScopes()).containsExactly(ServerScope.set("lobby"));
        assertThat(local.covers(null)).isTrue();
    }

    @Test
    void theNewNameWinsWhenAFileHasBoth() throws IOException {
        assertThat(read("server-name = \"new\"\n  server-id = \"old\"").serverId())
                .isEqualTo("new");
    }
}
