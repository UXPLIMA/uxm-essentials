package com.uxplima.uxmessentials.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * Opening a vault again, however it happens, keeps every item in exactly one place. Every diamond is counted where it
 * can be: in the vault window, in the player's inventory and cursor, and on the ground.
 */
@Tag("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VaultReopenScenarioTest {

    private static final String OPEN = "vault 1";

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
    void whatIsPutInAndClosedIsThereOnTheNextOpen() {
        ScenarioPlayer owner = running.join("Ada");
        running.run(owner, OPEN);
        assertThat(running.place(owner, 0, new ItemStack(Material.DIAMOND, 10))).isTrue();
        owner.closeInventory();
        running.settle();

        running.run(owner, OPEN);

        assertThat(inVault(owner)).isEqualTo(10);
        assertThat(everywhere(owner)).isEqualTo(10);
        assertThat(running.problems()).isEmpty();
    }

    @Test
    void reopeningWhileTheOldWindowIsShowingCarriesWhatWasJustPutInAndCopiesNothing() {
        ScenarioPlayer owner = running.join("Bea");
        running.run(owner, OPEN);
        assertThat(running.place(owner, 0, new ItemStack(Material.DIAMOND, 10))).isTrue();
        owner.closeInventory();
        running.settle();
        running.run(owner, OPEN);
        assertThat(running.place(owner, 1, new ItemStack(Material.DIAMOND, 5))).isTrue();

        running.run(owner, OPEN); // the old window is still in front of the player

        assertThat(owner.getOpenInventory().getType()).isEqualTo(InventoryType.CHEST);
        assertThat(inVault(owner)).isEqualTo(15);
        assertThat(onTheGround()).isZero();
        assertThat(everywhere(owner)).isEqualTo(15);
        owner.closeInventory();
        running.settle();
        running.run(owner, OPEN);
        assertThat(inVault(owner)).as("written once, as the new window closed").isEqualTo(15);
        assertThat(everywhere(owner)).isEqualTo(15);
        assertThat(running.problems()).isEmpty();
    }

    @Test
    void reopeningThreeTimesInARowKeepsTheCount() {
        ScenarioPlayer owner = running.join("Cleo");
        running.run(owner, OPEN);
        assertThat(running.place(owner, 0, new ItemStack(Material.DIAMOND, 12))).isTrue();

        owner.performCommand(OPEN);
        owner.performCommand(OPEN);
        owner.performCommand(OPEN);
        running.settle();

        assertThat(inVault(owner)).isEqualTo(12);
        assertThat(everywhere(owner)).isEqualTo(12);
        owner.closeInventory();
        running.settle();
        running.run(owner, OPEN);
        assertThat(everywhere(owner)).isEqualTo(12);
        assertThat(running.problems()).isEmpty();
    }

    @Test
    void leavingWithTheVaultOpenAndComingBackKeepsTheCount() {
        ScenarioPlayer owner = running.join("Dara");
        running.run(owner, OPEN);
        assertThat(running.place(owner, 0, new ItemStack(Material.DIAMOND, 7))).isTrue();

        running.quit(owner);
        ScenarioPlayer back = running.join("Dara");
        running.run(back, OPEN);

        assertThat(back.getOpenInventory().getType()).isEqualTo(InventoryType.CHEST);
        assertThat(inVault(back)).isEqualTo(7);
        assertThat(everywhere(back)).isEqualTo(7);
        assertThat(running.problems()).isEmpty();
    }

    /** Diamonds in the window the player has open, which is their vault. */
    private static int inVault(ScenarioPlayer player) {
        assertThat(player.getOpenInventory().getType())
                .as("a vault window is open")
                .isEqualTo(InventoryType.CHEST);
        return diamonds(player.getOpenInventory().getTopInventory());
    }

    /** Every diamond this player could lay hands on: the open vault, their inventory, their cursor and the ground. */
    private int everywhere(ScenarioPlayer player) {
        int count = diamonds(player.getInventory()) + diamonds(player.getItemOnCursor()) + onTheGround();
        if (player.getOpenInventory().getType() == InventoryType.CHEST) {
            count += diamonds(player.getOpenInventory().getTopInventory());
        }
        return count;
    }

    private int onTheGround() {
        return running.world.getEntitiesByClass(Item.class).stream()
                .mapToInt(item -> diamonds(item.getItemStack()))
                .sum();
    }

    private static int diamonds(Inventory inventory) {
        int count = 0;
        for (@Nullable ItemStack stack : inventory.getContents()) {
            count += diamonds(stack);
        }
        return count;
    }

    private static int diamonds(@Nullable ItemStack stack) {
        return stack != null && stack.getType() == Material.DIAMOND ? stack.getAmount() : 0;
    }
}
