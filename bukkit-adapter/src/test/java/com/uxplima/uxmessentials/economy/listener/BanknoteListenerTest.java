package com.uxplima.uxmessentials.economy.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.economy.adapter.inbound.listener.BanknoteListener;
import com.uxplima.uxmessentials.economy.adapter.inbound.listener.BanknoteRedeemer;
import com.uxplima.uxmessentials.economy.application.EconomyMessageKey;
import com.uxplima.uxmessentials.economy.application.EconomyNotifier;
import com.uxplima.uxmessentials.economy.application.port.BanknoteStore;
import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.TransferError;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

@SuppressWarnings({"unchecked", "deprecation"})
class BanknoteListenerTest {

    private ServerMock server;
    private Plugin plugin;
    private EconomyProvider economy;
    private EconomyNotifier notifier;
    private BanknoteStore banknoteStore;
    private Currency coins;

    private NamespacedKey valueKey;
    private NamespacedKey currencyKey;
    private NamespacedKey tokenKey;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("uxmEssentials");

        economy = mock(EconomyProvider.class);
        notifier = mock(EconomyNotifier.class);
        banknoteStore = mock(BanknoteStore.class);

        coins = mock(Currency.class);
        when(coins.id()).thenReturn(CurrencyId.of("coins"));
        when(economy.currencies()).thenReturn(java.util.Set.of(coins));

        valueKey = new NamespacedKey(plugin, "banknote_value");
        currencyKey = new NamespacedKey(plugin, "banknote_currency");
        tokenKey = new NamespacedKey(plugin, "banknote_token");
        when(notifier.amount(any())).thenReturn("$10");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private BanknoteListener newListener() {
        BanknoteRedeemer redeemer = new BanknoteRedeemer(plugin, economy, notifier, banknoteStore, mock(Logger.class));
        return new BanknoteListener(redeemer);
    }

    @Test
    void whenInteractingWithNormalPaper_doesNothing() {
        PlayerMock player = server.addPlayer("Alice");
        ItemStack paper = new ItemStack(Material.PAPER);

        BanknoteListener listener = newListener();
        PlayerInteractEvent event =
                new PlayerInteractEvent(player, Action.RIGHT_CLICK_AIR, paper, null, null, EquipmentSlot.HAND);
        event.setCancelled(false);
        listener.onInteract(event);

        assertThat(event.isCancelled()).isFalse();
        verify(banknoteStore, never()).redeem(any());
    }

    @Test
    void whenBanknoteIsInvalidOrRedeemed_sendsErrorMessage() {
        PlayerMock player = server.addPlayer("Alice");
        PlayerRef ref = BukkitRefs.toRef(player);

        ItemStack banknote = createBanknoteItem(BigDecimal.TEN, "coins", UUID.randomUUID());
        when(banknoteStore.redeem(any())).thenReturn(false);

        BanknoteListener listener = newListener();
        PlayerInteractEvent event =
                new PlayerInteractEvent(player, Action.RIGHT_CLICK_AIR, banknote, null, null, EquipmentSlot.HAND);
        listener.onInteract(event);

        assertThat(event.isCancelled()).isTrue();
        verify(notifier).send(eq(ref), eq(EconomyMessageKey.BANKNOTE_INVALID));
        verify(economy, never()).credit(any(), any());
    }

    @Test
    void whenBanknoteIsRedeemedSuccessfully_creditsPlayerWallet() {
        PlayerMock player = server.addPlayer("Alice");
        PlayerRef ref = BukkitRefs.toRef(player);

        UUID token = UUID.randomUUID();
        ItemStack banknote = createBanknoteItem(BigDecimal.TEN, "coins", token);
        when(banknoteStore.redeem(eq(token))).thenReturn(true);

        Money amount = Money.of(coins, BigDecimal.TEN);
        when(economy.credit(eq(ref), eq(amount))).thenReturn(Result.ok());

        BanknoteListener listener = newListener();
        PlayerInteractEvent event =
                new PlayerInteractEvent(player, Action.RIGHT_CLICK_AIR, banknote, null, null, EquipmentSlot.HAND);
        listener.onInteract(event);

        assertThat(event.isCancelled()).isTrue();
        verify(economy).credit(eq(ref), eq(amount));
        verify(notifier).send(eq(ref), eq(EconomyMessageKey.BANKNOTE_DEPOSITED), any());
        assertThat(banknote.getAmount()).isEqualTo(0); // Stack decremented
    }

    @Test
    void whenCreditFails_reregistersBanknote() {
        PlayerMock player = server.addPlayer("Alice");
        PlayerRef ref = BukkitRefs.toRef(player);

        UUID token = UUID.randomUUID();
        ItemStack banknote = createBanknoteItem(BigDecimal.TEN, "coins", token);
        when(banknoteStore.redeem(eq(token))).thenReturn(true);

        Money amount = Money.of(coins, BigDecimal.TEN);
        when(economy.credit(eq(ref), eq(amount))).thenReturn(Result.err(TransferError.BALANCE_MAX_EXCEEDED));

        BanknoteListener listener = newListener();
        PlayerInteractEvent event =
                new PlayerInteractEvent(player, Action.RIGHT_CLICK_AIR, banknote, null, null, EquipmentSlot.HAND);
        listener.onInteract(event);

        assertThat(event.isCancelled()).isTrue();
        verify(economy).credit(eq(ref), eq(amount));
        // Verify banknote was registered back
        verify(banknoteStore).register(any());
        assertThat(banknote.getAmount()).isEqualTo(1); // Stack not decremented
    }

    private ItemStack createBanknoteItem(BigDecimal amount, String currencyId, UUID token) {
        ItemStack banknote = new ItemStack(Material.PAPER);
        ItemMeta meta = banknote.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(valueKey, PersistentDataType.STRING, amount.toString());
        pdc.set(currencyKey, PersistentDataType.STRING, currencyId);
        pdc.set(tokenKey, PersistentDataType.STRING, token.toString());
        banknote.setItemMeta(meta);
        return banknote;
    }
}
