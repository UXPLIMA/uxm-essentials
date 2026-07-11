package com.uxplima.uxmessentials.playerwarps.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpEconomy;
import com.uxplima.uxmessentials.playerwarps.domain.ChargeError;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId;
import com.uxplima.uxmessentials.playerwarps.domain.RewardSpec;
import com.uxplima.uxmessentials.shared.adapter.outbound.action.ClickCommandRunner;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The Bukkit rating-reward granter over recording fakes: a money reward routes to the economy seam's guarded
 * credit, a command reward is dispatched from the console with {@code %player%} substituted (hopped onto the global
 * region thread first), an empty reward grants nothing, and a money reward with no economy present is a safe no-op.
 */
class BukkitRatingRewardGranterTest {

    private static final PlayerRef STEVE = new PlayerRef(UUID.randomUUID(), "Steve");

    private RecordingEconomy economy;
    private InlineGlobalScheduler scheduler;
    private RecordingCommands commands;

    @BeforeEach
    void setUp() {
        economy = new RecordingEconomy();
        scheduler = new InlineGlobalScheduler();
        commands = new RecordingCommands();
    }

    private BukkitRatingRewardGranter granter(Optional<PlayerWarpEconomy> withEconomy) {
        return new BukkitRatingRewardGranter(withEconomy, scheduler, commands, new NoopLogger());
    }

    @Test
    void aMoneyRewardCreditsTheSubjectThroughTheEconomy() {
        granter(Optional.of(economy)).grant(STEVE, RewardSpec.of(BigDecimal.TEN, "gold", ""));

        assertThat(economy.creditedTo).isEqualTo(STEVE);
        assertThat(economy.creditedAmount).isEqualByComparingTo(BigDecimal.TEN);
        assertThat(economy.creditedCurrency).isEqualTo("gold");
        assertThat(commands.lastConsoleCommand).isNull();
    }

    @Test
    void aCommandRewardDispatchesTheSubstitutedCommandFromTheConsole() {
        granter(Optional.of(economy))
                .grant(STEVE, RewardSpec.of(BigDecimal.ZERO, "default", "give %player% diamond 1"));

        assertThat(scheduler.globalHops).isEqualTo(1);
        assertThat(commands.lastConsoleCommand).isEqualTo("give Steve diamond 1");
        assertThat(economy.creditedTo).isNull();
    }

    @Test
    void anEmptyRewardGrantsNothing() {
        granter(Optional.of(economy)).grant(STEVE, RewardSpec.none());

        assertThat(economy.creditedTo).isNull();
        assertThat(commands.lastConsoleCommand).isNull();
        assertThat(scheduler.globalHops).isZero();
    }

    @Test
    void aMoneyRewardWithNoEconomyIsANoOp() {
        granter(Optional.empty()).grant(STEVE, RewardSpec.of(BigDecimal.TEN, "default", ""));

        assertThat(commands.lastConsoleCommand).isNull();
    }

    /** Records the target, amount, and currency of the single credit the granter routes through {@code refund}. */
    private static final class RecordingEconomy implements PlayerWarpEconomy {
        @Nullable PlayerRef creditedTo;

        @Nullable BigDecimal creditedAmount;

        @Nullable String creditedCurrency;

        @Override
        public Result<Unit, ChargeError> refund(PlayerRef to, BigDecimal amount, String currencyId) {
            creditedTo = to;
            creditedAmount = amount;
            creditedCurrency = currencyId;
            return Result.ok();
        }

        @Override
        public Result<Unit, ChargeError> chargeAndAccrue(
                PlayerRef payer, PlayerWarpId warp, BigDecimal price, String currencyId) {
            return Result.ok();
        }

        @Override
        public boolean canAfford(PlayerRef who, BigDecimal amount, String currencyId) {
            return true;
        }

        @Override
        public Result<Unit, ChargeError> withdraw(PlayerWarpId warp, PlayerRef to) {
            return Result.ok();
        }

        @Override
        public Result<Unit, ChargeError> collectRent(
                PlayerWarpId warp, PlayerRef owner, BigDecimal amount, String currencyId) {
            return Result.ok();
        }

        @Override
        public Result<Unit, ChargeError> chargeOwner(PlayerRef owner, BigDecimal amount, String currencyId) {
            return Result.ok();
        }
    }

    /** Runs {@code onGlobal} work inline (counting the hop) and rejects any other scheduler surface. */
    private static final class InlineGlobalScheduler implements Scheduler {
        int globalHops;

        @Override
        public void onGlobal(Runnable task) {
            globalHops++;
            task.run();
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void async(Runnable task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            throw new UnsupportedOperationException();
        }
    }

    /** Records the last console command dispatched, so a test can assert the resolved text. */
    private static final class RecordingCommands implements ClickCommandRunner {
        @Nullable String lastConsoleCommand;

        @Override
        public void runAsConsole(String command) {
            lastConsoleCommand = command;
        }

        @Override
        public void runAsPlayer(Player player, String command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void runAsPlayerOp(Player player, String command) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class NoopLogger implements Logger {
        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }
}
