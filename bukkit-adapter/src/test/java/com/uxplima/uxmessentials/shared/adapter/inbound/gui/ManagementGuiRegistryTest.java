package com.uxplima.uxmessentials.shared.adapter.inbound.gui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.UUID;

import org.bukkit.Material;

import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.Permissions.QuotaFamily;
import com.uxplima.uxmessentials.shared.application.port.Permissions.QuotaResult;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.shared.domain.message.GuiMessageKey;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** The hub shows a viewer the tiles they hold the node for, in the order the modules registered them. */
class ManagementGuiRegistryTest {

    private static final PlayerRef VIEWER =
            new PlayerRef(UUID.fromString("00000000-0000-0000-0000-00000000005a"), "Staff");

    private static ManagementGuiEntry tile(String id, String node) {
        return new ManagementGuiEntry(id, GuiMessageKey.HUB_TITLE, Material.CHEST, node, (player, viewer) -> {});
    }

    @Test
    void aViewerSeesOnlyTheTilesTheyHoldTheNodeFor() {
        ManagementGuiRegistry registry = new ManagementGuiRegistry();
        registry.register(tile("invrollback", "node.restore"));
        registry.register(tile("trade", "node.trade"));
        registry.register(tile("villagers", "node.manager"));

        assertThat(registry.entriesFor(VIEWER, new Granted(Set.of("node.trade", "node.manager"))))
                .extracting(ManagementGuiEntry::id)
                .containsExactly("trade", "villagers");
    }

    @Test
    void tilesKeepTheOrderTheModulesRegisteredThem() {
        ManagementGuiRegistry registry = new ManagementGuiRegistry();
        registry.register(tile("playerstate-invsee", "node"));
        registry.register(tile("playerstate-endersee", "node"));
        registry.register(tile("playerstate-playtime", "node"));

        assertThat(registry.entriesFor(VIEWER, new Granted(Set.of("node"))))
                .extracting(ManagementGuiEntry::id)
                .containsExactly("playerstate-invsee", "playerstate-endersee", "playerstate-playtime");
    }

    @Test
    void aModuleWiredAgainReplacesItsTileInsteadOfAddingASecond() {
        ManagementGuiRegistry registry = new ManagementGuiRegistry();
        registry.register(tile("trade", "node.old"));
        registry.register(tile("trade", "node.new"));

        assertThat(registry.entries()).hasSize(1);
        assertThat(registry.entriesFor(VIEWER, new Granted(Set.of("node.old")))).isEmpty();
        assertThat(registry.entriesFor(VIEWER, new Granted(Set.of("node.new"))))
                .extracting(ManagementGuiEntry::id)
                .containsExactly("trade");
    }

    /** Every viewer holds exactly the given nodes. */
    private record Granted(Set<String> nodes) implements Permissions {

        @Override
        public boolean has(PlayerRef who, String node) {
            return nodes.contains(node);
        }

        @Override
        public QuotaResult resolveQuota(
                PlayerRef who, QuotaFamily family, @Nullable WorldRef world, long configDefault) {
            return QuotaResult.limited(configDefault);
        }
    }
}
