package com.uxplima.uxmessentials.shared.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.economy.adapter.outbound.ProviderWarpEconomy;
import com.uxplima.uxmessentials.economy.application.port.BaltopRow;
import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.TransferError;
import com.uxplima.uxmessentials.economy.domain.TransferResult;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import org.junit.jupiter.api.Test;

/**
 * The receipt behaviour every feature seam inherits from the shared bridge, exercised through the warps seam: a
 * debit that takes is reported to the payer, and a debit that is refused is not (the feature answers that itself,
 * and no money moved to explain).
 */
class AbstractProviderEconomyReceiptTest {

    private static final PlayerRef PAYER = new PlayerRef(UUID.randomUUID(), "Steve");
    private static final Currency COINS = Currency.builder(CurrencyId.of("coins"))
            .symbol("$")
            .plural("coins")
            .precision(2)
            .build();

    private final RecordingSink sink = new RecordingSink();

    @Test
    void aChargeThatTakesIsReportedToThePayer() {
        ProviderWarpEconomy economy = seam(true);

        assertThat(economy.withdraw(PAYER, new BigDecimal("50"), "default")).isTrue();

        assertThat(sink.delivered).containsExactly("eco.charged");
    }

    @Test
    void aRefusedChargeIsNotReported() {
        ProviderWarpEconomy economy = seam(false);

        assertThat(economy.withdraw(PAYER, new BigDecimal("50"), "default")).isFalse();

        assertThat(sink.delivered).isEmpty();
    }

    private ProviderWarpEconomy seam(boolean sufficient) {
        return new ProviderWarpEconomy(
                new FixedProvider(sufficient), COINS, Optional.of(new ChargeReceipts(new KeyMessages(), sink)));
    }

    /** An economy provider whose debit either takes or is refused, with no state behind it. */
    private record FixedProvider(boolean sufficient) implements EconomyProvider {
        @Override
        public Money balance(PlayerRef who, Currency currency) {
            return Money.of(currency, BigDecimal.ZERO);
        }

        @Override
        public Result<Unit, TransferError> credit(PlayerRef who, Money amount) {
            return Result.ok();
        }

        @Override
        public Result<Unit, TransferError> debit(PlayerRef who, Money amount) {
            return sufficient ? Result.ok() : Result.err(TransferError.INSUFFICIENT_FUNDS);
        }

        @Override
        public TransferResult transfer(PlayerRef from, PlayerRef to, Money amount) {
            // This fake only answers debits; a two-sided move has no funds behind it here.
            return TransferResult.insufficientFunds(amount, Money.of(amount.currency(), BigDecimal.ZERO));
        }

        @Override
        public List<BaltopRow> top(Currency currency, int limit) {
            return List.of();
        }

        @Override
        public void ensureAccount(PlayerRef owner, Currency currency) {
            // nothing to materialise: this provider holds no rows
        }

        @Override
        public boolean hasAccount(PlayerRef owner, Currency currency) {
            return true;
        }

        @Override
        public Set<Currency> currencies() {
            return Set.of(COINS);
        }
    }

    /** Renders each template to its own key, so the assertion reads which line was sent, not how it is worded. */
    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    private static final class RecordingSink implements MessageSink {
        private final List<String> delivered = new ArrayList<>();

        @Override
        public void deliver(PlayerRef viewer, String renderedText) {
            delivered.add(renderedText);
        }
    }
}
