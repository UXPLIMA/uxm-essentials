package com.uxplima.uxmessentials.villagers.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.villagers.adapter.inbound.listener.ClickToTradeListener;
import com.uxplima.uxmessentials.villagers.adapter.outbound.PdcVillagerFlags;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

/**
 * MockBukkit coverage of click-to-trade: a permitted player's right-click opens the villager's trade window and cancels
 * the interaction, while a player without the permission, a globally-disabled trade, a per-villager disabled trade, and
 * the off-hand click are all left alone with no window opened.
 */
class ClickToTradeListenerTest {

    private static final String TRADE_PERMISSION = "uxmessentials.villagers.trade";

    private ServerMock server;
    private WorldMock world;
    private Plugin plugin;
    private PlayerMock player;
    private Villager villager;
    private PdcVillagerFlags flags;
    private RecordingOpener opener;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        world = server.addSimpleWorld("world");
        player = server.addPlayer("Steve");
        villager = (Villager) world.spawnEntity(new Location(world, 0, 64, 0), EntityType.VILLAGER);
        flags = new PdcVillagerFlags();
        opener = new RecordingOpener();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aPermittedPlayerOpensTheTradeAndTheInteractionIsCancelled() {
        grantTradePermission();
        PlayerInteractEntityEvent event = mainHandClick();

        listener(false).onInteract(event);

        assertThat(opener.opened).containsExactly(villager);
        assertThat(event.isCancelled()).isTrue();
    }

    @Test
    void aPlayerWithoutPermissionOpensNothing() {
        PlayerInteractEntityEvent event = mainHandClick();

        listener(false).onInteract(event);

        assertThat(opener.opened).isEmpty();
        assertThat(event.isCancelled()).isFalse();
    }

    @Test
    void aGloballyDisabledVillagerOpensNothing() {
        grantTradePermission();
        PlayerInteractEntityEvent event = mainHandClick();

        listener(true).onInteract(event);

        assertThat(opener.opened).isEmpty();
        assertThat(event.isCancelled()).isFalse();
    }

    @Test
    void aPerVillagerDisabledTradeOpensNothing() {
        grantTradePermission();
        flags.setTradesDisabled(villager, true);
        PlayerInteractEntityEvent event = mainHandClick();

        listener(false).onInteract(event);

        assertThat(opener.opened).isEmpty();
        assertThat(event.isCancelled()).isFalse();
    }

    @Test
    void theOffHandClickIsIgnored() {
        grantTradePermission();
        PlayerInteractEntityEvent event = new PlayerInteractEntityEvent(player, villager, EquipmentSlot.OFF_HAND);

        listener(false).onInteract(event);

        assertThat(opener.opened).isEmpty();
        assertThat(event.isCancelled()).isFalse();
    }

    private ClickToTradeListener listener(boolean disableGlobally) {
        return new ClickToTradeListener(disableGlobally, TRADE_PERMISSION, flags, opener::record);
    }

    private void grantTradePermission() {
        player.addAttachment(plugin, TRADE_PERMISSION, true);
    }

    private PlayerInteractEntityEvent mainHandClick() {
        return new PlayerInteractEntityEvent(player, villager, EquipmentSlot.HAND);
    }

    private static final class RecordingOpener {
        private final List<Villager> opened = new ArrayList<>();

        void record(Player player, Villager villager) {
            opened.add(villager);
        }
    }
}
