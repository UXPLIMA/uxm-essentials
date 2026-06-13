package com.uxplima.uxmessentials.homes.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.homes.application.port.HomeEconomy;
import com.uxplima.uxmessentials.homes.domain.HomeCost;
import com.uxplima.uxmessentials.homes.domain.HomeError;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the {@link HomeCharge} gate: verifies the four skip-charge cases (free cost, absent
 * economy, bypass permission) and the two charge outcomes (affordable, unaffordable).
 */
class HomeChargeTest {

    private static final PlayerRef WHO = new PlayerRef(UUID.randomUUID(), "Alice");
    private static final HomeCost COST = HomeCost.of(new BigDecimal("10.00"));

    // --- skip-charge cases ---

    @Test
    void freeCostSkipsWithdrawAndSucceeds() {
        TrackingEconomy economy = new TrackingEconomy(true);
        HomeCharge charge = new HomeCharge(neverBypass(), Optional.of(economy), allFreeSettings());

        Result<Unit, HomeError> result = charge.charge(WHO, HomeChargeKind.CREATE);

        assertThat(result.isOk()).isTrue();
        assertThat(economy.withdrawCalled).isFalse();
    }

    @Test
    void absentEconomySkipsWithdrawAndSucceeds() {
        HomeCharge charge = new HomeCharge(neverBypass(), Optional.empty(), uniformCostSettings(COST));

        Result<Unit, HomeError> result = charge.charge(WHO, HomeChargeKind.CREATE);

        assertThat(result.isOk()).isTrue();
    }

    @Test
    void bypassPermissionSkipsWithdrawAndSucceeds() {
        TrackingEconomy economy = new TrackingEconomy(true);
        HomeCharge charge = new HomeCharge(alwaysBypass(), Optional.of(economy), uniformCostSettings(COST));

        Result<Unit, HomeError> result = charge.charge(WHO, HomeChargeKind.TELEPORT);

        assertThat(result.isOk()).isTrue();
        assertThat(economy.withdrawCalled).isFalse();
    }

    // --- charge outcomes ---

    @Test
    void affordableChargeWithdrawsAndSucceeds() {
        TrackingEconomy economy = new TrackingEconomy(true);
        HomeCharge charge = new HomeCharge(neverBypass(), Optional.of(economy), uniformCostSettings(COST));

        Result<Unit, HomeError> result = charge.charge(WHO, HomeChargeKind.CREATE);

        assertThat(result.isOk()).isTrue();
        assertThat(economy.withdrawCalled).isTrue();
        assertThat(economy.lastAmount).isEqualByComparingTo(COST.amount());
        assertThat(economy.lastCurrencyId).isEqualTo(COST.currencyId());
    }

    @Test
    void unaffordableChargeReturnsCannotAfford() {
        TrackingEconomy economy = new TrackingEconomy(false);
        HomeCharge charge = new HomeCharge(neverBypass(), Optional.of(economy), uniformCostSettings(COST));

        Result<Unit, HomeError> result = charge.charge(WHO, HomeChargeKind.RELOCATE);

        assertThat(result.errorOrThrow()).isEqualTo(HomeError.CANNOT_AFFORD);
    }

    // --- helpers ---

    private static HomeChargeSettings allFreeSettings() {
        return HomeChargeSettings.allFree();
    }

    private static HomeChargeSettings uniformCostSettings(HomeCost cost) {
        return new HomeChargeSettings(cost, cost, cost);
    }

    private static Permissions neverBypass() {
        return new StubPermissions(false);
    }

    private static Permissions alwaysBypass() {
        return new StubPermissions(true);
    }

    /** A {@link Permissions} stub that always returns the same value for {@code has}. */
    private static final class StubPermissions implements Permissions {
        private final boolean bypass;

        StubPermissions(boolean bypass) {
            this.bypass = bypass;
        }

        @Override
        public boolean has(PlayerRef who, String node) {
            return bypass;
        }

        @Override
        public QuotaResult resolveQuota(
                PlayerRef who, QuotaFamily family, @Nullable WorldRef world, long configDefault) {
            return QuotaResult.limited(configDefault);
        }
    }

    /** A {@link HomeEconomy} fake that records the last withdraw call and returns a fixed outcome. */
    private static final class TrackingEconomy implements HomeEconomy {
        private final boolean canAfford;
        boolean withdrawCalled;

        @Nullable BigDecimal lastAmount;

        @Nullable String lastCurrencyId;

        TrackingEconomy(boolean canAfford) {
            this.canAfford = canAfford;
        }

        @Override
        public boolean canAfford(PlayerRef who, BigDecimal amount, String currencyId) {
            return canAfford;
        }

        @Override
        public boolean withdraw(PlayerRef who, BigDecimal amount, String currencyId) {
            withdrawCalled = true;
            lastAmount = amount;
            lastCurrencyId = currencyId;
            return canAfford;
        }
    }
}
