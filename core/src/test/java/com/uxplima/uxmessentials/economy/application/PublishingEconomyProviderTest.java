package com.uxplima.uxmessentials.economy.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.economy.application.port.CurrencyBackendRegistry;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.CurrencyRegistry;
import com.uxplima.uxmessentials.economy.domain.EconomyError;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.event.WalletCredited;
import com.uxplima.uxmessentials.economy.domain.event.WalletDebited;
import com.uxplima.uxmessentials.economy.domain.event.WalletRejected;
import com.uxplima.uxmessentials.economy.fakes.RecordingLogger;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Every change of money raises the event the economy publishes for it.
 *
 * <p>None did. The wallet aggregate that raises them was never on the path money takes: the ledger moves money in one
 * guarded statement, so {@code UxmWalletCreditEvent}, {@code UxmWalletDebitEvent} and {@code UxmWalletRejectEvent}
 * were in the developer API and never fired. A ledger plugin listening for them heard nothing, and nor did the REST
 * event stream.
 */
class PublishingEconomyProviderTest {

    private static final Instant NOW = Instant.parse("2026-10-01T10:00:00Z");
    private static final Currency COINS =
            Currency.builder(CurrencyId.of("coins")).build();
    private static final PlayerRef ALICE = new PlayerRef(UUID.randomUUID(), "Alice");
    private static final PlayerRef BOB = new PlayerRef(UUID.randomUUID(), "Bob");

    private final List<DomainEvent> published = new ArrayList<>();
    private final FakeCurrencyBackend backend = new FakeCurrencyBackend("native");
    private final PublishingEconomyProvider provider = new PublishingEconomyProvider(
            new RoutingEconomyProvider(
                    CurrencyBackendRegistry.of(List.of(backend)),
                    CurrencyRegistry.of(List.of(COINS), CurrencyId.of("coins")),
                    Clock.fixed(NOW, ZoneOffset.UTC),
                    new RecordingLogger()),
            published::add,
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    @DisplayName("a credit raises WalletCredited with the balance it left")
    void aCreditIsPublished() {
        backend.seed(ALICE, new BigDecimal("10"));

        provider.credit(ALICE, coins("5"));

        assertThat(published).singleElement().isInstanceOfSatisfying(WalletCredited.class, credited -> {
            assertThat(credited.owner()).isEqualTo(ALICE);
            assertThat(credited.amount().amount()).isEqualByComparingTo("5");
            assertThat(credited.resulting().amount()).isEqualByComparingTo("15");
            assertThat(credited.occurredAt()).isEqualTo(NOW);
        });
    }

    @Test
    @DisplayName("a debit raises WalletDebited, and one it cannot cover raises WalletRejected")
    void aDebitAndARefusalArePublished() {
        backend.seed(ALICE, new BigDecimal("10"));

        provider.debit(ALICE, coins("4"));
        provider.debit(ALICE, coins("100"));

        assertThat(published).hasSize(2);
        assertThat(published.get(0))
                .isInstanceOfSatisfying(
                        WalletDebited.class,
                        debited -> assertThat(debited.resulting().amount()).isEqualByComparingTo("6"));
        assertThat(published.get(1)).isInstanceOfSatisfying(WalletRejected.class, rejected -> {
            assertThat(rejected.requested().amount()).isEqualByComparingTo("100");
            assertThat(rejected.available().amount()).isEqualByComparingTo("6");
            assertThat(rejected.reason()).isEqualTo(EconomyError.INSUFFICIENT_FUNDS);
        });
    }

    @Test
    @DisplayName("a transfer raises the debit of the payer and the credit of the payee")
    void aTransferIsPublishedOnBothSides() {
        backend.seed(ALICE, new BigDecimal("10"));

        provider.transfer(ALICE, BOB, coins("3"));

        assertThat(published).hasSize(2);
        assertThat(published.get(0))
                .isInstanceOfSatisfying(
                        WalletDebited.class,
                        debited -> assertThat(debited.owner()).isEqualTo(ALICE));
        assertThat(published.get(1)).isInstanceOfSatisfying(WalletCredited.class, credited -> {
            assertThat(credited.owner()).isEqualTo(BOB);
            assertThat(credited.resulting().amount()).isEqualByComparingTo("3");
        });
    }

    @Test
    @DisplayName("a transfer refused for want of money raises WalletRejected, and a self payment raises nothing")
    void refusedTransfers() {
        backend.seed(ALICE, new BigDecimal("1"));

        provider.transfer(ALICE, BOB, coins("3"));
        provider.transfer(ALICE, ALICE, coins("1"));

        assertThat(published).singleElement().isInstanceOf(WalletRejected.class);
    }

    private static Money coins(String amount) {
        return Money.of(COINS, new BigDecimal(amount));
    }
}
