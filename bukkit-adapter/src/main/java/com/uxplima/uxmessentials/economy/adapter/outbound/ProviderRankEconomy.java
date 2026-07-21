package com.uxplima.uxmessentials.economy.adapter.outbound;

import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.ranks.application.port.RankEconomy;
import com.uxplima.uxmessentials.shared.adapter.outbound.AbstractProviderEconomy;
import org.jspecify.annotations.NullMarked;

/**
 * Bridges the ranks context's narrow {@link RankEconomy} seam to the resolved {@link EconomyProvider}, so a
 * rank's rankup cost is charged in the default currency without the ranks context ever importing an economy type
 * (mirrors {@link ProviderKitEconomy}). This is the adapter that flips the ranks {@code Optional<RankEconomy>}
 * from empty to present once economy is wired; the shared {@link AbstractProviderEconomy} supplies the guarded
 * balance-read and single-sided debit, so a concurrent spend can never double-charge.
 */
@NullMarked
public final class ProviderRankEconomy extends AbstractProviderEconomy implements RankEconomy {

    public ProviderRankEconomy(EconomyProvider economy, Currency currency) {
        super(economy, currency);
    }
}
