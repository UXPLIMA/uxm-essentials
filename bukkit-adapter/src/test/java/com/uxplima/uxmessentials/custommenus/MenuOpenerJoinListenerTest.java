package com.uxplima.uxmessentials.custommenus;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.bukkit.Material;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.custommenus.adapter.inbound.listener.MenuOpenerJoinListener;
import com.uxplima.uxmessentials.custommenus.adapter.inbound.listener.OpenerItems;
import com.uxplima.uxmessentials.custommenus.adapter.inbound.listener.OpenerSpec;
import com.uxplima.uxmessentials.custommenus.adapter.inbound.listener.OpenerSpec.GiveOnJoin;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of give-on-join through the real {@link MenuOpenerJoinListener}. A {@link GiveOnJoin#FIRST}
 * opener is handed to a fresh joiner at its slot with the given-flag set, and a rejoin does not duplicate it;
 * {@link GiveOnJoin#ALWAYS} hands one out on every join; {@link GiveOnJoin#NEVER} hands out nothing; and an occupied
 * target slot falls back to a free slot rather than overwriting what is there.
 */
class MenuOpenerJoinListenerTest {

    private ServerMock server;
    private OpenerItems openerItems;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        openerItems = new OpenerItems(MockBukkit.createMockPlugin());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void firstJoinGivesTheItemAtItsSlotAndSetsTheFlagAndDoesNotDuplicateOnRejoin() {
        PlayerMock player = server.addPlayer("Steve");
        MenuOpenerJoinListener listener = listenerFor(opener("hub", 4, GiveOnJoin.FIRST));

        join(listener, player);

        assertThat(openerItems.menuOf(itemAt(player, 4))).contains("hub");
        assertThat(openerItems.hasGiven(player, "hub")).isTrue();
        assertThat(totalOpenerAmount(player)).isEqualTo(1);

        join(listener, player);
        assertThat(totalOpenerAmount(player)).isEqualTo(1);
    }

    @Test
    void alwaysGivesTheItemOnEveryJoin() {
        PlayerMock player = server.addPlayer("Steve");
        MenuOpenerJoinListener listener = listenerFor(opener("hub", 4, GiveOnJoin.ALWAYS));

        join(listener, player);
        join(listener, player);

        assertThat(totalOpenerAmount(player)).isEqualTo(2);
        assertThat(openerItems.hasGiven(player, "hub")).isFalse();
    }

    @Test
    void neverGivesNothing() {
        PlayerMock player = server.addPlayer("Steve");
        MenuOpenerJoinListener listener = listenerFor(opener("hub", 4, GiveOnJoin.NEVER));

        join(listener, player);

        assertThat(totalOpenerAmount(player)).isZero();
    }

    @Test
    void anOccupiedSlotFallsBackToAddingElsewhere() {
        PlayerMock player = server.addPlayer("Steve");
        player.getInventory().setItem(4, new ItemStack(Material.STONE));
        MenuOpenerJoinListener listener = listenerFor(opener("hub", 4, GiveOnJoin.ALWAYS));

        join(listener, player);

        // The pre-placed stone is untouched and the opener landed somewhere else.
        assertThat(itemAt(player, 4).getType()).isEqualTo(Material.STONE);
        assertThat(totalOpenerAmount(player)).isEqualTo(1);
    }

    private MenuOpenerJoinListener listenerFor(OpenerSpec opener) {
        return new MenuOpenerJoinListener(() -> List.of(opener), openerItems);
    }

    private static void join(MenuOpenerJoinListener listener, PlayerMock player) {
        listener.onJoin(new PlayerJoinEvent(player, Component.empty()));
    }

    private static ItemStack itemAt(PlayerMock player, int slot) {
        ItemStack item = player.getInventory().getItem(slot);
        return item == null ? new ItemStack(Material.AIR) : item;
    }

    private int totalOpenerAmount(PlayerMock player) {
        int total = 0;
        for (@Nullable ItemStack item : player.getInventory().getContents()) {
            if (item != null && openerItems.menuOf(item).isPresent()) {
                total += item.getAmount();
            }
        }
        return total;
    }

    private static OpenerSpec opener(String menu, int slot, GiveOnJoin mode) {
        return new OpenerSpec(menu, new OpenerSpec.Item(Material.COMPASS, "<gold>Menu", List.of()), slot, mode);
    }
}
