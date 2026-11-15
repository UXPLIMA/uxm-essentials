package com.uxplima.uxmessentials.teleport.application;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static com.uxplima.uxmessentials.testing.Fixtures.CLOCK;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Warmups;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.teleport.application.port.CombatGate;
import com.uxplima.uxmessentials.teleport.application.port.JailGate;
import com.uxplima.uxmessentials.teleport.application.port.TeleportExecutor;
import com.uxplima.uxmessentials.teleport.application.port.TeleportFee;
import com.uxplima.uxmessentials.teleport.domain.Destination;
import com.uxplima.uxmessentials.teleport.domain.TeleportError;
import com.uxplima.uxmessentials.teleport.domain.TeleportKind;
import com.uxplima.uxmessentials.testing.ClockCooldowns;
import com.uxplima.uxmessentials.testing.Fixtures;
import com.uxplima.uxmessentials.testing.RecordingEvents;
import com.uxplima.uxmessentials.testing.RecordingNotifier;
import org.junit.jupiter.api.Test;

/** A paid {@code /rtp} through the engine: when the fee is taken, and what happens when it cannot be. */
class TeleportEngineTest {

    private static final Position LANDING = Fixtures.at(1200, 70, -340);
    private static final Map<String, String> PRICE = Map.of("cost", "50.0 coins");

    private final HeldWarmups warmups = new HeldWarmups();
    private final Hops hops = new Hops();
    private final Purse purse = new Purse(new BigDecimal("100"));
    private final List<PlayerRef> shielded = new ArrayList<>();
    private final RecordingNotifier notifier = new RecordingNotifier();
    private final TeleportEngine engine = new TeleportEngine(
            new ClockCooldowns(CLOCK),
            warmups,
            hops,
            notifier.notifier(),
            new RecordingEvents(),
            new TeleportSettings(new CostOf("50")),
            JailGate.NEVER,
            CombatGate.NEVER,
            purse,
            shielded::add);

    private void startRandomTeleport() {
        assertThat(engine.gateRandom(ALEX).isOk()).isTrue();
        engine.launchRandom(ALEX, Destination.at(LANDING));
    }

    @Test
    void theFeeIsTakenWhenTheWarmupEndsAndThePlayerLands() {
        startRandomTeleport();
        assertThat(purse.balance)
                .as("nothing is taken while the player is still warming up")
                .isEqualByComparingTo("100");

        warmups.finish();

        assertThat(purse.balance).isEqualByComparingTo("50");
        assertThat(hops.arrived).containsExactly(LANDING);
        assertThat(shielded).containsExactly(ALEX);
        assertThat(engine.gateRandom(ALEX).asError())
                .as("the landing stamps the cooldown")
                .contains(TeleportError.ON_COOLDOWN);
    }

    @Test
    void aPlayerWhoSpentTheirBalanceDuringTheWarmupIsRefusedAndStaysPut() {
        startRandomTeleport();
        purse.balance = new BigDecimal("10");

        warmups.finish();

        assertThat(hops.arrived).isEmpty();
        assertThat(purse.balance).isEqualByComparingTo("10");
        assertThat(shielded).isEmpty();
        assertThat(notifier.sent())
                .containsExactly(new RecordingNotifier.Sent(ALEX, "teleport.rtp.cant-afford", PRICE));
    }

    @Test
    void aHopThatMissesAfterTheChargeGivesTheFeeBack() {
        hops.outcome = Hop.MISS;
        startRandomTeleport();

        warmups.finish();

        assertThat(hops.arrived).isEmpty();
        assertThat(purse.balance).isEqualByComparingTo("100");
        assertThat(shielded).isEmpty();
        assertThat(notifier.sent()).containsExactly(new RecordingNotifier.Sent(ALEX, "teleport.rtp.refunded", PRICE));
    }

    @Test
    void aHopThatThrowsAfterTheChargeGivesTheFeeBackOnce() {
        hops.outcome = Hop.THROW;
        startRandomTeleport();

        warmups.finish();

        assertThat(hops.arrived).isEmpty();
        assertThat(purse.balance).isEqualByComparingTo("100");
        assertThat(notifier.sent()).containsExactly(new RecordingNotifier.Sent(ALEX, "teleport.rtp.refunded", PRICE));
    }

    @Test
    void aRefundThatFailsTellsThePlayerTheFeeIsStillTaken() {
        hops.outcome = Hop.MISS;
        purse.refunds = false;
        startRandomTeleport();

        warmups.finish();

        assertThat(hops.arrived).isEmpty();
        assertThat(purse.balance).isEqualByComparingTo("50");
        assertThat(notifier.sent())
                .containsExactly(new RecordingNotifier.Sent(ALEX, "teleport.rtp.refund-failed", PRICE));
    }

    /** Warmups that hold the completion until the test says the countdown has elapsed. */
    private static final class HeldWarmups implements Warmups {

        private final List<Runnable> pending = new ArrayList<>();

        @Override
        public WarmupHandle begin(PlayerRef who, WarmupKind kind, Runnable onComplete, Runnable onCancel) {
            pending.add(onComplete);
            return new Warmups.CompletedWarmup(who);
        }

        void finish() {
            List<Runnable> due = List.copyOf(pending);
            pending.clear();
            due.forEach(Runnable::run);
        }
    }

    /** How every hop of the fake executor ends. */
    private enum Hop {
        LAND,
        MISS,
        // Reports the miss and then throws as well, the worst case for paying the refund exactly once.
        THROW
    }

    /** An executor whose hops all end the same way. */
    private static final class Hops implements TeleportExecutor {

        private final List<Position> arrived = new ArrayList<>();
        private Hop outcome = Hop.LAND;

        @Override
        public void teleport(PlayerRef who, Destination destination, TeleportKind kind) {
            arrived.add(destination.position());
        }

        @Override
        public void teleport(
                PlayerRef who, Destination destination, TeleportKind kind, Runnable onLanded, Runnable onFailed) {
            if (outcome != Hop.LAND) {
                onFailed.run();
                if (outcome == Hop.THROW) {
                    throw new IllegalStateException("the hop broke after reporting its miss");
                }
                return;
            }
            teleport(who, destination, kind);
            onLanded.run();
        }
    }

    /** One player's balance, debited only when it covers the amount, as the guarded database debit does. */
    private static final class Purse implements TeleportFee {

        private BigDecimal balance;
        private boolean refunds = true;

        Purse(BigDecimal balance) {
            this.balance = balance;
        }

        @Override
        public boolean canAfford(PlayerRef who, BigDecimal amount) {
            return balance.compareTo(amount) >= 0;
        }

        @Override
        public CompletionStage<Result<Unit, TeleportError>> charge(PlayerRef who, BigDecimal amount) {
            if (!canAfford(who, amount)) {
                return CompletableFuture.completedFuture(Result.err(TeleportError.RTP_CANT_AFFORD));
            }
            balance = balance.subtract(amount);
            return CompletableFuture.completedFuture(Result.ok());
        }

        @Override
        public CompletionStage<Result<Unit, TeleportError>> refund(PlayerRef who, BigDecimal amount) {
            if (!refunds) {
                return CompletableFuture.completedFuture(Result.err(TeleportError.RTP_REFUND_FAILED));
            }
            balance = balance.add(amount);
            return CompletableFuture.completedFuture(Result.ok());
        }

        @Override
        public String format(BigDecimal amount) {
            return amount.toPlainString() + " coins";
        }
    }

    /** Teleport settings at their defaults, with the random-teleport cost set. */
    private record CostOf(String cost) implements ConfigStore {

        @Override
        public boolean getBoolean(String path, boolean fallback) {
            return fallback;
        }

        @Override
        public String getString(String path, String fallback) {
            return path.equals("rtp.cost") ? cost : fallback;
        }

        @Override
        public int getInt(String path, int fallback) {
            return fallback;
        }
    }
}
