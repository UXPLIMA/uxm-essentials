package com.uxplima.uxmessentials.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * Staff restoring the inventory of a player who is not online: the restore is kept and put on the player as they next
 * join, once.
 */
@Tag("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InvRestoreScenarioTest {

    private static final String ENABLED = """
            enabled = true
            capture {
              on-death = true
              on-logout = true
            }
            include-enderchest = true
            retention {
              max-per-player = 10
              max-age-days = 30
            }
            """;

    /** The restore button of the snapshot preview, where the bundled preview window puts it. */
    private static final int RESTORE_SLOT = 53;

    private RunningPlugin running;
    private ScenarioPlayer staff;

    @BeforeAll
    void start() {
        running = RunningPlugin.start(Map.of("modules/invrollback/config.conf", ENABLED));
        staff = running.join("Sol");
        staff.setOp(true);
    }

    @AfterAll
    void stop() {
        running.close();
    }

    @Test
    void aRestoreOfAPlayerWhoIsOfflineIsPutOnThemAsTheyNextJoinAndOnlyThen() {
        leftHolding("Ivy", 5);

        restoreTheLogoutSnapshotOf("Ivy");

        ScenarioPlayer back = running.join("Ivy");
        assertThat(diamonds(back)).isEqualTo(5);

        back.getInventory().clear();
        running.quit(back);
        ScenarioPlayer again = running.join("Ivy");
        assertThat(diamonds(again)).as("the restore applies once").isZero();
        assertThat(running.problems()).isEmpty();
    }

    @Test
    void aRestoreOfABedrockPlayerWhoIsOfflineIsPutOnThemAsTheyNextJoin() {
        leftHolding("#Bea", 3);

        restoreTheLogoutSnapshotOf("#Bea");

        ScenarioPlayer back = running.join("#Bea");
        assertThat(diamonds(back)).isEqualTo(3);
        assertThat(running.problems()).isEmpty();
    }

    /** {@code name} joins, picks up {@code count} diamonds and leaves, which snapshots what they hold. */
    private void leftHolding(String name, int count) {
        ScenarioPlayer player = running.join(name);
        player.getInventory().addItem(new ItemStack(Material.DIAMOND, count));
        running.quit(player);
    }

    /** Staff open the player's snapshots, preview the one their logout took, and restore it. */
    private void restoreTheLogoutSnapshotOf(String name) {
        running.run(staff, "invrestore " + name);
        running.click(staff, running.slotOf(staff, Material.ENDER_PEARL));
        running.click(staff, RESTORE_SLOT);
        staff.closeInventory();
        running.settle();
    }

    private static int diamonds(ScenarioPlayer player) {
        int count = 0;
        for (@Nullable ItemStack stack : player.getInventory().getContents()) {
            count += stack != null && stack.getType() == Material.DIAMOND ? stack.getAmount() : 0;
        }
        return count;
    }
}
