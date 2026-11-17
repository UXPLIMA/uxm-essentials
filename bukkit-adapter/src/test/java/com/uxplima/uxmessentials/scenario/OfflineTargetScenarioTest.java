package com.uxplima.uxmessentials.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * Commands whose target is kept by account reach a player who logged off: staff go to an offline player's home, a
 * player blocks or unblocks the requests of someone who is not online, and staff reset the playtime of, or copy and
 * clear the containers of, a player who left.
 */
@Tag("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OfflineTargetScenarioTest {

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
    void staffGoToAHomeOfAPlayerWhoLoggedOff() {
        ScenarioPlayer staff = running.join("Stella");
        staff.setOp(true);
        ScenarioPlayer owner = running.join("Quinn");
        Location home = new Location(running.world, 10.5, 5, 10.5);
        staff.teleport(home);
        running.run(staff, "home admin Quinn set 1");
        running.quit(owner);

        staff.teleport(new Location(running.world, 60.5, 5, 60.5));
        running.run(staff, "home admin Quinn tp 1");
        // Long enough for the shipped warmup, should the staff teleport have one.
        for (int second = 0; second < 4; second++) {
            running.settle();
        }

        assertThat(staff.getLocation().getBlockX()).isEqualTo(home.getBlockX());
        assertThat(staff.getLocation().getBlockZ()).isEqualTo(home.getBlockZ());
        assertThat(running.problems()).isEmpty();
    }

    @Test
    void aPlayerWhoLoggedOffIsBlockedAndUnblocked() {
        ScenarioPlayer alice = running.join("Alice");
        ScenarioPlayer bob = running.join("Bob");
        running.quit(bob);

        running.run(alice, "tpblock Bob");
        bob = running.join("Bob");
        RunningPlugin.said(alice);
        running.run(bob, "tpa Alice");
        assertThat(RunningPlugin.said(alice))
                .as("a blocked player's request never reaches Alice")
                .isEmpty();

        running.quit(bob);
        running.run(alice, "tpunblock Bob");
        bob = running.join("Bob");
        RunningPlugin.said(alice);
        running.run(bob, "tpa Alice");
        assertThat(RunningPlugin.said(alice))
                .as("the request reaches Alice once Bob is unblocked")
                .isNotEmpty();
        assertThat(running.problems()).isEmpty();
    }

    @Test
    void staffResetThePlaytimeOfAPlayerWhoLeft() {
        ScenarioPlayer staff = running.join("Stacy");
        staff.setOp(true);
        running.quit(running.join("Olive"));
        RunningPlugin.said(staff);

        running.run(staff, "playtime reset Olive");

        assertThat(RunningPlugin.saidPlain(staff))
                .anyMatch(line -> line.contains("reset the tracked playtime of Olive"));
        assertThat(running.problems()).isEmpty();
    }

    @Test
    void theItemCommandsReachForTheStoredContainerOfAPlayerWhoLeft() {
        ScenarioPlayer staff = running.join("Sven");
        staff.setOp(true);
        running.quit(running.join("Otto"));
        RunningPlugin.said(staff);

        running.run(staff, "enderclear Otto");
        running.run(staff, "endercopy Otto");
        running.run(staff, "copyinv Otto");

        // MockBukkit has no player data files, so the stored container is out of reach; before, the name was unknown.
        assertThat(RunningPlugin.saidPlain(staff))
                .hasSize(3)
                .allMatch(line -> line.contains("offline inventories cannot be opened on this server version"));
        assertThat(running.problems()).isEmpty();
    }

    @Test
    void theItemCommandsStillWorkOnAPlayerOnlineHere() {
        ScenarioPlayer staff = running.join("Sage");
        staff.setOp(true);
        ScenarioPlayer target = running.join("Tilly");
        target.getEnderChest().setItem(3, new ItemStack(Material.DIAMOND, 2));
        target.getInventory().setItem(5, new ItemStack(Material.EMERALD, 4));

        running.run(staff, "endercopy tilly");
        running.run(staff, "copyinv Tilly");
        running.run(staff, "enderclear Tilly");

        assertThat(staff.getEnderChest().getItem(3)).isEqualTo(new ItemStack(Material.DIAMOND, 2));
        assertThat(staff.getInventory().getItem(5)).isEqualTo(new ItemStack(Material.EMERALD, 4));
        assertThat(target.getEnderChest().isEmpty()).isTrue();
        assertThat(running.problems()).isEmpty();
    }
}
