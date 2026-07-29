package com.uxplima.uxmessentials.economy.adapter.outbound;

import java.util.Optional;

import com.uxplima.uxmessentials.economy.application.EconomyMessageKey;
import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.shared.adapter.outbound.AbstractProviderEconomy;
import com.uxplima.uxmessentials.shared.adapter.outbound.ChargeReceipts;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.ClickActionEconomy;
import org.jspecify.annotations.NullMarked;

/**
 * Bridges the shared {@link ClickActionEconomy} seam to the resolved {@link EconomyProvider}, so a {@code COST}
 * click action charges the clicking viewer in the resolved currency without the owning context ever importing an
 * economy type (mirrors {@link ProviderWarpEconomy} and {@link ProviderKitEconomy}). This is the adapter that
 * flips the npc {@code Optional<ClickActionEconomy>} from empty to present once economy is wired; the shared
 * {@link AbstractProviderEconomy} supplies the guarded single-sided debit, so a concurrent spend can never
 * double-charge.
 */
@NullMarked
public final class ProviderNpcEconomy extends AbstractProviderEconomy implements ClickActionEconomy {

    public ProviderNpcEconomy(EconomyProvider economy, Currency currency, Optional<ChargeReceipts> receipts) {
        super(economy, currency, receipts);
    }

    @Override
    protected MessageKey chargeLabel() {
        return EconomyMessageKey.CHARGE_ACTION;
    }
}
