package com.uxplima.uxmessentials.economy.adapter.outbound;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.economy.application.UncountedMoney;
import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.application.port.PendingTransactionRepository;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.EconomyMessageKey;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.ranks.application.port.OwedCredits;
import com.uxplima.uxmessentials.ranks.application.port.RankEconomy;
import com.uxplima.uxmessentials.shared.adapter.outbound.AbstractProviderEconomy;
import com.uxplima.uxmessentials.shared.adapter.outbound.ChargeReceipts;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.message.MessageKey;
import org.jspecify.annotations.NullMarked;

/**
 * Bridges the ranks context's narrow {@link RankEconomy} seam to the resolved {@link EconomyProvider}, so a
 * rank's rankup cost is charged in the default currency without the ranks context ever importing an economy type
 * (mirrors {@link ProviderKitEconomy}). This is the adapter that flips the ranks {@code Optional<RankEconomy>}
 * from empty to present once economy is wired; the shared {@link AbstractProviderEconomy} supplies the guarded
 * balance-read and single-sided debit, so a concurrent spend can never double-charge. The {@link #deposit} on top
 * refunds a charge whose rankup could not be saved, and {@link #owe} queues that refund on the pending queue when the
 * deposit itself is refused, so it reaches the player on their next join in the currency it was charged in.
 */
@NullMarked
public final class ProviderRankEconomy extends AbstractProviderEconomy implements RankEconomy, OwedCredits {

    private final PendingTransactionRepository pending;

    public ProviderRankEconomy(
            EconomyProvider economy,
            Currency currency,
            Optional<ChargeReceipts> receipts,
            PendingTransactionRepository pending) {
        super(economy, currency, receipts);
        this.pending = Objects.requireNonNull(pending, "pending");
    }

    @Override
    public boolean deposit(PlayerRef who, BigDecimal amount, String currencyId) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(amount, "amount");
        Currency target = resolve(currencyId);
        // A refund gives back what was just spent, so it is not counted as earned.
        return UncountedMoney.call(() -> economy().credit(who, Money.of(target, amount)))
                .isOk();
    }

    @Override
    public void owe(PlayerRef who, BigDecimal amount, String currencyId) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(amount, "amount");
        // Resolved exactly as the charge and the deposit are, so the queued credit is in the currency that was taken.
        pending.queueCredit(who, resolve(currencyId).id().value(), amount);
    }

    @Override
    protected MessageKey chargeLabel() {
        return EconomyMessageKey.CHARGE_RANK;
    }
}
