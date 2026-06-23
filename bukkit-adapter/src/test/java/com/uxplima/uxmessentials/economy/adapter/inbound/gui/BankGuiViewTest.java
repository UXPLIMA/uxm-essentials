package com.uxplima.uxmessentials.economy.adapter.inbound.gui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.economy.application.BankService;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.CurrencyRegistry;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.SharedBank;
import com.uxplima.uxmessentials.economy.domain.SharedBank.BankMember;
import com.uxplima.uxmessentials.economy.domain.SharedBank.BankRole;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.InputRequest;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmlib.gui.Guis;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of the bank-list create flow after the random-id rework: the create button captures a name
 * through the input seam, opens the reusable currency picker, and a picked currency reaches
 * {@code BankService.createBank(name, currency, viewer)} with no caller-supplied id — the id is the one the
 * service assigns. The seam is mocked to feed a canned name synchronously; the picker is the real view, so a
 * currency click drives the actual {@code onPick} path.
 */
class BankGuiViewTest {

    private static final int CREATE_SLOT = 49;

    private static final Currency COINS = Currency.builder(CurrencyId.of("coins"))
            .symbol("$")
            .plural("coins")
            .precision(2)
            .build();

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerRef playerRef;

    private BankService bankService;
    private TextInput textInput;
    private GuiText guiText;
    private Scheduler scheduler;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("uxmEssentials");
        player = server.addPlayer("Alice");
        playerRef = new PlayerRef(player.getUniqueId(), "Alice");

        Guis.install(plugin);
        bankService = mock(BankService.class);
        when(bankService.getBankIdsForPlayer(any())).thenReturn(List.of());
        guiText = new GuiText(new KeyMessages());
        scheduler = new SyncScheduler();
        textInput = mock(TextInput.class);
    }

    @AfterEach
    void tearDown() {
        Guis.uninstall();
        MockBukkit.unmock();
    }

    @Test
    void creatingThroughTheGuiPicksACurrencyAndAssignsAnId() {
        // The seam fires its submit callback with the canned name as soon as the create button asks for it.
        feedName("Vault");
        SharedBank created = bankFor("eEa12523", "Vault");
        when(bankService.createBank(eq("Vault"), eq(COINS), any(PlayerRef.class)))
                .thenReturn(Result.ok(created));

        BankGuiView view = view(CurrencyRegistry.single(COINS));
        view.open(player);
        fireClick(CREATE_SLOT); // create -> name prompt (fires inline) -> currency picker opens

        // The picker grids one icon per currency in its content slots; coins sits at slot 0.
        fireClick(0); // pick coins -> createBank(name, currency, viewer)

        verify(bankService).createBank(eq("Vault"), eq(COINS), any(PlayerRef.class));
    }

    @Test
    void theCreateButtonOpensTheCurrencyPickerNotAnIdPrompt() {
        feedName("Vault");
        when(bankService.createBank(anyString(), any(Currency.class), any(PlayerRef.class)))
                .thenReturn(Result.ok(bankFor("eEa12523", "Vault")));

        BankGuiView view = view(CurrencyRegistry.single(COINS));
        view.open(player);
        fireClick(CREATE_SLOT);

        Inventory picker = player.getOpenInventory().getTopInventory();
        assertThat(picker.getSize()).isEqualTo(54); // the 6-row reusable currency picker
        assertThat(picker.getItem(0)).isNotNull();
    }

    private void feedName(String name) {
        doAnswer(invocation -> {
                    Consumer<String> onSubmit = invocation.getArgument(3);
                    onSubmit.accept(name);
                    return null;
                })
                .when(textInput)
                .prompt(any(), any(PlayerRef.class), any(InputRequest.class), any(), any());
    }

    private BankGuiView view(CurrencyRegistry currencies) {
        CurrencyPickerView picker = new CurrencyPickerView(guiText, scheduler);
        GuiLayout layout = GuiLayout.paginatedDefault(Material.CHEST);
        return new BankGuiView(
                bankService,
                currencies,
                textInput,
                picker,
                scheduler,
                new KeyMessages(),
                () -> mock(BankNavigation.class),
                layout);
    }

    private SharedBank bankFor(String id, String name) {
        List<BankMember> members = List.of(new BankMember(playerRef, BankRole.LEADER));
        return new SharedBank(id, name, Money.zero(COINS), playerRef, members, 0L);
    }

    private void fireClick(int slot) {
        InventoryView view = player.getOpenInventory();
        InventoryClickEvent event = new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, slot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
    }

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, java.util.Map<String, String> placeholders) {
            return key.key();
        }
    }

    private static final class SyncScheduler implements Scheduler {
        @Override
        public void onGlobal(Runnable task) {
            task.run();
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            task.run();
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            task.run();
        }

        @Override
        public void async(Runnable task) {
            task.run();
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            task.run();
        }
    }
}
