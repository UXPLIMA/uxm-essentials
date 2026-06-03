package com.uxplima.uxmessentials.economy.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.uxplima.uxmessentials.economy.domain.CurrencyRegistry;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.fakes.CapturingSink;
import com.uxplima.uxmessentials.economy.fakes.Currencies;
import com.uxplima.uxmessentials.economy.fakes.InMemoryWalletRepository;
import com.uxplima.uxmessentials.economy.fakes.KeyMessages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@code /sellall}: sell every priced material in the inventory snapshot in one credit. Unpriced materials are
 * left in place (no spam, not reported for removal), the proceeds are the sum of every priced stack, and an
 * inventory with nothing priced is refused before any credit.
 */
class SellAllTest {

    private InMemoryWalletRepository repo;
    private CapturingSink sink;
    private PlayerRef seller;

    @BeforeEach
    void setUp() {
        repo = new InMemoryWalletRepository();
        sink = new CapturingSink();
        seller = new PlayerRef(UUID.randomUUID(), "Alice");
    }

    private SellAll sellAllWith(WorthTable table) {
        CurrencyRegistry registry = CurrencyRegistry.single(Currencies.COINS);
        Clock clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC);
        NativeEconomyProvider provider = new NativeEconomyProvider(repo, registry, clock);
        EconomyNotifier notifier = new EconomyNotifier(new KeyMessages(), sink);
        return new SellAll(provider, table, notifier, Currencies.COINS);
    }

    @Test
    void sellsOnlyPricedMaterialsAndCreditsTheTotal() {
        SellAll sellAll =
                sellAllWith(new WorthTable(Map.of("diamond", new BigDecimal("10"), "iron_ingot", new BigDecimal("2"))));
        Map<String, Integer> inventory = new LinkedHashMap<>();
        inventory.put("diamond", 4);
        inventory.put("iron_ingot", 5);
        inventory.put("dirt", 64);

        SellAllOutcome outcome = sellAll.sellAll(seller, inventory);

        assertThat(outcome.earned()).contains(Money.of(Currencies.COINS, 50));
        assertThat(outcome.sold()).containsOnlyKeys("diamond", "iron_ingot");
        assertThat(repo.findByOwner(seller).orElseThrow().balanceOf(Currencies.COINS))
                .isEqualTo(Money.of(Currencies.COINS, 50));
        assertThat(sink.delivered("wallet.sellall-summary")).isTrue();
    }

    @Test
    void nothingPricedIsRefusedWithoutCredit() {
        SellAll sellAll = sellAllWith(WorthTable.empty());

        SellAllOutcome outcome = sellAll.sellAll(seller, Map.of("dirt", 64, "cobblestone", 32));

        assertThat(outcome.sold()).isEmpty();
        assertThat(outcome.earned()).isEmpty();
        assertThat(repo.findByOwner(seller)).isEmpty();
        assertThat(sink.delivered("wallet.sell-nothing")).isTrue();
    }
}
