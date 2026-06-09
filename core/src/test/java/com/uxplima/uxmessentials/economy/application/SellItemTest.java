package com.uxplima.uxmessentials.economy.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import com.uxplima.uxmessentials.economy.domain.CurrencyRegistry;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.fakes.CapturingSink;
import com.uxplima.uxmessentials.economy.fakes.Currencies;
import com.uxplima.uxmessentials.economy.fakes.InMemoryWalletRepository;
import com.uxplima.uxmessentials.economy.fakes.InMemoryWorthOverrideStore;
import com.uxplima.uxmessentials.economy.fakes.KeyMessages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@code /sell}: convert held items into currency at their configured worth. A priced material credits the
 * seller the stack value and reports the sale; an unpriced material is refused before any credit. The credit
 * goes through the {@link com.uxplima.uxmessentials.economy.application.port.EconomyProvider}, never a PDC
 * stamp, so the proceeds survive a world rollback like every other balance.
 */
class SellItemTest {

    private InMemoryWalletRepository repo;
    private CapturingSink sink;
    private PlayerRef seller;

    @BeforeEach
    void setUp() {
        repo = new InMemoryWalletRepository();
        sink = new CapturingSink();
        seller = new PlayerRef(UUID.randomUUID(), "Alice");
    }

    private SellItem sellWith(WorthSource worth) {
        CurrencyRegistry registry = CurrencyRegistry.single(Currencies.COINS);
        Clock clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC);
        NativeEconomyProvider provider = new NativeEconomyProvider(repo, registry, clock);
        EconomyNotifier notifier = new EconomyNotifier(new KeyMessages(), sink);
        return new SellItem(provider, worth, notifier, Currencies.COINS, registry.all());
    }

    @Test
    void unpricedMaterialIsRefusedWithoutCredit() {
        SellItem sell = sellWith(WorthTable.empty());

        SellOutcome outcome = sell.sell(seller, "emerald", 3);

        assertThat(outcome.sold()).isFalse();
        assertThat(sink.delivered("wallet.sell-not-sellable")).isTrue();
        assertThat(repo.findByOwner(seller)).isEmpty();
    }

    @Test
    void pricedStackCreditsTheStackValue() {
        SellItem sell = sellWith(new WorthTable(Map.of("diamond", Worth.of(new BigDecimal("10"), "coins"))));

        SellOutcome outcome = sell.sell(seller, "diamond", 4);

        assertThat(outcome.sold()).isTrue();
        assertThat(outcome.earned()).contains(Money.of(Currencies.COINS, 40));
        assertThat(repo.findByOwner(seller).orElseThrow().balanceOf(Currencies.COINS))
                .isEqualTo(Money.of(Currencies.COINS, 40));
        assertThat(sink.delivered("wallet.sell-sold")).isTrue();
    }

    @Test
    void anOverridePriceChangesTheProceeds() {
        InMemoryWorthOverrideStore overrides = new InMemoryWorthOverrideStore();
        overrides.set("diamond", new BigDecimal("25"));
        WorthSource worth = new CombiningWorthSource(
                overrides, new WorthTable(Map.of("diamond", Worth.of(new BigDecimal("10"), "coins"))), "coins");
        SellItem sell = sellWith(worth);

        SellOutcome outcome = sell.sell(seller, "diamond", 4);

        assertThat(outcome.earned()).contains(Money.of(Currencies.COINS, 100));
    }

    @Test
    void sellingZeroIsNothingToSell() {
        SellItem sell = sellWith(new WorthTable(Map.of("diamond", Worth.of(new BigDecimal("10"), "coins"))));

        SellOutcome outcome = sell.sell(seller, "diamond", 0);

        assertThat(outcome.sold()).isFalse();
        assertThat(sink.delivered("wallet.sell-nothing")).isTrue();
    }
}
