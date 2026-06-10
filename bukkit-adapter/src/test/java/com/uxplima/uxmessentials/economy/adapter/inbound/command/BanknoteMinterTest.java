package com.uxplima.uxmessentials.economy.adapter.inbound.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.economy.application.port.BanknoteStore;
import com.uxplima.uxmessentials.economy.domain.Banknote;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class BanknoteMinterTest {

    private Plugin plugin;
    private BanknoteStore banknoteStore;
    private Messages messages;
    private Currency coins;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("uxmEssentials");
        banknoteStore = mock(BanknoteStore.class);
        messages = mock(Messages.class);
        when(messages.resolve(any(PlayerRef.class), any(), any())).thenReturn("<gray>banknote</gray>");
        coins = mock(Currency.class);
        when(coins.id()).thenReturn(CurrencyId.of("coins"));
        when(coins.normalize(any(BigDecimal.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void mintsAPaperNoteCarryingValueCurrencyAndTokenAndRegistersTheToken() {
        BanknoteMinter minter = new BanknoteMinter(plugin, messages, banknoteStore);
        PlayerRef recipient = new PlayerRef(java.util.UUID.randomUUID(), "Alice");
        Money amount = Money.of(coins, new BigDecimal("250"));

        ItemStack note = minter.mint(recipient, amount);

        assertThat(note.getType()).isEqualTo(Material.PAPER);
        ItemMeta meta = note.getItemMeta();
        assertThat(meta).isNotNull();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        assertThat(pdc.get(new NamespacedKey(plugin, "banknote_value"), PersistentDataType.STRING))
                .isEqualTo("250");
        assertThat(pdc.get(new NamespacedKey(plugin, "banknote_currency"), PersistentDataType.STRING))
                .isEqualTo("coins");
        assertThat(pdc.get(new NamespacedKey(plugin, "banknote_token"), PersistentDataType.STRING))
                .isNotBlank();

        // The anti-dupe token is registered so the note can be redeemed exactly once.
        verify(banknoteStore).register(any(Banknote.class));
    }
}
