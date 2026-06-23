package com.uxplima.uxmessentials.warps.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.WarpEditorLayout;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.warps.adapter.inbound.gui.PlayerWarpRepositoryHandle;
import com.uxplima.uxmessentials.warps.adapter.inbound.gui.WarpEditorHolder;
import com.uxplima.uxmessentials.warps.adapter.inbound.gui.WarpEditorView;
import com.uxplima.uxmessentials.warps.application.port.WarpRepository;
import com.uxplima.uxmessentials.warps.domain.Warp;
import com.uxplima.uxmessentials.warps.domain.WarpName;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * Coverage that the warp editor's icon option reflects the warp's live display item. A warp with no icon set
 * falls back to {@code ENDER_PEARL} — the same default the browse menu renders — both as the option button's
 * material and in its "Current:" lore (never {@code item_frame} as a stand-in), and a warp with an explicit
 * icon shows that material instead. This mirrors how the kit editor's display-material option reads the live
 * material rather than a fixed placeholder.
 */
class WarpEditorIconOptionTest {

    private static final int ICON_SLOT = WarpEditorLayout.defaultLayout().iconSlot();

    private ServerMock server;
    private PlayerMock player;
    private PlayerRef viewer;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        MockBukkit.createMockPlugin();
        player = server.addPlayer("Alice");
        viewer = new PlayerRef(player.getUniqueId(), player.getName());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void unsetIconFallsBackToEnderPearlAndNamesIt() {
        ItemStack icon = iconItemFor(warpWithIcon(Optional.empty()));

        assertThat(icon.getType()).isEqualTo(Material.ENDER_PEARL);
        assertThat(loreText(icon)).contains("ender_pearl");
        assertThat(loreText(icon)).doesNotContain("None");
    }

    @Test
    void setIconShowsTheWarpsActualMaterialAndNamesIt() {
        ItemStack icon = iconItemFor(warpWithIcon(Optional.of("DIAMOND")));

        assertThat(icon.getType()).isEqualTo(Material.DIAMOND);
        assertThat(loreText(icon)).contains("diamond");
    }

    private ItemStack iconItemFor(Warp warp) {
        WarpEditorView view = new WarpEditorView(
                new EchoMessages(),
                new SyncScheduler(),
                new SingleWarpRepository(warp),
                WarpEditorLayout.defaultLayout(),
                new PlayerWarpRepositoryHandle());
        Inventory inventory =
                server.createInventory(null, WarpEditorLayout.defaultLayout().rows() * 9);
        WarpEditorHolder holder = new WarpEditorHolder(viewer, warp.name().value(), null);
        view.populate(inventory, holder);
        ItemStack icon = inventory.getItem(ICON_SLOT);
        assertThat(icon).as("icon option at slot %s", ICON_SLOT).isNotNull();
        return icon;
    }

    private static String loreText(ItemStack item) {
        List<Component> lore = item.lore();
        if (lore == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Component line : lore) {
            sb.append(PlainTextComponentSerializer.plainText().serialize(line)).append('\n');
        }
        return sb.toString();
    }

    private Warp warpWithIcon(Optional<String> icon) {
        WorldRef world = new WorldRef(UUID.randomUUID(), "world");
        PlayerRef owner = new PlayerRef(UUID.randomUUID(), "Owner");
        Warp base = Warp.create(WarpName.of("spawn"), Position.of(world, 0, 64, 0), owner, Instant.now());
        return icon.isPresent() ? base.withIconMaterial(icon) : base;
    }

    /** A repository holding exactly one server warp the editor reads. */
    private record SingleWarpRepository(Warp warp) implements WarpRepository {
        @Override
        public Optional<Warp> find(WarpName name) {
            return warp.name().equals(name) ? Optional.of(warp) : Optional.empty();
        }

        @Override
        public List<Warp> all() {
            return List.of(warp);
        }

        @Override
        public boolean exists(WarpName name) {
            return warp.name().equals(name);
        }

        @Override
        public void save(Warp warp) {}

        @Override
        public void delete(WarpName name) {}

        @Override
        public void rate(WarpName name, UUID player, double rating) {}

        @Override
        public double averageRating(WarpName name) {
            return 0.0;
        }
    }

    /** Substitutes placeholders into the key so the rendered text carries the material name for assertions. */
    private static final class EchoMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            String value = placeholders.getOrDefault("material", key.key());
            return value;
        }
    }

    private static final class SyncScheduler implements Scheduler {
        @Override
        public void onGlobal(Runnable task) {
            task.run();
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            task.run();
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            task.run();
        }

        @Override
        public void async(Runnable task) {
            task.run();
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            task.run();
        }
    }
}
