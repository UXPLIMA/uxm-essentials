package com.uxplima.uxmessentials.economy.adapter.inbound.gui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.economy.adapter.EconomyServices;
import com.uxplima.uxmessentials.economy.adapter.inbound.command.EcoCommand;
import com.uxplima.uxmessentials.economy.application.EcoAdmin;
import com.uxplima.uxmessentials.economy.application.EconomyNotifier;
import com.uxplima.uxmessentials.economy.application.port.BanknoteStore;
import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.CurrencyRegistry;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.PlayerPickerView;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmlib.gui.Guis;
import com.uxplima.uxmlib.gui.anvil.AnvilInput;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of the bare-{@code /eco} admin GUI: the per-player Give / Take / Set / Reset flow reaches
 * {@code EcoAdmin} with the chosen {@link Money} (right currency and parsed amount), a malformed amount runs no
 * op, the currency selector switches the active currency the amount applies to, and the server-wide give-all /
 * reset-all reach the bulk ops. {@code EcoAdmin} is a Mockito mock so the wiring is asserted without a live
 * persistence stack; the scheduler runs each hop inline. The amount-entry branch is driven through the
 * package-private {@code applyAmount}/{@code applyGiveAll} (the same seam {@code PlayerPickerView.resolveTyped}
 * exposes), so the test does not depend on driving a live anvil.
 */
class EconomyAdminGuiTest {

    private static final int MANAGE_SLOT = 11;
    private static final int CONFIRM_SLOT = 11;
    private static final int RESETALL_SLOT = 15;

    private static final Currency COINS = Currency.builder(CurrencyId.of("coins"))
            .symbol("$")
            .plural("coins")
            .precision(2)
            .build();
    private static final Currency GEMS = Currency.builder(CurrencyId.of("gems"))
            .symbol("♦")
            .plural("gems")
            .precision(0)
            .build();

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock admin;
    private PlayerRef adminRef;
    private PlayerMock targetPlayer;
    private PlayerRef target;

    private EcoAdmin ecoAdmin;
    private EconomyProvider provider;
    private GuiText guiText;
    private Scheduler scheduler;
    private AnvilInput anvil;
    private com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput textInput;
    private PlayerPickerView picker;
    private EconomyNotifier notifier;
    private TransactionsHistoryView historyView;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        admin = server.addPlayer("Admin");
        adminRef = new PlayerRef(admin.getUniqueId(), admin.getName());
        targetPlayer = server.addPlayer("Target");
        target = new PlayerRef(targetPlayer.getUniqueId(), targetPlayer.getName());

        ecoAdmin = mock(EcoAdmin.class);
        provider = mock(EconomyProvider.class);
        when(provider.balance(eq(target), eq(COINS))).thenReturn(Money.zero(COINS));
        when(provider.balance(eq(target), eq(GEMS))).thenReturn(Money.zero(GEMS));

        guiText = new GuiText(new KeyMessages());
        scheduler = new SyncScheduler();
        anvil = new AnvilInput(plugin);
        anvil.install();
        Guis.install(plugin);
        textInput = com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInputInstaller.install(
                        plugin,
                        plugin.getDataFolder().toPath(),
                        anvil,
                        guiText,
                        scheduler,
                        mock(com.uxplima.uxmessentials.shared.application.port.Logger.class))
                .textInput();
        notifier = new EconomyNotifier(new KeyMessages(), new NoopSink());
        historyView = mock(TransactionsHistoryView.class);
        picker = new PlayerPickerView(guiText, scheduler, textInput, server, new KeyMessages(), new NoopSink());
    }

    @AfterEach
    void tearDown() {
        Guis.uninstall();
        MockBukkit.unmock();
    }

    private CurrencyPickerView picker() {
        return new CurrencyPickerView(guiText, scheduler);
    }

    private EconomyTargetView targetView(CurrencyRegistry currencies) {
        EcoAdminOps ops = new EcoAdminOps(ecoAdmin);
        return new EconomyTargetView(
                guiText,
                scheduler,
                textInput,
                provider,
                ops,
                currencies,
                notifier,
                historyView,
                picker(),
                (p, v) -> {});
    }

    private EconomyBulkView bulkView(CurrencyRegistry currencies) {
        EcoAdminOps ops = new EcoAdminOps(ecoAdmin);
        return new EconomyBulkView(
                guiText, scheduler, textInput, server, ops, currencies, notifier, picker(), (p, v) -> {});
    }

    private static CurrencyRegistry singleCoins() {
        return CurrencyRegistry.single(COINS);
    }

    private static CurrencyRegistry coinsAndGems() {
        return CurrencyRegistry.of(List.of(COINS, GEMS), COINS.id());
    }

    @Test
    void giveButtonCallsGiveWithTheParsedAmountInTheActiveCurrency() {
        EconomyTargetView view = targetView(singleCoins());
        view.applyAmount(admin, adminRef, target, COINS, EcoAdminOps.Verb.GIVE, "100");

        verify(ecoAdmin).give(eq(adminRef), eq(target), eq(Money.of(COINS, new BigDecimal("100"))));
    }

    @Test
    void takeButtonCallsTakeWithTheParsedAmount() {
        EconomyTargetView view = targetView(singleCoins());
        view.applyAmount(admin, adminRef, target, COINS, EcoAdminOps.Verb.TAKE, "25.50");

        verify(ecoAdmin).take(eq(adminRef), eq(target), eq(Money.of(COINS, new BigDecimal("25.50"))));
    }

    @Test
    void setButtonCallsSetWithTheParsedAmount() {
        EconomyTargetView view = targetView(singleCoins());
        view.applyAmount(admin, adminRef, target, COINS, EcoAdminOps.Verb.SET, "1k");

        verify(ecoAdmin).set(eq(adminRef), eq(target), eq(Money.of(COINS, new BigDecimal("1000"))));
    }

    @Test
    void aMalformedAmountRunsNoOp() {
        EconomyTargetView view = targetView(singleCoins());
        view.applyAmount(admin, adminRef, target, COINS, EcoAdminOps.Verb.GIVE, "abc");

        verifyNoInteractions(ecoAdmin);
    }

    @Test
    void aNegativeAmountRunsNoOp() {
        EconomyTargetView view = targetView(singleCoins());
        view.applyAmount(admin, adminRef, target, COINS, EcoAdminOps.Verb.GIVE, "-5");

        verifyNoInteractions(ecoAdmin);
    }

    @Test
    void anAmountIsParsedAgainstTheChosenSecondCurrency() {
        EconomyTargetView view = targetView(coinsAndGems());
        view.applyAmount(admin, adminRef, target, GEMS, EcoAdminOps.Verb.GIVE, "7");

        verify(ecoAdmin).give(eq(adminRef), eq(target), eq(Money.of(GEMS, 7L)));
    }

    @Test
    void theResetButtonConfirmsThenCallsResetWithTheActiveCurrency() {
        EconomyTargetView view = targetView(singleCoins());
        view.open(admin, adminRef, target);
        // The manage screen's reset button opens the confirm menu; the confirm (slot 11) calls reset.
        fireClick(25); // RESET_SLOT on the manage screen
        fireClick(CONFIRM_SLOT); // confirm yes

        verify(ecoAdmin).reset(eq(adminRef), eq(target), eq(COINS));
    }

    @Test
    void theManageScreenShowsAnActionPerVerb() {
        EconomyTargetView view = targetView(singleCoins());
        view.open(admin, adminRef, target);

        Inventory menu = admin.getOpenInventory().getTopInventory();
        assertThat(menu.getItem(19).getType()).isEqualTo(Material.EMERALD); // give
        assertThat(menu.getItem(21).getType()).isEqualTo(Material.REDSTONE); // take
        assertThat(menu.getItem(23).getType()).isEqualTo(Material.COMPARATOR); // set
        assertThat(menu.getItem(25).getType()).isEqualTo(Material.TNT); // reset
    }

    @Test
    void theSelectCurrencyItemIsAbsentWithASingleCurrency() {
        EconomyTargetView view = targetView(singleCoins());
        view.open(admin, adminRef, target);

        Inventory menu = admin.getOpenInventory().getTopInventory();
        // The select-currency slot (38) carries only filler when a single currency is configured.
        assertThat(menu.getItem(38).getType()).isEqualTo(Material.GRAY_STAINED_GLASS_PANE);
    }

    @Test
    void theSelectCurrencyItemIsPresentWithMultipleCurrencies() {
        EconomyTargetView view = targetView(coinsAndGems());
        view.open(admin, adminRef, target);

        Inventory menu = admin.getOpenInventory().getTopInventory();
        // The single [Currency] item opens the paginated picker; it is the sunflower at slot 38.
        assertThat(menu.getItem(38).getType()).isEqualTo(Material.SUNFLOWER);
    }

    @Test
    void clickingTheSelectCurrencyItemOpensThePaginatedPicker() {
        EconomyTargetView view = targetView(coinsAndGems());
        view.open(admin, adminRef, target);
        fireClick(38); // [Currency] -> the paginated picker

        Inventory picker = admin.getOpenInventory().getTopInventory();
        // The picker grids one icon per currency in its content slots; both currencies are present.
        assertThat(picker.getSize()).isEqualTo(54); // 6-row picker
        assertThat(picker.getItem(0)).isNotNull();
        assertThat(picker.getItem(1)).isNotNull();
    }

    @Test
    void pickingACurrencyInThePickerReopensTheManageScreenWithItActive() {
        EconomyTargetView view = targetView(coinsAndGems());
        view.open(admin, adminRef, target);
        fireClick(38); // open the picker
        // The picker lists COINS at slot 0 and GEMS at slot 1 (registry order); pick GEMS.
        fireClick(1);
        // Back on the manage screen, entering an amount now parses against GEMS (precision 0).
        view.applyAmount(admin, adminRef, target, GEMS, EcoAdminOps.Verb.GIVE, "7");

        verify(ecoAdmin).give(eq(adminRef), eq(target), eq(Money.of(GEMS, 7L)));
    }

    @Test
    void theBulkScreenSelectCurrencyItemOpensThePicker() {
        EconomyBulkView view = bulkView(coinsAndGems());
        view.open(admin, adminRef);

        Inventory menu = admin.getOpenInventory().getTopInventory();
        // The single [Currency] item sits between give-all (11) and reset-all (15) at slot 13.
        assertThat(menu.getItem(13).getType()).isEqualTo(Material.SUNFLOWER);

        fireClick(13); // -> the paginated picker
        Inventory picker = admin.getOpenInventory().getTopInventory();
        assertThat(picker.getSize()).isEqualTo(54);
        assertThat(picker.getItem(0)).isNotNull();
        assertThat(picker.getItem(1)).isNotNull();
    }

    @Test
    void giveAllCreditsTheOnlineRosterWithTheParsedAmount() {
        EconomyBulkView view = bulkView(singleCoins());
        view.applyGiveAll(admin, adminRef, COINS, "50");

        verify(ecoAdmin)
                .giveAll(eq(adminRef), eq(List.of(adminRef, target)), eq(Money.of(COINS, new BigDecimal("50"))));
    }

    @Test
    void giveAllWithAMalformedAmountRunsNoOp() {
        EconomyBulkView view = bulkView(singleCoins());
        view.applyGiveAll(admin, adminRef, COINS, "nope");

        verifyNoInteractions(ecoAdmin);
    }

    @Test
    void resetAllConfirmsThenResetsTheOnlineRoster() {
        EconomyBulkView view = bulkView(singleCoins());
        view.open(admin, adminRef);
        fireClick(RESETALL_SLOT); // reset-all on the bulk screen -> confirm
        fireClick(CONFIRM_SLOT); // confirm yes

        verify(ecoAdmin).resetAll(eq(adminRef), eq(List.of(adminRef, target)), eq(COINS));
    }

    @Test
    void theHubOpensTheManageScreenWhenAPlayerIsPicked() {
        EconomyTargetView targetView = targetView(singleCoins());
        EconomyBulkView bulkView = bulkView(singleCoins());
        PlayerLookup players = mock(PlayerLookup.class);
        EconomyAdminView hub =
                new EconomyAdminView(guiText, scheduler, picker, players, targetView, bulkView, historyView);

        hub.open(admin, adminRef);
        fireClick(MANAGE_SLOT); // [Manage a player] -> player picker
        // The picker grids online heads in roster order: admin at slot 0, target at slot 1.
        fireClick(1); // pick Target -> manage screen

        Inventory menu = admin.getOpenInventory().getTopInventory();
        assertThat(menu.getItem(19).getType()).isEqualTo(Material.EMERALD); // the manage screen's give button
    }

    @Test
    void bareEcoInstallsTheGuiOpenerWhenTheAdminGuiIsSupplied() {
        EconomyAdminGuiViews adminGui = EconomyAdminGuiViews.create(
                guiText,
                scheduler,
                server,
                picker,
                textInput,
                mock(PlayerLookup.class),
                provider,
                ecoAdmin,
                singleCoins(),
                notifier,
                historyView);
        EcoCommand command = new EcoCommand(plugin, stubServices(), new KeyMessages(), adminGui);

        assertThat(command.guiRoot()).isPresent();
        // The raw subcommands still build alongside the bare-root opener.
        assertThat(command.build().getChild("give")).isNotNull();
        assertThat(command.build().getChild("set")).isNotNull();
        assertThat(command.build().getChild("reset")).isNotNull();
    }

    @Test
    void bareEcoHasNoOpenerWithoutTheGuiStack() {
        EcoCommand command = new EcoCommand(plugin, stubServices(), new KeyMessages());

        assertThat(command.guiRoot()).isEmpty();
        assertThat(command.build().getChild("give")).isNotNull();
    }

    private EconomyServices stubServices() {
        EconomyServices services = mock(EconomyServices.class);
        when(services.banknoteStore()).thenReturn(mock(BanknoteStore.class));
        return services;
    }

    private void fireClick(int slot) {
        InventoryView view = admin.getOpenInventory();
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

    private static final class NoopSink implements MessageSink {
        @Override
        public void deliver(PlayerRef viewer, String renderedText) {}
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
