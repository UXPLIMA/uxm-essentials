package com.uxplima.uxmessentials.economy.adapter.outbound;

import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.shared.adapter.outbound.AbstractProviderEconomy;
import com.uxplima.uxmessentials.warps.application.port.WarpEconomy;
import org.jspecify.annotations.NullMarked;

/**
 * Bridges the warps context's narrow {@link WarpEconomy} seam to the resolved {@link EconomyProvider}, so a
 * per-warp {@code WarpCost} is charged in the default currency without the warps context ever importing an
 * economy type ({@code docs/11-economy-integration.md} §4.2, the warps {@code WarpEconomy} contract). This is
 * the adapter that flips the warps {@code Optional<WarpEconomy>} from empty to present once economy is wired; the
 * shared {@link AbstractProviderEconomy} supplies the guarded balance-read and single-sided debit, so a
 * concurrent spend can never double-charge.
 */
@NullMarked
public final class ProviderWarpEconomy extends AbstractProviderEconomy implements WarpEconomy {

    public ProviderWarpEconomy(EconomyProvider economy, Currency currency) {
        super(economy, currency);
    }
}
