package com.uxplima.uxmessentials.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import org.bukkit.Material;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/** Staff open the vault of a player who is not online, Bedrock players included, and what they change is kept. */
@Tag("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VaultStaffOpenScenarioTest {

    private RunningPlugin running;
    private ScenarioPlayer staff;

    @BeforeAll
    void start() {
        running = RunningPlugin.start();
        staff = running.join("Sol");
        staff.setOp(true);
    }

    @AfterAll
    void stop() {
        running.close();
    }

    @Test
    void staffSeeTheVaultOfAPlayerWhoIsOffline() {
        storedThenLeft("Ada", 10);

        running.run(staff, "vault Ada 1");

        assertThat(inWindow(staff)).isEqualTo(10);
        staff.closeInventory();
        running.settle();
        assertThat(running.problems()).isEmpty();
    }

    @Test
    void whatStaffPutIntoTheVaultOfAPlayerWhoIsOfflineIsThereWhenTheyComeBack() {
        storedThenLeft("Bo", 4);
        running.run(staff, "vault Bo 1");
        assertThat(running.place(staff, 1, new ItemStack(Material.DIAMOND, 3))).isTrue();
        staff.closeInventory();
        running.settle();

        ScenarioPlayer back = running.join("Bo");
        running.run(back, "vault 1");

        assertThat(inWindow(back)).isEqualTo(7);
        back.closeInventory();
        running.settle();
        assertThat(running.problems()).isEmpty();
    }

    @Test
    void staffSeeTheVaultOfABedrockPlayerWhoIsOffline() {
        storedThenLeft("#Bea", 5);

        running.run(staff, "vault #Bea 1");

        assertThat(inWindow(staff)).isEqualTo(5);
        staff.closeInventory();
        running.settle();
        assertThat(running.problems()).isEmpty();
    }

    @Test
    void aSelectorStillOpensTheVaultOfAPlayerOnlineHere() {
        running.run(staff, "vault 1");
        assertThat(running.place(staff, 0, new ItemStack(Material.DIAMOND, 2))).isTrue();
        staff.closeInventory();
        running.settle();

        running.run(staff, "vault @s 1");

        assertThat(inWindow(staff)).isEqualTo(2);
        staff.closeInventory();
        running.settle();
        assertThat(running.problems()).isEmpty();
    }

    /** {@code name} joins, puts {@code diamonds} into their first vault, closes it and leaves. */
    private void storedThenLeft(String name, int diamonds) {
        ScenarioPlayer owner = running.join(name);
        running.run(owner, "vault 1");
        assertThat(running.place(owner, 0, new ItemStack(Material.DIAMOND, diamonds)))
                .isTrue();
        owner.closeInventory();
        running.settle();
        running.quit(owner);
    }

    /** Diamonds in the vault window the player has open. */
    private static int inWindow(ScenarioPlayer player) {
        assertThat(player.getOpenInventory().getType())
                .as("a vault window is open")
                .isEqualTo(InventoryType.CHEST);
        return diamonds(player.getOpenInventory().getTopInventory());
    }

    private static int diamonds(Inventory inventory) {
        int count = 0;
        for (@Nullable ItemStack stack : inventory.getContents()) {
            count += stack != null && stack.getType() == Material.DIAMOND ? stack.getAmount() : 0;
        }
        return count;
    }
}
