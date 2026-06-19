package com.uxplima.uxmessentials.worlds.adapter.inbound.gui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/**
 * Coverage of the world-editor inventory holder: it round-trips the viewer, screen, world and page it was built
 * with, answers {@link WorldEditorHolder#getInventory()} only once a menu has been attached, and rejects a null
 * viewer or screen. A LIST holder carries no world, so its {@code world()} accessor reports {@code null}.
 */
class WorldEditorHolderTest {

    private ServerMock server;
    private PlayerRef viewer;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        viewer = new PlayerRef(UUID.randomUUID(), "Alice");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void accessorsRoundTripTheConstructorArguments() {
        WorldName world = WorldName.of("world_nether");
        WorldEditorHolder holder = new WorldEditorHolder(viewer, WorldEditorScreen.RULES, world, 2);
        assertThat(holder.viewer()).isEqualTo(viewer);
        assertThat(holder.screen()).isEqualTo(WorldEditorScreen.RULES);
        assertThat(holder.world()).isEqualTo(world);
        assertThat(holder.page()).isEqualTo(2);
    }

    @Test
    void listHolderHasNoWorld() {
        WorldEditorHolder holder = new WorldEditorHolder(viewer, WorldEditorScreen.LIST, null, 0);
        assertThat(holder.world()).isNull();
    }

    @Test
    void getInventoryThrowsBeforeAttach() {
        WorldEditorHolder holder = new WorldEditorHolder(viewer, WorldEditorScreen.MAIN, WorldName.of("world"), 0);
        assertThatThrownBy(holder::getInventory).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void getInventoryReturnsTheAttachedInventory() {
        WorldEditorHolder holder = new WorldEditorHolder(viewer, WorldEditorScreen.MAIN, WorldName.of("world"), 0);
        Inventory built = server.createInventory(holder, InventoryType.CHEST);
        holder.attach(built);
        assertThat(holder.getInventory()).isSameAs(built);
    }

    @SuppressWarnings("NullAway") // deliberately feeds null to verify the constructor rejects it at runtime
    @Test
    void rejectsNullViewer() {
        assertThatNullPointerException().isThrownBy(() -> new WorldEditorHolder(null, WorldEditorScreen.LIST, null, 0));
    }

    @SuppressWarnings("NullAway") // deliberately feeds null to verify the constructor rejects it at runtime
    @Test
    void rejectsNullScreen() {
        assertThatNullPointerException().isThrownBy(() -> new WorldEditorHolder(viewer, null, null, 0));
    }
}
