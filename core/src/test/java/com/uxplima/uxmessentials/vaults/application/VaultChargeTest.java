package com.uxplima.uxmessentials.vaults.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.vaults.application.port.VaultEconomy;
import com.uxplima.uxmessentials.vaults.domain.VaultError;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * {@code VaultCharge} expresses the economy soft coupling: the charge is skipped entirely when the action is
 * free (zero cost), when no economy provider is wired, or when the player holds {@code uxmessentials.vault.free}
 * — and only otherwise does it consult {@link VaultEconomy}. An affordable fee is withdrawn and succeeds; an
 * unaffordable one fails with {@link VaultError#CANNOT_AFFORD} and withdraws nothing. The refund deposits only
 * when configured and the player was not bypassed. A capturing fake economy asserts the exact provider calls.
 */
class VaultChargeTest {

    private static final PlayerRef WHO = new PlayerRef(UUID.randomUUID(), "Alice");

    @Test
    void bypassNodeMakesEveryActionFreeWithoutTouchingTheEconomy() {
        CapturingEconomy economy = new CapturingEconomy(true);
        VaultCharge charge = new VaultCharge(
                allow("uxmessentials.vault.free"), Optional.of(economy), VaultChargeSettings.of(100, 50, 25));

        assertThat(charge.chargeCreate(WHO).isOk()).isTrue();
        assertThat(charge.chargeOpen(WHO).isOk()).isTrue();
        charge.refund(WHO);

        assertThat(economy.withdrawals).isEmpty();
        assertThat(economy.deposits).isEmpty();
    }

    @Test
    void absentEconomyMakesEveryActionFreeEvenWithAConfiguredCost() {
        VaultCharge charge = new VaultCharge(denyAll(), Optional.empty(), VaultChargeSettings.of(100, 50, 25));

        assertThat(charge.chargeCreate(WHO).isOk()).isTrue();
        assertThat(charge.chargeOpen(WHO).isOk()).isTrue();
        charge.refund(WHO); // no economy, nothing to deposit — must not throw
    }

    @Test
    void zeroCostSkipsTheEconomyEntirely() {
        CapturingEconomy economy = new CapturingEconomy(true);
        VaultCharge charge = new VaultCharge(denyAll(), Optional.of(economy), VaultChargeSettings.allFree());

        assertThat(charge.chargeCreate(WHO).isOk()).isTrue();
        assertThat(charge.chargeOpen(WHO).isOk()).isTrue();

        assertThat(economy.withdrawals).isEmpty();
    }

    @Test
    void anAffordableFeeIsWithdrawnAndSucceeds() {
        CapturingEconomy economy = new CapturingEconomy(true);
        VaultCharge charge = new VaultCharge(denyAll(), Optional.of(economy), VaultChargeSettings.of(100, 0, 0));

        Result<Unit, VaultError> result = charge.chargeCreate(WHO);

        assertThat(result.isOk()).isTrue();
        assertThat(economy.withdrawals).containsExactly(BigDecimal.valueOf(100.0));
    }

    @Test
    void anUnaffordableFeeFailsWithCannotAffordAndWithdrawsNothing() {
        CapturingEconomy economy = new CapturingEconomy(false);
        VaultCharge charge = new VaultCharge(denyAll(), Optional.of(economy), VaultChargeSettings.of(100, 0, 0));

        Result<Unit, VaultError> result = charge.chargeCreate(WHO);

        assertThat(result.isErr()).isTrue();
        assertThat(result.errorOrThrow()).isEqualTo(VaultError.CANNOT_AFFORD);
        assertThat(economy.withdrawals).isEmpty();
    }

    @Test
    void refundDepositsTheConfiguredAmountWhenNotBypassed() {
        CapturingEconomy economy = new CapturingEconomy(true);
        VaultCharge charge = new VaultCharge(denyAll(), Optional.of(economy), VaultChargeSettings.of(0, 0, 25));

        charge.refund(WHO);

        assertThat(economy.deposits).containsExactly(BigDecimal.valueOf(25.0));
    }

    @Test
    void refundIsAnoOpWhenTheRefundIsZero() {
        CapturingEconomy economy = new CapturingEconomy(true);
        VaultCharge charge = new VaultCharge(denyAll(), Optional.of(economy), VaultChargeSettings.of(100, 50, 0));

        charge.refund(WHO);

        assertThat(economy.deposits).isEmpty();
    }

    private static Permissions denyAll() {
        return allow("");
    }

    /** A {@code Permissions} that grants only {@code grantedNode} and resolves no quotas. */
    private static Permissions allow(String grantedNode) {
        return new Permissions() {
            @Override
            public boolean has(PlayerRef who, String node) {
                return !grantedNode.isEmpty() && grantedNode.equals(node);
            }

            @Override
            public QuotaResult resolveQuota(
                    PlayerRef who, QuotaFamily family, @Nullable WorldRef world, long configDefault) {
                return QuotaResult.limited(configDefault);
            }
        };
    }

    /** A {@link VaultEconomy} recording every withdraw/deposit and answering {@code canAfford} from a flag. */
    private static final class CapturingEconomy implements VaultEconomy {
        private final boolean canAfford;
        private final List<BigDecimal> withdrawals = new ArrayList<>();
        private final List<BigDecimal> deposits = new ArrayList<>();

        private CapturingEconomy(boolean canAfford) {
            this.canAfford = canAfford;
        }

        @Override
        public boolean canAfford(PlayerRef who, BigDecimal amount) {
            return canAfford;
        }

        @Override
        public void withdraw(PlayerRef who, BigDecimal amount) {
            withdrawals.add(amount);
        }

        @Override
        public void deposit(PlayerRef who, BigDecimal amount) {
            deposits.add(amount);
        }
    }
}
