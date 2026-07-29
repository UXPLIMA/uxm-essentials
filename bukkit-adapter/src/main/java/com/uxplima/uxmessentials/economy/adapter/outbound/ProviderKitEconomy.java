package com.uxplima.uxmessentials.economy.adapter.outbound;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.economy.application.EconomyMessageKey;
import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.kits.application.port.KitEconomy;
import com.uxplima.uxmessentials.shared.adapter.outbound.AbstractProviderEconomy;
import com.uxplima.uxmessentials.shared.adapter.outbound.ChargeReceipts;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Bridges the kits context's narrow {@link KitEconomy} seam to the resolved {@link EconomyProvider}, so a
 * per-kit cost is charged in the resolved currency without the kits context ever importing an economy type
 * (mirrors {@link ProviderWarpEconomy}). This is the adapter that flips the kits {@code Optional<KitEconomy>}
 * from empty to present once economy is wired. The shared {@link AbstractProviderEconomy} supplies the balance
 * read and the guarded single-sided debit; kits adds the delete-side {@link #deposit} on top, which credits
 * through the same resolved currency.
 */
@NullMarked
public final class ProviderKitEconomy extends AbstractProviderEconomy implements KitEconomy {

    public ProviderKitEconomy(EconomyProvider economy, Currency currency, Optional<ChargeReceipts> receipts) {
        super(economy, currency, receipts);
    }

    @Override
    public boolean deposit(PlayerRef who, BigDecimal amount, String currencyId) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(amount, "amount");
        Currency target = resolve(currencyId);
        return economy().credit(who, Money.of(target, amount)).isOk();
    }

    @Override
    protected MessageKey chargeLabel() {
        return EconomyMessageKey.CHARGE_KIT;
    }
}
