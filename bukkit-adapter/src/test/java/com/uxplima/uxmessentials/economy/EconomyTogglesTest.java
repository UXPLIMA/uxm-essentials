package com.uxplima.uxmessentials.economy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.economy.adapter.EconomyConfig;
import com.uxplima.uxmessentials.economy.adapter.EconomyServices;
import com.uxplima.uxmessentials.economy.adapter.inbound.command.EconomyCommands;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class EconomyTogglesTest {

    private Plugin plugin;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("uxmEssentials");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void whenAllFeaturesAreEnabled_allCommandsAreRegistered() {
        ConfigStore configStore = mock(ConfigStore.class);
        // Default returns true for enablement checks
        when(configStore.getBoolean("banknotes.enabled", true)).thenReturn(true);
        when(configStore.getBoolean("bank.enabled", true)).thenReturn(true);
        when(configStore.getBoolean("loans.enabled", true)).thenReturn(true);
        when(configStore.getBoolean("exchange.enabled", true)).thenReturn(true);
        when(configStore.getBoolean("worth.enabled", true)).thenReturn(true);
        when(configStore.getBoolean("wallet.gui-enabled", true)).thenReturn(true);

        EconomyConfig config = new EconomyConfig(configStore);
        EconomyServices services = mock(EconomyServices.class);
        when(services.banknoteStore())
                .thenReturn(mock(com.uxplima.uxmessentials.economy.application.port.BanknoteStore.class));
        Messages messages = mock(Messages.class);

        List<CommandRegistration> commands = EconomyCommands.all(plugin, config, services, messages);

        // Verify that all commands are present (17 in total)
        assertThat(commands).hasSize(17);
    }

    @Test
    void whenFeaturesAreDisabled_correspondingCommandsAreOmitted() {
        ConfigStore configStore = mock(ConfigStore.class);
        // Disable bank, loans and banknotes
        when(configStore.getBoolean("banknotes.enabled", true)).thenReturn(false);
        when(configStore.getBoolean("bank.enabled", true)).thenReturn(false);
        when(configStore.getBoolean("loans.enabled", true)).thenReturn(false);
        // Keep others enabled
        when(configStore.getBoolean("exchange.enabled", true)).thenReturn(true);
        when(configStore.getBoolean("worth.enabled", true)).thenReturn(true);
        when(configStore.getBoolean("wallet.gui-enabled", true)).thenReturn(true);

        EconomyConfig config = new EconomyConfig(configStore);
        EconomyServices services = mock(EconomyServices.class);
        when(services.banknoteStore())
                .thenReturn(mock(com.uxplima.uxmessentials.economy.application.port.BanknoteStore.class));
        Messages messages = mock(Messages.class);

        List<CommandRegistration> commands = EconomyCommands.all(plugin, config, services, messages);

        // Verify that commands for bank, loans, withdraw, deposit are not registered
        // Total commands registered: 17 - 4 (bank, loan, withdraw, deposit) = 13
        assertThat(commands).hasSize(13);

        for (CommandRegistration reg : commands) {
            String name = reg.defaultName();
            assertThat(name).isNotEqualTo("bank");
            assertThat(name).isNotEqualTo("loan");
            assertThat(name).isNotEqualTo("withdraw");
            assertThat(name).isNotEqualTo("deposit");
        }
    }
}
