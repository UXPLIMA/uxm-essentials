package com.uxplima.uxmessentials.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockbukkit.mockbukkit.MockBukkit;

/** Windows that hold real items: a staff member editing another inventory, a trade, a vault. */
@Tag("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WindowScenarioTest {

    private RunningPlugin running;

    @BeforeAll
    void start() {
        running = RunningPlugin.start();
    }

    @AfterAll
    void stop() {
        running.close();
    }

    @Test
    void aStaffMemberCannotDropWhileEditingAnInventoryAndTheEditIsSavedOnClose() {
        ScenarioPlayer staff = running.join("Stan");
        ScenarioPlayer target = running.join("Tess");
        staff.setOp(true);
        staff.getInventory().setItem(0, new ItemStack(Material.STICK));
        target.getInventory().setItem(0, new ItemStack(Material.DIAMOND, 3));

        running.run(staff, "invsee Tess");
        assertThat(staff.getOpenInventory().getTopInventory().getItem(0))
                .as("the window mirrors the target's inventory")
                .isEqualTo(new ItemStack(Material.DIAMOND, 3));
        assertThat(drop(staff).isCancelled())
                .as("a drop while the edit is open")
                .isTrue();

        assertThat(running.place(staff, 5, new ItemStack(Material.EMERALD, 2))).isTrue();
        staff.closeInventory();
        running.settle();

        assertThat(target.getInventory().getItem(5)).isEqualTo(new ItemStack(Material.EMERALD, 2));
        assertThat(target.getInventory().getItem(0)).isEqualTo(new ItemStack(Material.DIAMOND, 3));
        assertThat(drop(staff).isCancelled())
                .as("a drop once the edit is saved")
                .isFalse();
        assertThat(running.problems()).isEmpty();
    }

    @Test
    void aTraderWhoLeavesGetsTheirOfferBackAndThePartnerKeepsNothingOfIt() {
        ScenarioPlayer leaver = running.join("Ezra");
        ScenarioPlayer partner = running.join("Faye");
        running.run(leaver, "trade Faye");
        running.run(partner, "trade accept Ezra");
        assertThat(leaver.getOpenInventory().getTopInventory().getSize())
                .as("the trade window")
                .isEqualTo(54);

        assertThat(running.place(leaver, 9, new ItemStack(Material.DIAMOND, 4))).isTrue();
        running.quit(leaver);

        assertThat(leaver.getInventory().containsAtLeast(new ItemStack(Material.DIAMOND), 4))
                .as("the offer handed back as the trader left")
                .isTrue();
        assertThat(partner.getInventory().contains(Material.DIAMOND)).isFalse();
        assertThat(partner.getOpenInventory().getType())
                .as("the partner's window closes")
                .isEqualTo(InventoryType.CRAFTING);
        assertThat(running.problems()).isEmpty();
    }

    @Test
    void aVaultOpenInAnotherWindowIsRefusedToStaffUntilItsOwnerClosesIt() {
        ScenarioPlayer owner = running.join("Ola");
        ScenarioPlayer staff = running.join("Sven");
        staff.setOp(true);
        running.run(owner, "vault 1");
        RunningPlugin.said(staff);

        running.run(staff, "vault Ola 1");
        assertThat(staff.getOpenInventory().getType()).as("refused: no window").isEqualTo(InventoryType.CRAFTING);
        assertThat(RunningPlugin.said(staff))
                .as("told it is open elsewhere, and not that it opened")
                .hasSize(1);

        owner.closeInventory();
        running.settle();
        running.run(staff, "vault Ola 1");
        assertThat(staff.getOpenInventory().getType()).isEqualTo(InventoryType.CHEST);
        assertThat(running.problems()).isEmpty();
    }

    @Test
    void aVaultWindowAnotherPluginRefusesIsNotReportedOpenAndOpensOnTheNextTry() {
        ScenarioPlayer owner = running.join("Vera");
        RunningPlugin.said(owner);
        Listener refusing = new RefusesEveryWindow();
        running.server.getPluginManager().registerEvents(refusing, MockBukkit.createMockPlugin("Refuser"));
        try {
            running.run(owner, "vault 1");
            assertThat(owner.getOpenInventory().getType()).isEqualTo(InventoryType.CRAFTING);
            assertThat(RunningPlugin.said(owner)).as("no word that it opened").isEmpty();
        } finally {
            HandlerList.unregisterAll(refusing);
        }

        running.run(owner, "vault 1");
        assertThat(owner.getOpenInventory().getType()).isEqualTo(InventoryType.CHEST);
        assertThat(RunningPlugin.said(owner)).as("told it opened").hasSize(1);
        assertThat(running.problems()).isEmpty();
    }

    /** Another plugin that refuses every window, the way a region or anti-cheat plugin can. */
    static final class RefusesEveryWindow implements Listener {

        @EventHandler
        public void onOpen(InventoryOpenEvent event) {
            event.setCancelled(true);
        }
    }

    /** The player dropping the first stack of their inventory, as the server raises it. */
    private PlayerDropItemEvent drop(ScenarioPlayer player) {
        ItemStack held = player.getInventory().getItem(0);
        Item dropped =
                running.world.dropItem(player.getLocation(), held == null ? new ItemStack(Material.STICK) : held);
        PlayerDropItemEvent event = new PlayerDropItemEvent(player, dropped);
        running.server.getPluginManager().callEvent(event);
        dropped.remove();
        return event;
    }
}
