package com.uxplima.uxmessentials.economy.adapter.outbound;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.economy.application.EconomyMessageKey;
import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.shared.adapter.outbound.AbstractProviderEconomy;
import com.uxplima.uxmessentials.shared.adapter.outbound.ChargeReceipts;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.trade.application.port.TradeEconomy;
import org.jspecify.annotations.NullMarked;

/**
 * Bridges the trade context's narrow {@link TradeEconomy} seam to the resolved {@link EconomyProvider}, so a trade's
 * staked money moves without the trade context ever importing an economy type (mirrors {@link ProviderKitEconomy} /
 * {@link ProviderRankEconomy}). This is the adapter that flips the trade {@code Optional<TradeEconomy>} from empty to
 * present once economy is wired.
 *
 * <p>The shared {@link AbstractProviderEconomy} supplies {@code canAfford} (the balance read for the window preview)
 * and {@code withdraw} (the guarded escrow debit). Trade adds two moves of its own on top: {@link #transfer} is the
 * guarded, atomic two-sided move that never partially applies, so two concurrent settlements can never both overdraw,
 * and a payer who cannot cover the leg is reported by a {@code false} return rather than a check-then-charge race;
 * {@link #deposit} is the delivery/refund credit, logged when the provider refuses it.
 */
@NullMarked
public final class ProviderTradeEconomy extends AbstractProviderEconomy implements TradeEconomy {

    private final Logger logger;

    public ProviderTradeEconomy(
            EconomyProvider economy, Currency defaultCurrency, Logger logger, Optional<ChargeReceipts> receipts) {
        super(economy, defaultCurrency, receipts);
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public boolean transfer(PlayerRef from, PlayerRef to, BigDecimal amount, String currencyId) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(amount, "amount");
        Currency target = resolve(currencyId);
        return economy().transfer(from, to, Money.of(target, amount)).isOk();
    }

    @Override
    public void deposit(PlayerRef who, BigDecimal amount, String currencyId) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(amount, "amount");
        Currency target = resolve(currencyId);
        if (economy().credit(who, Money.of(target, amount)).isErr()) {
            logger.warn(
                    "cross-server trade deposit of {} {} to {} was refused by the economy provider",
                    amount,
                    target.id().value(),
                    who.name());
        }
    }

    @Override
    protected MessageKey chargeLabel() {
        return EconomyMessageKey.CHARGE_TRADE;
    }
}
