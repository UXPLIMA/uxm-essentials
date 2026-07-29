package com.uxplima.uxmessentials.shared.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.uxplima.uxmessentials.economy.application.EconomyMessageKey;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.Test;

/**
 * The receipt a feature emits after taking money on the side: it names the amount as the economy formats it and the
 * feature the money went to, and it stays silent when nothing actually moved.
 */
class ChargeReceiptsTest {

    private static final PlayerRef PAYER = new PlayerRef(UUID.randomUUID(), "Steve");

    private final RecordingSink sink = new RecordingSink();
    private final ChargeReceipts receipts = new ChargeReceipts(new CatalogMessages(), sink);

    @Test
    void aChargeIsReportedWithTheAmountAndWhatItWasFor() {
        receipts.charged(PAYER, Money.of(currency(), new BigDecimal("50")), EconomyMessageKey.CHARGE_WARP);

        assertThat(sink.delivered).containsExactly("Paid $50.00 for a warp");
    }

    @Test
    void theFeatureLabelIsResolvedFromTheCatalogRatherThanHardcoded() {
        receipts.charged(PAYER, Money.of(currency(), new BigDecimal("8")), EconomyMessageKey.CHARGE_KIT);

        assertThat(sink.delivered).containsExactly("Paid $8.00 for a kit");
    }

    @Test
    void blankingTheSentenceInTheCatalogTurnsReceiptsOff() {
        // Without this, an operator who empties the line gets an empty chat line per charge instead of silence.
        ChargeReceipts silenced = new ChargeReceipts(new BlankMessages(), sink);

        silenced.charged(PAYER, Money.of(currency(), new BigDecimal("50")), EconomyMessageKey.CHARGE_WARP);

        assertThat(sink.delivered).isEmpty();
    }

    @Test
    void aChargeOfNothingSaysNothing() {
        // A free warp, a waived kit price, a zero fee: no money moved, so there is no receipt to write.
        receipts.charged(PAYER, Money.of(currency(), BigDecimal.ZERO), EconomyMessageKey.CHARGE_WARP);

        assertThat(sink.delivered).isEmpty();
    }

    private static Currency currency() {
        return Currency.builder(CurrencyId.of("coins"))
                .symbol("$")
                .plural("coins")
                .precision(2)
                .build();
    }

    /** Renders the two receipt templates the way the shipped catalog does, minus the styling tags. */
    private static final class CatalogMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            String template =
                    switch (key.key()) {
                        case "eco.charged" -> "Paid {amount} for {what}";
                        case "eco.charge.warp" -> "a warp";
                        case "eco.charge.kit" -> "a kit";
                        default -> key.key();
                    };
            for (Map.Entry<String, String> placeholder : placeholders.entrySet()) {
                template = template.replace("{" + placeholder.getKey() + "}", placeholder.getValue());
            }
            return template;
        }
    }

    /** A catalog whose receipt sentence has been emptied by the operator. */
    private static final class BlankMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return "eco.charged".equals(key.key()) ? "" : key.key();
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
