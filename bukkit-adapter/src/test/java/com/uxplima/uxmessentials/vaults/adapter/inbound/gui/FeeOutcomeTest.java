package com.uxplima.uxmessentials.vaults.adapter.inbound.gui;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import com.uxplima.uxmessentials.shared.adapter.inbound.window.WindowOutcome;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.vaults.application.VaultCharge;
import com.uxplima.uxmessentials.vaults.application.VaultChargeSettings;
import com.uxplima.uxmessentials.vaults.application.VaultOpenFee;
import com.uxplima.uxmessentials.vaults.application.port.VaultEconomy;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * A vault's open fee follows its window: kept when the window showed, given back exactly once, in the currency it was
 * taken in, when it was refused or a deferral ended without it. Nothing moves while a deferred open is undecided.
 */
class FeeOutcomeTest {

    private static final PlayerRef ALEX =
            new PlayerRef(UUID.fromString("00000000-0000-0000-0000-00000000000a"), "Alex");

    private final Wallet wallet = new Wallet();
    private final VaultCharge charge = new VaultCharge(
            new NoPermissions(),
            Optional.of(wallet),
            new VaultChargeSettings(BigDecimal.ZERO, new BigDecimal("2"), BigDecimal.ZERO));
    private final List<String> told = new ArrayList<>();
    /** Deposits handed off the tick, run when a test says so. */
    private final List<BooleanSupplier> refunds = new ArrayList<>();

    private VaultOpenFee paidOpen() {
        return charge.openFee(ALEX, charge.chargeOpen(ALEX).orElseThrow());
    }

    private WindowOutcome outcomeOf(VaultOpenFee fee) {
        return FeeOutcome.of(fee, () -> told.add("opened"), () -> told.add("refused"), refunds::add);
    }

    private void runRefunds() {
        List<BooleanSupplier> queued = List.copyOf(refunds);
        refunds.clear();
        queued.forEach(credit -> assertThat(credit.getAsBoolean()).isTrue());
    }

    @Test
    void aWindowThatShowedKeepsTheFee() {
        WindowOutcome outcome = outcomeOf(paidOpen());

        outcome.asked(false, true);
        outcome.refused();
        runRefunds();

        assertThat(told).containsExactly("opened");
        assertThat(wallet.moves).containsExactly("-2");
    }

    @Test
    void aRefusedWindowGivesTheFeeBackExactlyOnce() {
        WindowOutcome outcome = outcomeOf(paidOpen());

        outcome.asked(false, false);
        outcome.refused();
        outcome.settle(false);
        outcome.shown();
        runRefunds();

        assertThat(told).containsExactly("refused");
        assertThat(wallet.moves).containsExactly("-2", "+2 coins");
    }

    @Test
    void aDeferredOpenThatEndedWithoutAWindowGivesTheFeeBackOnceAndNothingBefore() {
        VaultOpenFee fee = paidOpen();
        WindowOutcome outcome = outcomeOf(fee);

        outcome.asked(true, false);
        runRefunds();
        assertThat(wallet.moves).containsExactly("-2");
        assertThat(told).isEmpty();

        outcome.settle(false);
        outcome.settle(false);
        runRefunds();

        assertThat(told).containsExactly("refused");
        assertThat(wallet.moves).containsExactly("-2", "+2 coins");
        assertThat(outcome.mayOpen()).isFalse();
        assertThat(fee.spend()).isFalse();
    }

    @Test
    void aDeferredOpenWhoseWindowCameUpKeepsTheFee() {
        WindowOutcome outcome = outcomeOf(paidOpen());

        outcome.asked(true, false);
        outcome.shown();
        outcome.settle(false);
        runRefunds();

        assertThat(told).containsExactly("opened");
        assertThat(wallet.moves).containsExactly("-2");
    }

    @Test
    void theFeeGoesBackInTheCurrencyItWasTakenIn() {
        WindowOutcome outcome = outcomeOf(paidOpen());
        wallet.charging = "gems";

        outcome.asked(false, false);
        runRefunds();

        assertThat(wallet.moves).containsExactly("-2", "+2 coins");
    }

    @Test
    void aFeeAlreadyGivenBackRulesTheWindowOutAndIsNotGivenBackAgain() {
        VaultOpenFee fee = paidOpen();
        assertThat(fee.giveBack().orElseThrow().getAsBoolean()).isTrue();
        WindowOutcome outcome = outcomeOf(fee);

        assertThat(outcome.mayOpen()).isFalse();
        assertThat(outcome.asked(false, true)).isFalse();
        runRefunds();

        assertThat(told).containsExactly("refused");
        assertThat(wallet.moves).containsExactly("-2", "+2 coins");
    }

    /** A wallet that logs each move: {@code -amount} for a fee, {@code +amount currency} for a refund. */
    private static final class Wallet implements VaultEconomy {
        private final List<String> moves = new ArrayList<>();
        private String charging = "coins";

        @Override
        public boolean canAfford(PlayerRef who, BigDecimal amount) {
            return true;
        }

        @Override
        public boolean withdraw(PlayerRef who, BigDecimal amount) {
            moves.add("-" + amount.toPlainString());
            return true;
        }

        @Override
        public boolean deposit(PlayerRef who, BigDecimal amount, String currencyId) {
            moves.add("+" + amount.toPlainString() + " " + currencyId);
            return true;
        }

        @Override
        public String currencyId() {
            return charging;
        }

        @Override
        public BigDecimal denominate(BigDecimal amount) {
            return amount;
        }
    }

    /** Nobody holds a bypass node. */
    private static final class NoPermissions implements Permissions {
        @Override
        public boolean has(PlayerRef who, String node) {
            return false;
        }

        @Override
        public QuotaResult resolveQuota(
                PlayerRef who, QuotaFamily family, @Nullable WorldRef world, long configDefault) {
            return QuotaResult.limited(configDefault);
        }
    }
}
