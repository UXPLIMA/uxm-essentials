package com.uxplima.uxmessentials.economy.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.economy.application.port.ItemPurse.Change;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.Denomination;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * A reset of an item currency brings the items a player holds to the figure, takes the surplus, and fills only what the
 * inventory has room for, dropping nothing; a player who is not here is reported as not reached.
 */
class BukkitItemPurseTest {

    private static final Currency GOLD = Currency.builder(CurrencyId.of("gold"))
            .physical(true)
            .denominations(List.of(new Denomination(BigDecimal.ONE, "GOLD_NUGGET", "gold nugget", null)))
            .build();

    private ServerMock server;
    private PlayerMock alex;
    private final BukkitInventoryCalculations calculations = new BukkitInventoryCalculations();
    private final BukkitItemPurse purse = new BukkitItemPurse(new OwnThread(), calculations, new RecordingLog());

    @BeforeEach
    void start() {
        server = MockBukkit.mock();
        alex = server.addPlayer("Alex");
    }

    @AfterEach
    void stop() {
        MockBukkit.unmock();
    }

    @Test
    void aResetTakesTheSurplusAndSaysWhatTheBalanceWas() {
        alex.getInventory().addItem(new ItemStack(Material.GOLD_NUGGET, 40));

        Change change = purse.setTo(ref(), gold("10")).orElseThrow();

        assertThat(change.before()).isEqualByComparingTo("40");
        assertThat(change.after()).isEqualByComparingTo("10");
        assertThat(calculations.getBalance(alex, GOLD)).isEqualByComparingTo("10");
    }

    @Test
    void aResetUpFillsOnlyTheRoomThereIsAndDropsNothing() {
        // Every slot but the first is full; the test server also puts items in the armour slots, so those are too.
        ItemStack[] contents = alex.getInventory().getContents();
        for (int slot = 1; slot < contents.length; slot++) {
            contents[slot] = new ItemStack(Material.STONE, 64);
        }
        alex.getInventory().setContents(contents);

        Change change = purse.setTo(ref(), gold("100")).orElseThrow();

        assertThat(change.before()).isEqualByComparingTo("0");
        assertThat(change.after()).isEqualByComparingTo("64");
        assertThat(alex.getWorld().getEntitiesByClass(Item.class)).isEmpty();
    }

    @Test
    void aPlayerWhoIsNotHereIsNotReached() {
        PlayerRef away = new PlayerRef(new UUID(0L, 99L), "Away");

        assertThat(purse.setTo(away, gold("10"))).isEmpty();
        assertThat(purse.give(away, gold("10"))).isEmpty();
    }

    private PlayerRef ref() {
        return new PlayerRef(alex.getUniqueId(), alex.getName());
    }

    private static Money gold(String amount) {
        return Money.of(GOLD, new BigDecimal(amount));
    }

    /** The caller already runs on the player's thread, so the work runs in place. */
    private static final class OwnThread implements Scheduler {

        @Override
        public boolean ownsEntity(PlayerRef player) {
            return true;
        }

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
