package com.uxplima.uxmessentials.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Material;

import com.uxplima.uxmessentials.api.query.UxmEconomyQuery;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/** What a player does on an ordinary day: set a home and go back to it, pay someone, message someone. */
@Tag("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PlayerFlowScenarioTest {

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
    void aHomeSetFromTheHomeWindowTakesThePlayerBackToIt() {
        ScenarioPlayer player = running.join("Hana");
        Location home = new Location(running.world, 10.5, 5, 10.5);
        player.teleport(home);

        running.run(player, "home");
        running.click(player, running.slotOf(player, Material.GRAY_BED));
        player.closeInventory();
        player.teleport(new Location(running.world, 60.5, 5, 60.5));
        running.run(player, "home");
        running.click(player, running.slotOf(player, Material.RED_BED));
        running.click(player, running.slotOf(player, Material.ENDER_PEARL));
        // The shipped warmup is three seconds.
        for (int second = 0; second < 4; second++) {
            running.settle();
        }

        assertThat(player.getLocation().getBlockX()).isEqualTo(home.getBlockX());
        assertThat(player.getLocation().getBlockZ()).isEqualTo(home.getBlockZ());
        assertThat(running.await(running.api().homes().orElseThrow().count(player.getUniqueId())))
                .isEqualTo(1);
        assertThat(running.problems()).isEmpty();
    }

    @Test
    void aPaymentMovesMoneyFromThePayerToThePayeeAndARefusedOneMovesNothing() {
        ScenarioPlayer payer = running.join("Paula");
        ScenarioPlayer payee = running.join("Pete");
        payer.setOp(true);
        running.run(payer, "eco give Paula 100");

        running.run(payer, "pay Pete 25");
        running.run(payer, "pay Pete 500");

        assertThat(balance(payer.getUniqueId())).isEqualByComparingTo("75");
        assertThat(balance(payee.getUniqueId())).isEqualByComparingTo("25");
        assertThat(running.problems()).isEmpty();
    }

    @Test
    void aMessageReachesAPlayerWhileOnlineAndBecomesMailOnceTheyLeave() {
        ScenarioPlayer sender = running.join("Mira");
        ScenarioPlayer reader = running.join("Rhys");
        RunningPlugin.said(reader);

        running.run(sender, "msg Rhys first");
        assertThat(RunningPlugin.said(reader))
                .anySatisfy(line -> assertThat(line).contains("first"));

        running.quit(reader);
        RunningPlugin.said(sender);
        running.run(sender, "msg Rhys second");
        assertThat(RunningPlugin.said(reader))
                .as("nothing reaches a player who left")
                .isEmpty();
        assertThat(RunningPlugin.said(sender))
                .as("the sender learns the reader is offline")
                .singleElement()
                .asString()
                .contains("Rhys")
                .doesNotContain("second");
        assertThat(running.problems()).isEmpty();
    }

    private BigDecimal balance(UUID player) {
        UxmEconomyQuery economy = running.api().economy().orElseThrow();
        return running.await(economy.balance(player)).amount();
    }
}
