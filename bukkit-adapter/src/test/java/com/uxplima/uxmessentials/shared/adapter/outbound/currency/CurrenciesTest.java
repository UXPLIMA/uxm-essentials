package com.uxplima.uxmessentials.shared.adapter.outbound.currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.adapter.outbound.hooks.EconomyQuery;
import com.uxplima.uxmessentials.shared.adapter.outbound.hooks.Hooks;
import com.uxplima.uxmessentials.shared.adapter.outbound.hooks.VaultEconomyHook;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The multi-currency façade and its providers. The routing and caching tests pin the spec grammar; the Vault
 * provider is exercised over a fake {@link EconomyQuery}; the Exp provider over a MockBukkit online player; and the
 * three reflection providers prove the load-safe contract structurally — with their plugin absent every operation
 * is a no-op, and no field or method signature on those classes names a provider SDK type, so resolving one on a
 * server without the plugin loads nothing (exactly as {@code VaultHooksTest} proves for the hooks).
 */
class CurrenciesTest {

    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final UUID GHOST = UUID.fromString("00000000-0000-0000-0000-0000000000bb");

    private ServerMock server;
    private Currencies currencies;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        // Vault is absent here, so the Vault hook resolves to EconomyQuery.ABSENT — fine for the routing/caching
        // tests, which assert the resolved provider's id, not a live balance.
        Hooks hooks = Hooks.resolve(server, SILENT, List.of(new VaultEconomyHook()));
        currencies = new Currencies(hooks, server, SILENT, "vault");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void resolve_routesEachSpecToTheRightProvider() {
        assertThat(currencies.resolve("vault").id()).isEqualTo("vault");
        assertThat(currencies.resolve("exp").id()).isEqualTo("exp");
        assertThat(currencies.resolve("playerpoints").id()).isEqualTo("playerpoints");
        assertThat(currencies.resolve("coinsengine").id()).isEqualTo("coinsengine");
        assertThat(currencies.resolve("coinsengine:gold").id()).isEqualTo("coinsengine:gold");
        assertThat(currencies.resolve("zessentials").id()).isEqualTo("zessentials");
        assertThat(currencies.resolve("zessentials:tokens").id()).isEqualTo("zessentials:tokens");

        assertThat(currencies.resolve("vault")).isInstanceOf(VaultCurrencyProvider.class);
        assertThat(currencies.resolve("exp")).isInstanceOf(ExpCurrencyProvider.class);
        assertThat(currencies.resolve("playerpoints")).isInstanceOf(PlayerPointsCurrencyProvider.class);
        assertThat(currencies.resolve("coinsengine:gold")).isInstanceOf(CoinsEngineCurrencyProvider.class);
        assertThat(currencies.resolve("zessentials:tokens")).isInstanceOf(ZEssentialsCurrencyProvider.class);
    }

    @Test
    void resolve_normalisesTheBackendHeadAndKeepsTheCurrencyName() {
        assertThat(currencies.resolve("VAULT").id()).isEqualTo("vault");
        assertThat(currencies.resolve("  Exp  ").id()).isEqualTo("exp");
        assertThat(currencies.resolve("CoinsEngine:Gold").id()).isEqualTo("coinsengine:Gold");
    }

    @Test
    void resolve_blankSpecResolvesTheConfiguredDefault() {
        assertThat(currencies.defaultCurrency()).isEqualTo("vault");
        assertThat(currencies.resolve("").id()).isEqualTo("vault");
        assertThat(currencies.resolve("   ").id()).isEqualTo("vault");
        // A blank spec and the explicit default share the one cached default provider.
        assertThat(currencies.resolve("")).isSameAs(currencies.resolve("vault"));
    }

    @Test
    void resolve_unknownSpecYieldsANoOpProvider() {
        CurrencyProvider provider = currencies.resolve("dogecoin");

        assertThat(provider.id()).isEqualTo("dogecoin");
        assertThat(provider.available()).isFalse();
        assertThatCode(() -> {
                    assertThat(provider.balance(ALICE)).isZero();
                    assertThat(provider.has(ALICE, 1)).isFalse();
                    assertThat(provider.withdraw(ALICE, 1)).isFalse();
                    assertThat(provider.deposit(ALICE, 1)).isFalse();
                })
                .doesNotThrowAnyException();
    }

    @Test
    void resolve_cachesOneProviderInstancePerSpec() {
        assertThat(currencies.resolve("vault")).isSameAs(currencies.resolve("vault"));
        assertThat(currencies.resolve("coinsengine:gold")).isSameAs(currencies.resolve("coinsengine:gold"));
        assertThat(currencies.resolve("coinsengine:gold")).isNotSameAs(currencies.resolve("coinsengine:silver"));
        assertThat(currencies.resolve("nope")).isSameAs(currencies.resolve("nope"));
    }

    @Test
    void blankDefaultCurrencyFallsBackToVault() {
        Hooks hooks = Hooks.resolve(server, SILENT, List.of(new VaultEconomyHook()));
        Currencies blankDefault = new Currencies(hooks, server, SILENT, "   ");

        assertThat(blankDefault.defaultCurrency()).isEqualTo("vault");
        assertThat(blankDefault.resolve("").id()).isEqualTo("vault");
    }

    @Test
    void vaultProvider_delegatesToTheEconomyQuery() {
        FakeEconomy economy = new FakeEconomy(true);
        economy.balances.put(ALICE, 100.0);
        VaultCurrencyProvider provider = new VaultCurrencyProvider("vault", economy);

        assertThat(provider.available()).isTrue();
        assertThat(provider.balance(ALICE)).isEqualTo(100.0);
        assertThat(provider.has(ALICE, 50)).isTrue();
        assertThat(provider.has(ALICE, 150)).isFalse();
        assertThat(provider.format(12.0)).isEqualTo("$12.0");
        assertThat(provider.withdraw(ALICE, 40)).isTrue();
        assertThat(economy.balances.get(ALICE)).isEqualTo(60.0);
        assertThat(provider.deposit(ALICE, 15)).isTrue();
        assertThat(economy.balances.get(ALICE)).isEqualTo(75.0);
    }

    @Test
    void vaultProvider_noOpsWhenTheEconomyIsUnavailable() {
        FakeEconomy economy = new FakeEconomy(false);
        economy.balances.put(ALICE, 100.0);
        VaultCurrencyProvider provider = new VaultCurrencyProvider("vault", economy);

        assertThat(provider.available()).isFalse();
        assertThat(provider.balance(ALICE)).isZero();
        assertThat(provider.has(ALICE, 1)).isFalse();
        assertThat(provider.withdraw(ALICE, 1)).isFalse();
        assertThat(provider.deposit(ALICE, 1)).isFalse();
        assertThat(economy.balances.get(ALICE)).isEqualTo(100.0);
    }

    @Test
    void expProvider_readsAndMovesAnOnlinePlayersExperience() {
        PlayerMock player = server.addPlayer();
        player.setLevel(0);
        player.setExp(0);
        ExpCurrencyProvider provider = new ExpCurrencyProvider("exp", server);
        UUID id = player.getUniqueId();

        assertThat(provider.available()).isTrue();
        assertThat(provider.balance(id)).isZero();

        assertThat(provider.deposit(id, 100)).isTrue();
        assertThat(provider.balance(id)).isEqualTo(100.0);
        assertThat(provider.has(id, 100)).isTrue();
        assertThat(provider.has(id, 101)).isFalse();

        assertThat(provider.withdraw(id, 40)).isTrue();
        assertThat(provider.balance(id)).isEqualTo(60.0);
        assertThat(provider.withdraw(id, 100)).isFalse();
        assertThat(provider.balance(id)).isEqualTo(60.0);
    }

    @Test
    void expProvider_noOpsForAnOfflinePlayer() {
        ExpCurrencyProvider provider = new ExpCurrencyProvider("exp", server);

        assertThat(provider.balance(GHOST)).isZero();
        assertThat(provider.has(GHOST, 1)).isFalse();
        assertThat(provider.withdraw(GHOST, 1)).isFalse();
        assertThat(provider.deposit(GHOST, 1)).isFalse();
    }

    @Test
    void reflectionProviders_absentPluginYieldsSafeNoOpsAndLoadNoSdkType() {
        assertReflectionProviderAbsent(currencies.resolve("playerpoints"), "org.black_ixx");
        assertReflectionProviderAbsent(currencies.resolve("coinsengine:gold"), "su.nightexpress");
        assertReflectionProviderAbsent(currencies.resolve("zessentials:tokens"), "fr.maxlego08");
    }

    private void assertReflectionProviderAbsent(CurrencyProvider provider, String sdkPackage) {
        // The plugin is not registered in the mock, so the present-guard short-circuits every call.
        assertThat(provider.available()).isFalse();
        assertThatCode(() -> {
                    assertThat(provider.balance(ALICE)).isZero();
                    assertThat(provider.has(ALICE, 1)).isFalse();
                    assertThat(provider.withdraw(ALICE, 1)).isFalse();
                    assertThat(provider.deposit(ALICE, 1)).isFalse();
                })
                .doesNotThrowAnyException();
        // Structural proof of the load-safe contract: no field or method signature on the provider (or its base)
        // names the SDK package, so loading the class on a plugin-less server pulls in none of it.
        assertThat(referencesPackage(provider.getClass(), sdkPackage)).isFalse();
    }

    /** Whether {@code type} (walking up to {@code Object}) declares any field or method signature in {@code prefix}. */
    private static boolean referencesPackage(Class<?> type, String prefix) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Method method : c.getDeclaredMethods()) {
                if (inPackage(method.getReturnType(), prefix)) {
                    return true;
                }
                for (Class<?> parameter : method.getParameterTypes()) {
                    if (inPackage(parameter, prefix)) {
                        return true;
                    }
                }
            }
            for (Field field : c.getDeclaredFields()) {
                if (inPackage(field.getType(), prefix)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean inPackage(Class<?> type, String prefix) {
        return type.getName().startsWith(prefix);
    }

    /** An in-memory {@link EconomyQuery} the Vault provider delegates to, with a toggleable availability. */
    private static final class FakeEconomy implements EconomyQuery {

        private final boolean available;
        private final Map<UUID, Double> balances = new HashMap<>();

        FakeEconomy(boolean available) {
            this.available = available;
        }

        @Override
        public boolean available() {
            return available;
        }

        @Override
        public double balance(UUID player) {
            return balances.getOrDefault(player, 0.0);
        }

        @Override
        public boolean has(UUID player, double amount) {
            return balance(player) >= amount;
        }

        @Override
        public boolean withdraw(UUID player, double amount) {
            balances.merge(player, -amount, Double::sum);
            return true;
        }

        @Override
        public boolean deposit(UUID player, double amount) {
            balances.merge(player, amount, Double::sum);
            return true;
        }

        @Override
        public String format(double amount) {
            return "$" + amount;
        }
    }

    /** A {@link Logger} that drops every line — these tests assert behaviour, not log output. */
    private static final Logger SILENT = new Logger() {
        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    };
}
