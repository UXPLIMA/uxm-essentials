package com.uxplima.uxmessentials.homes.adapter.outbound;

import java.util.Optional;

import com.uxplima.uxmessentials.economy.application.EconomyMessageKey;
import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.homes.application.port.HomeEconomy;
import com.uxplima.uxmessentials.shared.adapter.outbound.AbstractProviderEconomy;
import com.uxplima.uxmessentials.shared.adapter.outbound.ChargeReceipts;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import org.jspecify.annotations.NullMarked;

/**
 * Bridges the homes context's narrow {@link HomeEconomy} seam to the resolved {@link EconomyProvider}, so a
 * per-action {@code HomeCost} is charged in the configured currency without the homes context ever importing
 * an economy type ({@code docs/11-economy-integration.md} §4.2). This adapter flips the homes
 * {@code Optional<HomeEconomy>} from empty to present once economy is wired; when it is absent the cost is
 * recorded in config but ignored at use time, so no charge ever fires on a server without economy. The shared
 * {@link AbstractProviderEconomy} supplies the guarded balance-read and single-sided debit.
 */
@NullMarked
public final class ProviderHomeEconomy extends AbstractProviderEconomy implements HomeEconomy {

    public ProviderHomeEconomy(EconomyProvider economy, Currency currency, Optional<ChargeReceipts> receipts) {
        super(economy, currency, receipts);
    }

    @Override
    protected MessageKey chargeLabel() {
        return EconomyMessageKey.CHARGE_HOME;
    }
}
