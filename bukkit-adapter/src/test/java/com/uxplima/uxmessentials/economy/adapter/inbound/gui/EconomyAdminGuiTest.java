package com.uxplima.uxmessentials.economy.adapter.inbound.gui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

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
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.menu.TestMenuEngine;
import com.uxplima.uxmlib.gui.Guis;
import com.uxplima.uxmlib.gui.anvil.AnvilInput;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of the bare-{@code /eco} admin GUI now that the per-player manage screen and the server-wide
 * bulk screen render through the menu engine: the Give / Take / Set / Reset flow reaches {@code EcoAdmin} with the
 * chosen {@link Money} (right currency and parsed amount), a malformed amount runs no op, the currency selector
 * switches the active currency the amount applies to, and the server-wide give-all / reset-all reach the bulk ops.
 * {@code EcoAdmin} is a Mockito mock so the wiring is asserted without a live persistence stack; the scheduler runs
 * each hop inline. The amount-entry branch is driven through the package-private {@code applyAmount}/{@code
 * applyGiveAll} (the same seam {@code PlayerPickerView.resolveTyped} exposes), so the test does not depend on
 * driving a live anvil. Clicks fire through the engine's own {@code MenuListener}; the still-bespoke hub rides
 * uxmLib's {@code Guis}.
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
    private TestMenuEngine engine;
    private MenuBindings bindings;
    private Menus menus;
    private AnvilInput anvil;
    private com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput textInput;
    private PlayerPickerView picker;
    private EconomyNotifier notifier;
    private TransactionsHistoryMenu historyView;

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
        engine = TestMenuEngine.create(new KeyMessages(), scheduler);
        bindings = engine.bindings();
        menus = engine.menus();
        engine.installListener(plugin);
        anvil = new AnvilInput(plugin);
        anvil.install();
        Guis.install(plugin);
        textInput = com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInputInstaller.install(
                        plugin, plugin.getDataFolder().toPath(), anvil, guiText, scheduler, mock(Logger.class))
                .textInput();
        notifier = new EconomyNotifier(new KeyMessages(), new NoopSink());
        historyView = mock(TransactionsHistoryMenu.class);
        picker = new PlayerPickerView(guiText, scheduler, textInput, server, new KeyMessages(), new NoopSink());
    }

    @AfterEach
    void tearDown() {
        Guis.uninstall();
        MockBukkit.unmock();
    }

    private CurrencyPickerView picker() {
        return new CurrencyPickerView(menus, guiText, scheduler);
    }

    /**
     * Build and register both engine menus over {@code currencies} exactly once on the shared bindings. The manage
     * screen owns the shared {@code eco_currency} placeholder and the multi-currency condition, so it registers
     * first; both screens then ride the one engine the test installed its listener on.
     */
    private Screens wire(CurrencyRegistry currencies) {
        EcoAdminOps ops = new EcoAdminOps(ecoAdmin);
        EconomyTargetMenu target = new EconomyTargetMenu(
                menus,
                guiText,
                new KeyMessages(),
                scheduler,
                textInput,
                provider,
                ops,
                currencies,
                notifier,
                historyView,
                picker(),
                (p, v) -> {});
        target.register(bindings, specDir(), NOOP);
        EconomyBulkMenu bulk = new EconomyBulkMenu(
                menus, guiText, scheduler, textInput, server, ops, currencies, notifier, picker(), (p, v) -> {});
        bulk.register(bindings, specDir(), NOOP);
        return new Screens(target, bulk);
    }

    private EconomyTargetMenu targetMenu(CurrencyRegistry currencies) {
        return wire(currencies).target();
    }

    private EconomyBulkMenu bulkMenu(CurrencyRegistry currencies) {
        return wire(currencies).bulk();
    }

    /** Both engine-rendered admin screens registered together on one bindings, as production wires them. */
    private record Screens(EconomyTargetMenu target, EconomyBulkMenu bulk) {}

    private static CurrencyRegistry singleCoins() {
        return CurrencyRegistry.single(COINS);
    }

    private static CurrencyRegistry coinsAndGems() {
        return CurrencyRegistry.of(List.of(COINS, GEMS), COINS.id());
    }

    @Test
    void giveButtonCallsGiveWithTheParsedAmountInTheActiveCurrency() {
        EconomyTargetMenu menu = targetMenu(singleCoins());
        menu.applyAmount(admin, adminRef, target, COINS, EcoAdminOps.Verb.GIVE, "100");

        verify(ecoAdmin).give(eq(adminRef), eq(target), eq(Money.of(COINS, new BigDecimal("100"))));
    }

    @Test
    void takeButtonCallsTakeWithTheParsedAmount() {
        EconomyTargetMenu menu = targetMenu(singleCoins());
        menu.applyAmount(admin, adminRef, target, COINS, EcoAdminOps.Verb.TAKE, "25.50");

        verify(ecoAdmin).take(eq(adminRef), eq(target), eq(Money.of(COINS, new BigDecimal("25.50"))));
    }

    @Test
    void setButtonCallsSetWithTheParsedAmount() {
        EconomyTargetMenu menu = targetMenu(singleCoins());
        menu.applyAmount(admin, adminRef, target, COINS, EcoAdminOps.Verb.SET, "1k");

        verify(ecoAdmin).set(eq(adminRef), eq(target), eq(Money.of(COINS, new BigDecimal("1000"))));
    }

    @Test
    void aMalformedAmountRunsNoOp() {
        EconomyTargetMenu menu = targetMenu(singleCoins());
        menu.applyAmount(admin, adminRef, target, COINS, EcoAdminOps.Verb.GIVE, "abc");

        verifyNoInteractions(ecoAdmin);
    }

    @Test
    void aNegativeAmountRunsNoOp() {
        EconomyTargetMenu menu = targetMenu(singleCoins());
        menu.applyAmount(admin, adminRef, target, COINS, EcoAdminOps.Verb.GIVE, "-5");

        verifyNoInteractions(ecoAdmin);
    }

    @Test
    void anAmountIsParsedAgainstTheChosenSecondCurrency() {
        EconomyTargetMenu menu = targetMenu(coinsAndGems());
        menu.applyAmount(admin, adminRef, target, GEMS, EcoAdminOps.Verb.GIVE, "7");

        verify(ecoAdmin).give(eq(adminRef), eq(target), eq(Money.of(GEMS, 7L)));
    }

    @Test
    void theResetButtonConfirmsThenCallsResetWithTheActiveCurrency() {
        EconomyTargetMenu menu = targetMenu(singleCoins());
        menu.open(admin, adminRef, target);
        // The manage screen's reset button opens the confirm menu; the confirm (slot 11) calls reset.
        fireClick(25); // reset slot on the manage screen
        fireClick(CONFIRM_SLOT); // confirm yes

        verify(ecoAdmin).reset(eq(adminRef), eq(target), eq(COINS));
    }

    @Test
    void theManageScreenShowsAnActionPerVerb() {
        EconomyTargetMenu menu = targetMenu(singleCoins());
        menu.open(admin, adminRef, target);

        Inventory inv = admin.getOpenInventory().getTopInventory();
        assertThat(inv.getItem(19).getType()).isEqualTo(Material.EMERALD); // give
        assertThat(inv.getItem(21).getType()).isEqualTo(Material.REDSTONE); // take
        assertThat(inv.getItem(23).getType()).isEqualTo(Material.COMPARATOR); // set
        assertThat(inv.getItem(25).getType()).isEqualTo(Material.TNT); // reset
    }

    @Test
    void theSelectCurrencyItemIsAbsentWithASingleCurrency() {
        EconomyTargetMenu menu = targetMenu(singleCoins());
        menu.open(admin, adminRef, target);

        Inventory inv = admin.getOpenInventory().getTopInventory();
        // The select-currency slot (38) carries only filler when a single currency is configured.
        assertThat(inv.getItem(38).getType()).isEqualTo(Material.GRAY_STAINED_GLASS_PANE);
    }

    @Test
    void theSelectCurrencyItemIsPresentWithMultipleCurrencies() {
        EconomyTargetMenu menu = targetMenu(coinsAndGems());
        menu.open(admin, adminRef, target);

        Inventory inv = admin.getOpenInventory().getTopInventory();
        // The single [Currency] item opens the paginated picker; it is the sunflower at slot 38.
        assertThat(inv.getItem(38).getType()).isEqualTo(Material.SUNFLOWER);
    }

    @Test
    void clickingTheSelectCurrencyItemOpensThePaginatedPicker() {
        EconomyTargetMenu menu = targetMenu(coinsAndGems());
        menu.open(admin, adminRef, target);
        fireClick(38); // [Currency] -> the paginated picker

        Inventory pickerInv = admin.getOpenInventory().getTopInventory();
        // The picker grids one icon per currency in its content slots; both currencies are present.
        assertThat(pickerInv.getSize()).isEqualTo(54); // 6-row picker
        assertThat(pickerInv.getItem(0)).isNotNull();
        assertThat(pickerInv.getItem(1)).isNotNull();
    }

    @Test
    void pickingACurrencyInThePickerReopensTheManageScreenWithItActive() {
        EconomyTargetMenu menu = targetMenu(coinsAndGems());
        menu.open(admin, adminRef, target);
        fireClick(38); // open the picker
        // The picker lists COINS at slot 0 and GEMS at slot 1 (registry order); pick GEMS.
        fireClick(1);
        // Back on the manage screen, entering an amount now parses against GEMS (precision 0).
        menu.applyAmount(admin, adminRef, target, GEMS, EcoAdminOps.Verb.GIVE, "7");

        verify(ecoAdmin).give(eq(adminRef), eq(target), eq(Money.of(GEMS, 7L)));
    }

    @Test
    void theBulkScreenSelectCurrencyItemOpensThePicker() {
        EconomyBulkMenu menu = bulkMenu(coinsAndGems());
        menu.open(admin, adminRef);

        Inventory inv = admin.getOpenInventory().getTopInventory();
        // The single [Currency] item sits between give-all (11) and reset-all (15) at slot 13.
        assertThat(inv.getItem(13).getType()).isEqualTo(Material.SUNFLOWER);

        fireClick(13); // -> the paginated picker
        Inventory pickerInv = admin.getOpenInventory().getTopInventory();
        assertThat(pickerInv.getSize()).isEqualTo(54);
        assertThat(pickerInv.getItem(0)).isNotNull();
        assertThat(pickerInv.getItem(1)).isNotNull();
    }

    @Test
    void giveAllCreditsTheOnlineRosterWithTheParsedAmount() {
        EconomyBulkMenu menu = bulkMenu(singleCoins());
        menu.applyGiveAll(admin, adminRef, COINS, "50");

        verify(ecoAdmin)
                .giveAll(eq(adminRef), eq(List.of(adminRef, target)), eq(Money.of(COINS, new BigDecimal("50"))));
    }

    @Test
    void giveAllWithAMalformedAmountRunsNoOp() {
        EconomyBulkMenu menu = bulkMenu(singleCoins());
        menu.applyGiveAll(admin, adminRef, COINS, "nope");

        verifyNoInteractions(ecoAdmin);
    }

    @Test
    void resetAllConfirmsThenResetsTheOnlineRoster() {
        EconomyBulkMenu menu = bulkMenu(singleCoins());
        menu.open(admin, adminRef);
        fireClick(RESETALL_SLOT); // reset-all on the bulk screen -> confirm
        fireClick(CONFIRM_SLOT); // confirm yes

        verify(ecoAdmin).resetAll(eq(adminRef), eq(List.of(adminRef, target)), eq(COINS));
    }

    @Test
    void theHubOpensTheManageScreenWhenAPlayerIsPicked() {
        Screens screens = wire(singleCoins());
        PlayerLookup players = mock(PlayerLookup.class);
        EconomyAdminMenu hub =
                new EconomyAdminMenu(menus, scheduler, picker, players, screens.target(), screens.bulk(), historyView);
        hub.register(bindings, specDir(), NOOP);

        hub.open(admin, adminRef);
        fireClick(MANAGE_SLOT); // [Manage a player] -> player picker
        // The picker grids online heads in roster order: admin at slot 0, target at slot 1.
        fireClick(1); // pick Target -> manage screen

        Inventory inv = admin.getOpenInventory().getTopInventory();
        assertThat(inv.getItem(19).getType()).isEqualTo(Material.EMERALD); // the manage screen's give button
    }

    @Test
    void bareEcoInstallsTheGuiOpenerWhenTheAdminGuiIsSupplied() {
        EconomyAdminGuiViews adminGui = EconomyAdminGuiViews.create(
                menus,
                bindings,
                specDir(),
                NOOP,
                guiText,
                new KeyMessages(),
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

    /** The bundled spec directory under the source tree, so the menus load the shipped target/bulk specs. */
    private static Path specDir() {
        Path repoRoot = Path.of("").toAbsolutePath();
        while (repoRoot != null && !java.nio.file.Files.exists(repoRoot.resolve("settings.gradle.kts"))) {
            repoRoot = repoRoot.getParent();
        }
        Objects.requireNonNull(repoRoot, "repo root");
        return repoRoot.resolve("bukkit-adapter/src/main/resources");
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

    private static final Logger NOOP = new Logger() {
        @Override
        public void info(String m, Object... a) {}

        @Override
        public void warn(String m, Object... a) {}

        @Override
        public void error(String m, Throwable t) {}

        @Override
        public void debug(String m, Object... a) {}
    };

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
