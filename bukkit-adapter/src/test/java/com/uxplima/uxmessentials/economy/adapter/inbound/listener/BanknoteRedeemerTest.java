package com.uxplima.uxmessentials.economy.adapter.inbound.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.Set;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.economy.application.EconomyMessageKey;
import com.uxplima.uxmessentials.economy.application.EconomyNotifier;
import com.uxplima.uxmessentials.economy.application.port.BanknoteStore;
import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.TransferError;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class BanknoteRedeemerTest {

    private ServerMock server;
    private Plugin plugin;
    private EconomyProvider economy;
    private EconomyNotifier notifier;
    private BanknoteStore store;
    private Logger log;
    private Currency coins;
    private BanknoteRedeemer redeemer;
    private NamespacedKey valueKey;
    private NamespacedKey currencyKey;
    private NamespacedKey tokenKey;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("uxmEssentials");
        economy = mock(EconomyProvider.class);
        notifier = mock(EconomyNotifier.class);
        store = mock(BanknoteStore.class);
        log = mock(Logger.class);

        coins = mock(Currency.class);
        when(coins.id()).thenReturn(CurrencyId.of("coins"));
        when(economy.currencies()).thenReturn(Set.of(coins));
        when(notifier.amount(any())).thenReturn("10");

        valueKey = new NamespacedKey(plugin, "banknote_value");
        currencyKey = new NamespacedKey(plugin, "banknote_currency");
        tokenKey = new NamespacedKey(plugin, "banknote_token");

        redeemer = new BanknoteRedeemer(plugin, economy, notifier, store, log);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private ItemStack banknote(UUID token, String value, int amount) {
        ItemStack item = new ItemStack(Material.PAPER, amount);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(valueKey, PersistentDataType.STRING, value);
        meta.getPersistentDataContainer().set(currencyKey, PersistentDataType.STRING, "coins");
        meta.getPersistentDataContainer().set(tokenKey, PersistentDataType.STRING, token.toString());
        item.setItemMeta(meta);
        return item;
    }

    @Test
    void successfulRedeem_consumesOneNote_andCredits() {
        UUID token = UUID.randomUUID();
        PlayerMock player = server.addPlayer("Alice");
        PlayerRef owner = new PlayerRef(player.getUniqueId(), "Alice");
        ItemStack note = banknote(token, "10", 1);
        when(store.redeem(token)).thenReturn(true);
        when(economy.credit(eq(owner), any())).thenReturn(Result.ok());

        redeemer.redeem(player, owner, note);

        assertThat(note.getAmount()).isZero();
        verify(economy).credit(eq(owner), any(Money.class));
        verify(store, never()).register(any());
        verify(notifier).send(eq(owner), eq(EconomyMessageKey.BANKNOTE_DEPOSITED), any());
    }

    @Test
    void creditFailure_restoresItem_andReRegistersToken() {
        UUID token = UUID.randomUUID();
        PlayerMock player = server.addPlayer("Alice");
        PlayerRef owner = new PlayerRef(player.getUniqueId(), "Alice");
        ItemStack note = banknote(token, "10", 1);
        when(store.redeem(token)).thenReturn(true);
        when(economy.credit(eq(owner), any())).thenReturn(Result.err(TransferError.BALANCE_MAX_EXCEEDED));

        redeemer.redeem(player, owner, note);

        // Item conserved: amount restored to its prior value
        assertThat(note.getAmount()).isEqualTo(1);
        // Token re-registered so the note stays depositable
        verify(store).register(any());
        verify(notifier).send(eq(owner), eq(EconomyMessageKey.BANKNOTE_INVALID));
    }

    @Test
    void creditThrows_restoresItem_andReRegistersToken() {
        UUID token = UUID.randomUUID();
        PlayerMock player = server.addPlayer("Alice");
        PlayerRef owner = new PlayerRef(player.getUniqueId(), "Alice");
        ItemStack note = banknote(token, "10", 1);
        when(store.redeem(token)).thenReturn(true);
        when(economy.credit(eq(owner), any())).thenThrow(new IllegalStateException("provider down"));

        redeemer.redeem(player, owner, note);

        assertThat(note.getAmount()).isEqualTo(1);
        verify(store).register(any());
    }

    @Test
    void alreadyRedeemedToken_doesNotConsumeItem() {
        UUID token = UUID.randomUUID();
        PlayerMock player = server.addPlayer("Alice");
        PlayerRef owner = new PlayerRef(player.getUniqueId(), "Alice");
        ItemStack note = banknote(token, "10", 1);
        when(store.redeem(token)).thenReturn(false);

        redeemer.redeem(player, owner, note);

        // Redeem returned false: item restored, nothing credited, nothing re-registered
        assertThat(note.getAmount()).isEqualTo(1);
        verify(economy, never()).credit(any(), any());
        verify(store, never()).register(any());
        verify(notifier).send(eq(owner), eq(EconomyMessageKey.BANKNOTE_INVALID));
    }
}
