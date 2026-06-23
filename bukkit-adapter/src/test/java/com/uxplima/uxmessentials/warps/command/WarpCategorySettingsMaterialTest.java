package com.uxplima.uxmessentials.warps.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.warps.adapter.inbound.gui.WarpCategorySettingsView;
import com.uxplima.uxmessentials.warps.domain.WarpCategory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The warp-category settings GUI's "display material" button must wear the category's configured material, not a
 * fixed stand-in: an operator who set the category to {@code SAND} should see a sand button, and a category with no
 * material set falls back to the same {@code BOOK} the lore line names. Pins the fix for the button that used to be
 * hardcoded to {@code GOLDEN_CHESTPLATE}.
 */
class WarpCategorySettingsMaterialTest {

    private static final int MATERIAL_SLOT = 12;

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
    void displayMaterialButtonWearsTheConfiguredMaterial() {
        ItemStack button = materialButtonFor(category(Optional.of("SAND")));

        assertThat(button.getType()).isEqualTo(Material.SAND);
    }

    @Test
    void unsetDisplayMaterialFallsBackToBook() {
        ItemStack button = materialButtonFor(category(Optional.empty()));

        assertThat(button.getType()).isEqualTo(Material.BOOK);
    }

    private ItemStack materialButtonFor(WarpCategory category) {
        WarpCategorySettingsView view = new WarpCategorySettingsView(new KeyMessages(), new SyncScheduler());
        view.open(player, viewer, category);
        ItemStack button = player.getOpenInventory().getTopInventory().getItem(MATERIAL_SLOT);
        assertThat(button).as("material button at slot %s", MATERIAL_SLOT).isNotNull();
        return button;
    }

    private static WarpCategory category(Optional<String> material) {
        return new WarpCategory("pvp", "PvP", material, List.of(), 0, Optional.empty());
    }

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
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
