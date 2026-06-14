package com.uxplima.uxmessentials.migration.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;

import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.migration.convert.map.ImportedUser;
import net.milkbowl.vault.economy.AbstractEconomy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.OfflinePlayerMock;

/**
 * The Vault feed against MockBukkit: with a non-self Vault provider registered it reads every offline account
 * with a positive balance into a balance-only {@link ImportedUser}, and with no provider it reports
 * unavailable and yields nothing. Drives the same {@code getServicesManager} read path the importer uses.
 */
class VaultBalanceFeedTest {

    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-0000000000bb");
    private static final UUID BROKE = UUID.fromString("00000000-0000-0000-0000-0000000000cc");

    private ServerMock server;
    private Plugin plugin;
    private Currency currency;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        currency = Currency.builder(CurrencyId.of("coins")).build();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void readsPositiveBalancesFromARegisteredNonSelfProvider() {
        registerOffline(ALICE, "Alice");
        registerOffline(BOB, "Bob");
        registerOffline(BROKE, "Broke");
        StubEconomy eco = new StubEconomy("CoolEconomy");
        eco.balances.put("Alice", 250.0);
        eco.balances.put("Bob", 12.5);
        eco.balances.put("Broke", 0.0);
        server.getServicesManager()
                .register(net.milkbowl.vault.economy.Economy.class, eco, plugin, ServicePriority.Normal);

        VaultBalanceFeed feed = new VaultBalanceFeed(plugin, currency);

        assertThat(feed.available()).isTrue();
        List<ImportedUser> users = feed.users().toList();
        assertThat(users).hasSize(2); // the zero-balance account is skipped
        assertThat(users).extracting(u -> u.owner().uuid()).containsExactlyInAnyOrder(ALICE, BOB);
        ImportedUser alice = byUuid(users, ALICE);
        assertThat(alice.owner().name()).isEqualTo("Alice");
        assertThat(alice.homes()).isEmpty();
        assertThat(alice.mail()).isEmpty();
        assertThat(alice.balance()).contains(new BigDecimal("250.00"));
        assertThat(byUuid(users, BOB).balance()).contains(new BigDecimal("12.50"));
    }

    @Test
    void refusesAProviderThatIsUxmEssentialsItself() {
        registerOffline(ALICE, "Alice");
        StubEconomy self = new StubEconomy("uxmEssentials");
        self.balances.put("Alice", 99.0);
        server.getServicesManager()
                .register(net.milkbowl.vault.economy.Economy.class, self, plugin, ServicePriority.Normal);

        VaultBalanceFeed feed = new VaultBalanceFeed(plugin, currency);

        assertThat(feed.available()).isFalse();
        assertThat(feed.users()).isEmpty();
    }

    @Test
    void isUnavailableAndEmptyWithoutAnyProvider() {
        registerOffline(ALICE, "Alice");

        VaultBalanceFeed feed = new VaultBalanceFeed(plugin, currency);

        assertThat(feed.available()).isFalse();
        assertThat(feed.users()).isEmpty();
    }

    private void registerOffline(UUID uuid, String name) {
        server.getPlayerList().addOfflinePlayer(new OfflinePlayerMock(uuid, name));
    }

    private static ImportedUser byUuid(List<ImportedUser> users, UUID uuid) {
        return users.stream()
                .filter(u -> u.owner().uuid().equals(uuid))
                .findFirst()
                .orElseThrow();
    }

    /**
     * A minimal Vault provider keyed by player name. {@link AbstractEconomy} routes the {@code OfflinePlayer}
     * overloads through {@code getName()} to these string-keyed methods, which is all the feed touches.
     */
    // The whole legacy Vault Economy interface is deprecated; a test stub must still implement it verbatim.
    @SuppressWarnings("deprecation")
    private static final class StubEconomy extends AbstractEconomy {

        private final String name;
        private final Map<String, Double> balances = new HashMap<>();

        private StubEconomy(String name) {
            this.name = name;
        }

        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public boolean hasBankSupport() {
            return false;
        }

        @Override
        public int fractionalDigits() {
            return 2;
        }

        @Override
        public String format(double amount) {
            return Double.toString(amount);
        }

        @Override
        public String currencyNamePlural() {
            return "coins";
        }

        @Override
        public String currencyNameSingular() {
            return "coin";
        }

        @Override
        public boolean hasAccount(String playerName) {
            return balances.containsKey(playerName);
        }

        @Override
        public boolean hasAccount(String playerName, String worldName) {
            return hasAccount(playerName);
        }

        @Override
        public double getBalance(String playerName) {
            return balances.getOrDefault(playerName, 0.0);
        }

        @Override
        public double getBalance(String playerName, String world) {
            return getBalance(playerName);
        }

        @Override
        public boolean has(String playerName, double amount) {
            return getBalance(playerName) >= amount;
        }

        @Override
        public boolean has(String playerName, String worldName, double amount) {
            return has(playerName, amount);
        }

        @Override
        public EconomyResponse withdrawPlayer(String playerName, double amount) {
            return unsupported();
        }

        @Override
        public EconomyResponse withdrawPlayer(String playerName, String worldName, double amount) {
            return unsupported();
        }

        @Override
        public EconomyResponse depositPlayer(String playerName, double amount) {
            return unsupported();
        }

        @Override
        public EconomyResponse depositPlayer(String playerName, String worldName, double amount) {
            return unsupported();
        }

        @Override
        public EconomyResponse createBank(String name, String player) {
            return unsupported();
        }

        @Override
        public EconomyResponse deleteBank(String name) {
            return unsupported();
        }

        @Override
        public EconomyResponse bankBalance(String name) {
            return unsupported();
        }

        @Override
        public EconomyResponse bankHas(String name, double amount) {
            return unsupported();
        }

        @Override
        public EconomyResponse bankWithdraw(String name, double amount) {
            return unsupported();
        }

        @Override
        public EconomyResponse bankDeposit(String name, double amount) {
            return unsupported();
        }

        @Override
        public EconomyResponse isBankOwner(String name, String playerName) {
            return unsupported();
        }

        @Override
        public EconomyResponse isBankMember(String name, String playerName) {
            return unsupported();
        }

        @Override
        public List<String> getBanks() {
            return List.of();
        }

        @Override
        public boolean createPlayerAccount(String playerName) {
            return balances.putIfAbsent(playerName, 0.0) == null;
        }

        @Override
        public boolean createPlayerAccount(String playerName, String worldName) {
            return createPlayerAccount(playerName);
        }

        private EconomyResponse unsupported() {
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "stub");
        }
    }
}
