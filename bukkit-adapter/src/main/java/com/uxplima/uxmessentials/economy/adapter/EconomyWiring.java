package com.uxplima.uxmessentials.economy.adapter;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.economy.adapter.inbound.command.EconomyCommands;
import com.uxplima.uxmessentials.economy.adapter.inbound.gui.BaltopGuiView;
import com.uxplima.uxmessentials.economy.adapter.inbound.gui.BankActionsView;
import com.uxplima.uxmessentials.economy.adapter.inbound.gui.BankGuiView;
import com.uxplima.uxmessentials.economy.adapter.inbound.gui.BankMembersView;
import com.uxplima.uxmessentials.economy.adapter.inbound.gui.ExchangeGuiView;
import com.uxplima.uxmessentials.economy.adapter.inbound.gui.LoanGuiView;
import com.uxplima.uxmessentials.economy.adapter.inbound.gui.PayConfirmationView;
import com.uxplima.uxmessentials.economy.adapter.inbound.gui.TransactionsHistoryView;
import com.uxplima.uxmessentials.economy.adapter.inbound.gui.WalletGuiView;
import com.uxplima.uxmessentials.economy.adapter.inbound.listener.BankChatPromptListener;
import com.uxplima.uxmessentials.economy.adapter.inbound.listener.BanknoteListener;
import com.uxplima.uxmessentials.economy.adapter.inbound.listener.CommandCostListener;
import com.uxplima.uxmessentials.economy.adapter.inbound.listener.LoanChatPromptListener;
import com.uxplima.uxmessentials.economy.adapter.outbound.BaltopSnapshots;
import com.uxplima.uxmessentials.economy.adapter.outbound.BukkitInventoryCalculations;
import com.uxplima.uxmessentials.economy.adapter.outbound.EconomyProviderRegistrar;
import com.uxplima.uxmessentials.economy.adapter.outbound.LoanRepaymentTask;
import com.uxplima.uxmessentials.economy.adapter.outbound.LoggingEconomyAudit;
import com.uxplima.uxmessentials.economy.adapter.outbound.PermissionBaltopExemption;
import com.uxplima.uxmessentials.economy.adapter.outbound.PermissionTaxExemption;
import com.uxplima.uxmessentials.economy.adapter.outbound.PhysicalWalletRepositoryDecorator;
import com.uxplima.uxmessentials.economy.adapter.outbound.ProviderKitEconomy;
import com.uxplima.uxmessentials.economy.adapter.outbound.ProviderNpcEconomy;
import com.uxplima.uxmessentials.economy.adapter.outbound.ProviderTaxSink;
import com.uxplima.uxmessentials.economy.adapter.outbound.ProviderWarpEconomy;
import com.uxplima.uxmessentials.economy.adapter.outbound.SalaryTask;
import com.uxplima.uxmessentials.economy.adapter.outbound.SchedulerPendingPayRegistry;
import com.uxplima.uxmessentials.economy.adapter.outbound.SnapshotBaltopProvider;
import com.uxplima.uxmessentials.economy.application.AmountFormat;
import com.uxplima.uxmessentials.economy.application.BalTop;
import com.uxplima.uxmessentials.economy.application.Balance;
import com.uxplima.uxmessentials.economy.application.CombiningWorthSource;
import com.uxplima.uxmessentials.economy.application.EcoAdmin;
import com.uxplima.uxmessentials.economy.application.EconomyNotifier;
import com.uxplima.uxmessentials.economy.application.ExchangeService;
import com.uxplima.uxmessentials.economy.application.LookupWorth;
import com.uxplima.uxmessentials.economy.application.NativeEconomyProvider;
import com.uxplima.uxmessentials.economy.application.Pay;
import com.uxplima.uxmessentials.economy.application.PayAll;
import com.uxplima.uxmessentials.economy.application.PayTaxation;
import com.uxplima.uxmessentials.economy.application.PayToggle;
import com.uxplima.uxmessentials.economy.application.SellAll;
import com.uxplima.uxmessentials.economy.application.SellItem;
import com.uxplima.uxmessentials.economy.application.SetWorth;
import com.uxplima.uxmessentials.economy.application.WorthSource;
import com.uxplima.uxmessentials.economy.application.WorthTable;
import com.uxplima.uxmessentials.economy.application.port.BaltopExemption;
import com.uxplima.uxmessentials.economy.application.port.EconomyAudit;
import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.application.port.PayPreferences;
import com.uxplima.uxmessentials.economy.application.port.PendingPayRegistry;
import com.uxplima.uxmessentials.economy.application.port.PendingTransactionRepository;
import com.uxplima.uxmessentials.economy.application.port.WalletRepository;
import com.uxplima.uxmessentials.economy.application.port.WorthOverrideStore;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyRegistry;
import com.uxplima.uxmessentials.homes.adapter.outbound.ProviderHomeEconomy;
import com.uxplima.uxmessentials.homes.application.port.HomeEconomy;
import com.uxplima.uxmessentials.kits.application.port.KitEconomy;
import com.uxplima.uxmessentials.persistence.economy.CachedWalletRepository;
import com.uxplima.uxmessentials.persistence.economy.WalletLedger;
import com.uxplima.uxmessentials.persistence.economy.WalletRepositories;
import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.outbound.bus.Bus;
import com.uxplima.uxmessentials.shared.adapter.outbound.bus.WalletSync;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmessentials.shared.application.port.ClickActionEconomy;
import com.uxplima.uxmessentials.vaults.adapter.outbound.ProviderVaultEconomy;
import com.uxplima.uxmessentials.vaults.application.port.VaultEconomy;
import com.uxplima.uxmessentials.warps.application.port.WarpEconomy;
import org.jspecify.annotations.NullMarked;

/**
 * Constructs the economy context — the plugin's canonical worked DDD example — over the injected kernel ports,
 * the persistence DSL, and the Bukkit {@code ServicesManager}. This is the one place the economy context is
 * wired: it builds the native ledger (the cached jOOQ repository plus the debounced settle writer and the
 * batched transaction telemetry), wraps it in the native {@code EconomyProvider}, then runs register-or-defer
 * — registering the native provider unless a foreign economy is already present, in which case it consumes the
 * incumbent (Treasury before Vault — {@code docs/11-economy-integration.md} §2, §4).
 *
 * <p>Every command reads the resolved provider through the per-currency baltop snapshot decorator, so the same
 * code serves the native ledger, a Treasury economy, or a legacy Vault economy without knowing which. The
 * {@code WarpEconomy} bridge produced here lets the warps context charge a per-warp cost through the resolved
 * provider. The {@link Wired} handle carries the lifecycle: {@code start} arms the settle/telemetry/baltop
 * loops and {@code stop} drains them and drops this plugin's registration.
 */
@NullMarked
public final class EconomyWiring {

    private EconomyWiring() {}

    /** Build the economy context from {@code plugin}, {@code ctx}, and the shared {@code persistence} DSL. */
    public static Wired wire(Plugin plugin, ModuleContext ctx, Persistence persistence, Bus bus) {
        return wire(plugin, ctx, persistence, bus, null);
    }

    /**
     * Build the economy context, threading the shared {@code anvil} into the bare-{@code /eco} admin GUI so its
     * Give / Take / Set amount entry reuses the one installed anvil listener. A {@code null} anvil disables the
     * admin GUI (bare {@code /eco} then falls through to usage); the raw subcommands are unaffected either way.
     */
    public static Wired wire(
            Plugin plugin,
            ModuleContext ctx,
            Persistence persistence,
            Bus bus,
            com.uxplima.uxmlib.gui.anvil.@org.jspecify.annotations.Nullable AnvilInput anvil) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(persistence, "persistence");
        Objects.requireNonNull(bus, "bus");
        KernelPorts kernel = ctx.kernel();
        EconomyConfig settings = new EconomyConfig(ctx.config());
        CurrencyRegistry currencies = settings.currencies();
        Clock clock = Clock.systemUTC();

        // The cached repository is the offline-read accelerator and the cache the bus invalidates on a remote
        // change; the broadcasting decorator wraps it so this backend's balance writes notify peers.
        CachedWalletRepository cached = WalletRepositories.cachedConcrete(persistence, currencies, clock);
        bus.registry().register(WalletSync.listener(cached));

        // Decorator chain: JooqWalletRepository -> CachedWalletRepository -> general-bus WalletSync broadcaster.
        // Cross-server invalidation rides the general bus's WalletSync frame (network.transport selects the
        // carrier). Money safety is the jOOQ guarded UPDATE alone — a single `UPDATE … WHERE amount >= ?` the
        // database serialises, where an over-draw updates zero rows; correct even across servers sharing one DB,
        // which a per-JVM lock could never be.
        WalletRepository repository = WalletSync.repository(cached, bus.publisher());

        WalletLedger ledger = WalletRepositories.ledgerOver(
                repository,
                persistence,
                kernel.scheduler(),
                kernel.log(),
                settings.writeDebounce(),
                settings.batchFlush());

        PendingTransactionRepository pendingRepo = WalletRepositories.pendingTransactionRepository(persistence);
        BukkitInventoryCalculations calculations = new BukkitInventoryCalculations();
        WalletRepository decoratedRepository = new PhysicalWalletRepositoryDecorator(
                ledger.repository(),
                pendingRepo,
                calculations,
                currencies,
                kernel.scheduler(),
                kernel.playerLookup(),
                kernel.log());

        EconomyProvider resolved = resolveProvider(plugin, kernel, settings, currencies, decoratedRepository, clock);
        return assemble(
                plugin,
                ctx,
                persistence,
                settings,
                currencies,
                ledger,
                resolved,
                decoratedRepository,
                pendingRepo,
                anvil);
    }

    private static EconomyProvider resolveProvider(
            Plugin plugin,
            KernelPorts kernel,
            EconomyConfig settings,
            CurrencyRegistry currencies,
            WalletRepository repository,
            Clock clock) {
        EconomyProvider nativeProvider = new NativeEconomyProvider(repository, currencies, clock);
        Optional<EconomyProvider> foreign = ForeignEconomyProviders.discover(plugin, currencies, kernel.log());
        if (foreign.isPresent()) {
            kernel.log().info("event=economy_provider_deferred (consuming foreign economy)");
            return foreign.get();
        }
        if (!settings.registerProvider()) {
            kernel.log().info("native economy provider registration disabled by config; using it locally");
            return nativeProvider;
        }
        EconomyProviderRegistrar registrar = new EconomyProviderRegistrar(
                plugin.getServer().getServicesManager(), plugin, kernel.log(), settings.registerPriority());
        return registrar.registerOrDefer(nativeProvider);
    }

    private static Wired assemble(
            Plugin plugin,
            ModuleContext ctx,
            Persistence persistence,
            EconomyConfig settings,
            CurrencyRegistry currencies,
            WalletLedger ledger,
            EconomyProvider resolved,
            WalletRepository repository,
            PendingTransactionRepository pendingRepo,
            com.uxplima.uxmlib.gui.anvil.@org.jspecify.annotations.Nullable AnvilInput anvil) {
        KernelPorts kernel = ctx.kernel();
        BaltopExemption exemption = new PermissionBaltopExemption(kernel.permissions(), settings.baltopExemptNode());
        BaltopSnapshots snapshots = new BaltopSnapshots(
                resolved,
                exemption,
                kernel.scheduler(),
                settings.baltopCacheTtl(),
                settings.baltopCapacity(),
                settings.baltopExcludeBanned(),
                settings.baltopMinBalance(),
                new com.uxplima.uxmessentials.economy.adapter.outbound.BukkitBannedLookup());

        com.uxplima.uxmessentials.economy.adapter.inbound.listener.ExchangeChatPromptListener chatPromptListener =
                new com.uxplima.uxmessentials.economy.adapter.inbound.listener.ExchangeChatPromptListener(
                        kernel.messages());

        BankChatPromptListener bankChatPromptListener = new BankChatPromptListener(kernel.messages());
        LoanChatPromptListener loanChatPromptListener = new LoanChatPromptListener(kernel.messages());

        // GUI geometry is operator-editable in modules/economy/gui/*.conf; titles and labels stay in the catalog.
        com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayouts guiLayouts =
                new com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayouts(
                        plugin.getDataFolder().toPath(), kernel.log());

        EconomyServices services = useCases(
                plugin,
                persistence,
                kernel,
                settings,
                currencies,
                repository,
                resolved,
                snapshots,
                ledger.telemetry(),
                chatPromptListener,
                bankChatPromptListener,
                loanChatPromptListener,
                guiLayouts);
        // The bare-/eco admin GUI (item 19): a hub over picker → per-player Give/Take/Set/Reset and a server-wide
        // bulk screen, reusing the one installed anvil for amount entry. Built only when an anvil is supplied (the
        // module is wired with GUIs available); GuiRootBinding then opens it on bare /eco when the catalog gui flag
        // is on, and the .requires(economy.admin) gate is carried by the rebind.
        com.uxplima.uxmessentials.economy.adapter.inbound.gui.EconomyAdminGuiViews adminGui = null;
        if (anvil != null) {
            com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText guiText =
                    new com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText(kernel.messages());
            com.uxplima.uxmessentials.shared.adapter.inbound.gui.PlayerPickerView picker =
                    new com.uxplima.uxmessentials.shared.adapter.inbound.gui.PlayerPickerView(
                            guiText,
                            kernel.scheduler(),
                            anvil,
                            plugin.getServer(),
                            kernel.messages(),
                            kernel.messageSink());
            adminGui = com.uxplima.uxmessentials.economy.adapter.inbound.gui.EconomyAdminGuiViews.create(
                    guiText,
                    kernel.scheduler(),
                    plugin.getServer(),
                    picker,
                    anvil,
                    kernel.playerLookup(),
                    resolved,
                    services.ecoAdmin(),
                    currencies,
                    services.notifier(),
                    services.historyView());
        }
        List<CommandRegistration> commands =
                EconomyCommands.all(plugin, settings, services, kernel.messages(), adminGui);
        WarpEconomy warpEconomy = new ProviderWarpEconomy(resolved, currencies.defaultCurrency());
        KitEconomy kitEconomy = new ProviderKitEconomy(resolved, currencies.defaultCurrency());
        HomeEconomy homeEconomy = new ProviderHomeEconomy(resolved, currencies.defaultCurrency());
        VaultEconomy vaultEconomy = new ProviderVaultEconomy(resolved, currencies.defaultCurrency());
        ClickActionEconomy npcEconomy = new ProviderNpcEconomy(resolved, currencies.defaultCurrency());

        java.util.List<org.bukkit.event.Listener> listenersList = new java.util.ArrayList<>();
        listenersList.add(new com.uxplima.uxmessentials.economy.adapter.inbound.listener.PendingTransactionListener(
                resolved, pendingRepo, services.notifier(), kernel.scheduler()));

        if (settings.commandCostsEnabled()) {
            listenersList.add(new CommandCostListener(
                    resolved, settings.commandCosts(), currencies.defaultCurrency(), services.notifier()));
        }
        if (settings.banknotesEnabled()) {
            listenersList.add(new BanknoteListener(services.banknoteRedeemer()));
        }
        if (settings.dailyRewardEnabled()) {
            listenersList.add(new com.uxplima.uxmessentials.economy.adapter.inbound.listener.DailyRewardListener(
                    plugin,
                    resolved,
                    services.notifier(),
                    kernel.scheduler(),
                    settings.dailyRewardCurrency(currencies),
                    true,
                    settings.dailyRewardAmount(),
                    settings.dailyRewardCooldown()));
        }
        if (settings.exchangeEnabled()) {
            listenersList.add(chatPromptListener);
        }
        if (settings.bankEnabled()) {
            listenersList.add(bankChatPromptListener);
        }
        if (settings.loansEnabled()) {
            listenersList.add(loanChatPromptListener);
        }
        List<org.bukkit.event.Listener> listeners = List.copyOf(listenersList);

        com.uxplima.uxmessentials.economy.adapter.outbound.SalaryTask salaryTask =
                new com.uxplima.uxmessentials.economy.adapter.outbound.SalaryTask(
                        plugin,
                        kernel.scheduler(),
                        resolved,
                        kernel.permissions(),
                        services.notifier(),
                        currencies.defaultCurrency(),
                        settings.salaryEnabled(),
                        settings.salaryInterval(),
                        settings.salaryDefaultAmount());

        com.uxplima.uxmessentials.economy.adapter.outbound.LoanRepaymentTask loanRepaymentTask =
                new com.uxplima.uxmessentials.economy.adapter.outbound.LoanRepaymentTask(
                        kernel.scheduler(),
                        services.loanService(),
                        kernel.log(),
                        settings.loansEnabled(),
                        settings.loanSweepInterval());

        com.uxplima.uxmessentials.economy.adapter.outbound.EconomyMaintenanceTask maintenanceTask =
                new com.uxplima.uxmessentials.economy.adapter.outbound.EconomyMaintenanceTask(
                        WalletRepositories.maintenance(persistence),
                        kernel.scheduler(),
                        kernel.log(),
                        Clock.systemUTC(),
                        new com.uxplima.uxmessentials.economy.adapter.outbound.BukkitPlayerLastSeen(),
                        settings.maintenanceEnabled(),
                        settings.maintenanceInterval(),
                        settings.maintenancePurgeInactiveDays(),
                        settings.maintenancePruneTransactionDays(),
                        settings.maintenanceDryRun());

        com.uxplima.uxmessentials.economy.adapter.outbound.BankInterestTask bankInterestTask =
                new com.uxplima.uxmessentials.economy.adapter.outbound.BankInterestTask(
                        com.uxplima.uxmessentials.persistence.economy.WalletRepositories.bankRepository(
                                persistence, currencies, Clock.systemUTC()),
                        settings.bankInterestPolicy(),
                        kernel.scheduler(),
                        kernel.log());

        // Clears every pending chat prompt on stop so a leftover callback can never fire after teardown.
        Runnable promptCleanup = () -> {
            chatPromptListener.clear();
            bankChatPromptListener.clear();
            loanChatPromptListener.clear();
        };

        return new Wired(
                commands,
                listeners,
                warpEconomy,
                kitEconomy,
                homeEconomy,
                vaultEconomy,
                npcEconomy,
                ledger,
                snapshots,
                resolved,
                plugin,
                settings.registerProvider(),
                currencies.defaultCurrency(),
                settings.amountFormat(),
                salaryTask,
                loanRepaymentTask,
                maintenanceTask,
                bankInterestTask,
                promptCleanup,
                services.walletView());
    }

    private static EconomyServices useCases(
            Plugin plugin,
            Persistence persistence,
            KernelPorts kernel,
            EconomyConfig settings,
            CurrencyRegistry currencies,
            WalletRepository repository,
            EconomyProvider resolved,
            BaltopSnapshots snapshots,
            com.uxplima.uxmessentials.economy.application.port.TransactionHistory history,
            com.uxplima.uxmessentials.economy.adapter.inbound.listener.ExchangeChatPromptListener chatPromptListener,
            BankChatPromptListener bankChatPromptListener,
            LoanChatPromptListener loanChatPromptListener,
            com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayouts guiLayouts) {
        EconomyNotifier notifier =
                new EconomyNotifier(kernel.messages(), kernel.messageSink(), settings.amountFormat());

        EconomyAudit baseAudit = new LoggingEconomyAudit(kernel.log());
        EconomyAudit audit = new com.uxplima.uxmessentials.economy.adapter.outbound.FraudDetector(
                baseAudit,
                kernel.log(),
                settings.fraudEnabled(),
                settings.fraudWebhookUrl(),
                settings.fraudSingleLimit(),
                settings.fraudVelocitySeconds(),
                settings.fraudVelocityLimit());
        audit = new com.uxplima.uxmessentials.economy.adapter.outbound.FileEconomyAudit(
                audit,
                kernel.scheduler(),
                kernel.log(),
                plugin.getDataFolder().toPath().resolve("economy").resolve("operations.log"),
                settings.operationLogEnabled());

        PayPreferences preferences = WalletRepositories.payPreferences(persistence, settings.payToggleDefault());
        PendingPayRegistry pending =
                new SchedulerPendingPayRegistry(kernel.scheduler(), kernel.log(), settings.confirmTimeout());
        Clock clock = Clock.systemUTC();
        EconomyProvider baltopProvider = new SnapshotBaltopProvider(resolved, snapshots);
        WorthTable configWorth = settings.worthTable(currencies, kernel.log());
        WorthOverrideStore worthOverrides = WalletRepositories.worthOverrideStore(persistence);
        WorthSource worth = new CombiningWorthSource(worthOverrides, configWorth);
        Currency defaultCurrency = currencies.defaultCurrency();
        PayTaxation taxation = new PayTaxation(
                settings.taxPolicy(),
                new ProviderTaxSink(resolved, settings.taxSinkAccount(), kernel.log()),
                new PermissionTaxExemption(kernel.permissions(), settings.taxBypassNode()));
        Pay pay = new Pay(resolved, preferences, pending, notifier, taxation, clock);

        // Exchange is a native-ledger feature like loans and banks: the atomic two-currency move runs through the
        // wallet repository this context owns, so it is available only when the resolved provider is the native
        // ledger (a foreign economy holds balances we cannot move atomically here).
        boolean nativeLedger = resolved instanceof NativeEconomyProvider;
        ExchangeService exchangeService = new ExchangeService(repository, settings.exchangeRegistry(), nativeLedger);

        com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayout logsLayout = guiLayouts.load(
                "economy",
                "transaction-logs",
                com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayout.paginatedDefault(
                        org.bukkit.Material.PAPER));
        TransactionsHistoryView historyView = new TransactionsHistoryView(
                history, kernel.scheduler(), kernel.messages(), logsLayout, settings.historyTimeZone());
        com.uxplima.uxmessentials.shared.adapter.inbound.gui.FixedMenuLayout payConfirmLayout =
                guiLayouts.loadFixedMenu("economy", "pay-confirm", PayConfirmationView.defaultLayout());
        PayConfirmationView payConfirmationView =
                new PayConfirmationView(pay, kernel.scheduler(), kernel.messages(), payConfirmLayout);
        BaltopGuiView baltopGuiView = new BaltopGuiView(snapshots, kernel.scheduler(), notifier, kernel.messages());
        com.uxplima.uxmessentials.shared.adapter.inbound.gui.FixedMenuLayout walletLayout =
                guiLayouts.loadFixedMenu("economy", "wallet", WalletGuiView.defaultLayout());
        WalletGuiView walletGuiView = new WalletGuiView(
                plugin,
                resolved,
                kernel.scheduler(),
                notifier,
                kernel.messages(),
                historyView,
                walletLayout,
                kernel.log());
        com.uxplima.uxmessentials.shared.adapter.inbound.gui.FixedMenuLayout exchangeLayout =
                guiLayouts.loadFixedMenu("economy", "exchange", ExchangeGuiView.defaultLayout());
        ExchangeGuiView exchangeView = new ExchangeGuiView(
                plugin,
                resolved,
                exchangeService,
                kernel.scheduler(),
                notifier,
                kernel.messages(),
                chatPromptListener,
                exchangeLayout);

        com.uxplima.uxmessentials.economy.application.port.BanknoteStore banknoteStore =
                com.uxplima.uxmessentials.persistence.economy.WalletRepositories.banknoteStore(persistence);
        com.uxplima.uxmessentials.economy.adapter.inbound.listener.BanknoteRedeemer banknoteRedeemer =
                new com.uxplima.uxmessentials.economy.adapter.inbound.listener.BanknoteRedeemer(
                        plugin, resolved, notifier, banknoteStore, kernel.log());
        com.uxplima.uxmessentials.economy.application.port.BankRepository bankRepo =
                com.uxplima.uxmessentials.persistence.economy.WalletRepositories.bankRepository(
                        persistence, currencies, clock);
        com.uxplima.uxmessentials.economy.application.port.LoanRepository loanRepo =
                com.uxplima.uxmessentials.persistence.economy.WalletRepositories.loanRepository(
                        persistence, currencies, clock);
        com.uxplima.uxmessentials.persistence.economy.EconomyBackupManager backupManager =
                com.uxplima.uxmessentials.persistence.economy.WalletRepositories.backupManager(
                        persistence, repository, currencies, plugin.getDataFolder());

        // Loans and banks are native-ledger features like exchange (gated above): their atomic wallet legs run
        // through the wallet repository this context owns, so they are available only when the resolved provider
        // is the native ledger (a foreign economy holds balances we cannot move atomically here).
        com.uxplima.uxmessentials.economy.application.BankService bankService =
                new com.uxplima.uxmessentials.economy.application.BankService(
                        bankRepo, history, kernel.events(), clock, nativeLedger);
        com.uxplima.uxmessentials.economy.application.LoanService loanService =
                new com.uxplima.uxmessentials.economy.application.LoanService(
                        loanRepo, settings.loanPolicy(), kernel.events(), clock, nativeLedger);

        // The three bank menus open each other, so they cannot all be constructed before the navigation router
        // that links them. A holder supplies the router once it is built; each view holds the supplier as a
        // final field and dereferences it only on a click, keeping the cross-links constructor-injected and
        // non-null (no post-construction setters).
        java.util.concurrent.atomic.AtomicReference<
                        com.uxplima.uxmessentials.economy.adapter.inbound.gui.BankNavigation>
                navigationHolder = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.function.Supplier<com.uxplima.uxmessentials.economy.adapter.inbound.gui.BankNavigation> navigation =
                () -> Objects.requireNonNull(navigationHolder.get(), "bank navigation not yet wired");

        com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayout bankListLayout = guiLayouts.load(
                "economy",
                "bank-list",
                com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayout.paginatedDefault(
                        org.bukkit.Material.CHEST));
        com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayout bankMembersLayout = guiLayouts.load(
                "economy",
                "bank-members",
                com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayout.paginatedDefault(
                        org.bukkit.Material.PLAYER_HEAD));

        BankGuiView bankGuiView = new BankGuiView(
                bankService,
                currencies,
                bankChatPromptListener,
                kernel.scheduler(),
                kernel.messages(),
                navigation,
                bankListLayout);
        com.uxplima.uxmessentials.shared.adapter.inbound.gui.FixedMenuLayout bankActionsLayout =
                guiLayouts.loadFixedMenu("economy", "bank-actions", BankActionsView.defaultLayout());
        BankActionsView bankActionsView = new BankActionsView(
                bankService,
                bankChatPromptListener,
                kernel.scheduler(),
                kernel.messages(),
                historyView,
                navigation,
                bankActionsLayout);
        BankMembersView bankMembersView = new BankMembersView(
                bankService,
                bankChatPromptListener,
                kernel.scheduler(),
                kernel.playerLookup(),
                kernel.messages(),
                navigation,
                bankMembersLayout);
        navigationHolder.set(new com.uxplima.uxmessentials.economy.adapter.inbound.gui.BankNavigation(
                bankGuiView, bankActionsView, bankMembersView));
        com.uxplima.uxmessentials.shared.adapter.inbound.gui.FixedMenuLayout loanLayout =
                guiLayouts.loadFixedMenu("economy", "loan-dashboard", LoanGuiView.defaultLayout());
        LoanGuiView loanGuiView = new LoanGuiView(
                loanService, currencies, loanChatPromptListener, kernel.scheduler(), kernel.messages(), loanLayout);

        return new EconomyServices(
                new Balance(resolved, notifier),
                pay,
                new PayAll(pay, notifier),
                new PayToggle(preferences, notifier),
                new BalTop(baltopProvider, notifier, settings.baltopPageSize()),
                new LookupWorth(worth, notifier, defaultCurrency, currencies.all()),
                new SellItem(resolved, worth, notifier, defaultCurrency, currencies.all()),
                new SellAll(resolved, worth, notifier, defaultCurrency, currencies.all()),
                new SetWorth(worthOverrides, notifier, audit, defaultCurrency, currencies.all()),
                new EcoAdmin(resolved, repository, audit, notifier),
                exchangeService,
                currencies,
                snapshots,
                kernel.scheduler(),
                kernel.playerLookup(),
                notifier,
                history,
                historyView,
                payConfirmationView,
                baltopGuiView,
                walletGuiView,
                exchangeView,
                resolved,
                banknoteStore,
                banknoteRedeemer,
                bankService,
                loanService,
                backupManager,
                bankGuiView,
                loanGuiView);
    }

    /**
     * Everything the economy module contributes once wired: the Brigadier commands, the {@code WarpEconomy},
     * {@code KitEconomy}, {@code HomeEconomy}, and {@code VaultEconomy} bridges the warps, kits, homes, and
     * vaults contexts charge through, and the lifecycle for the settle/telemetry/baltop loops and the
     * {@code ServicesManager} registration.
     *
     * @param commands the Brigadier command registrations to publish
     * @param warpEconomy the bridge warps charges a per-warp cost through
     * @param kitEconomy the bridge kits charges a per-kit cost through
     * @param homeEconomy the bridge homes charges a per-action cost through
     * @param vaultEconomy the bridge vaults charges a per-action cost (and pays the delete refund) through
     * @param npcEconomy the bridge an NPC {@code COST} click action charges the clicking viewer through
     * @param ledger the native-ledger persistence handle whose loops are armed/drained
     * @param snapshots the per-currency baltop snapshots whose refresh loop is armed/stopped
     * @param provider the resolved provider this plugin uses (registered or deferred)
     * @param plugin the owning plugin, for the registration drop on stop
     * @param registered whether this plugin registered the native provider (so stop only unregisters then)
     * @param defaultCurrency the default currency the {@code balance}/{@code baltop_position} placeholders read
     * @param amountFormat the operator-selected amount format the {@code balance_formatted} placeholder uses
     * @param walletView the wallet dashboard the {@code /wallet} command and the management hub both open
     */
    public record Wired(
            List<CommandRegistration> commands,
            List<org.bukkit.event.Listener> listeners,
            WarpEconomy warpEconomy,
            KitEconomy kitEconomy,
            HomeEconomy homeEconomy,
            VaultEconomy vaultEconomy,
            ClickActionEconomy npcEconomy,
            WalletLedger ledger,
            BaltopSnapshots snapshots,
            EconomyProvider provider,
            Plugin plugin,
            boolean registered,
            Currency defaultCurrency,
            AmountFormat amountFormat,
            @org.jspecify.annotations.Nullable SalaryTask salaryTask,
            @org.jspecify.annotations.Nullable LoanRepaymentTask loanRepaymentTask,
            com.uxplima.uxmessentials.economy.adapter.outbound.EconomyMaintenanceTask maintenanceTask,
            com.uxplima.uxmessentials.economy.adapter.outbound.BankInterestTask bankInterestTask,
            Runnable promptCleanup,
            WalletGuiView walletView) {

        public Wired {
            commands = List.copyOf(commands);
            listeners = List.copyOf(listeners);
            Objects.requireNonNull(warpEconomy, "warpEconomy");
            Objects.requireNonNull(kitEconomy, "kitEconomy");
            Objects.requireNonNull(homeEconomy, "homeEconomy");
            Objects.requireNonNull(vaultEconomy, "vaultEconomy");
            Objects.requireNonNull(npcEconomy, "npcEconomy");
            Objects.requireNonNull(ledger, "ledger");
            Objects.requireNonNull(snapshots, "snapshots");
            Objects.requireNonNull(provider, "provider");
            Objects.requireNonNull(plugin, "plugin");
            Objects.requireNonNull(defaultCurrency, "defaultCurrency");
            Objects.requireNonNull(amountFormat, "amountFormat");
            Objects.requireNonNull(maintenanceTask, "maintenanceTask");
            Objects.requireNonNull(bankInterestTask, "bankInterestTask");
            Objects.requireNonNull(promptCleanup, "promptCleanup");
            Objects.requireNonNull(walletView, "walletView");
        }

        /** Arm the settle, telemetry, and baltop-refresh loops. Called once after the module starts. */
        public void start() {
            ledger.start();
            snapshots.start();
            if (salaryTask != null) {
                salaryTask.start();
            }
            if (loanRepaymentTask != null) {
                loanRepaymentTask.start();
            }
            maintenanceTask.start();
            bankInterestTask.start();
        }

        /** Drain the queues, stop the loops, and drop this plugin's provider registration. */
        public void stop() {
            promptCleanup.run();
            bankInterestTask.stop();
            maintenanceTask.stop();
            if (loanRepaymentTask != null) {
                loanRepaymentTask.stop();
            }
            if (salaryTask != null) {
                salaryTask.stop();
            }
            snapshots.stop();
            ledger.stop();
            if (registered) {
                new EconomyProviderRegistrar(
                                plugin.getServer().getServicesManager(),
                                plugin,
                                new com.uxplima.uxmessentials.shared.adapter.outbound.log.Slf4jLogger(
                                        plugin.getSLF4JLogger()),
                                org.bukkit.plugin.ServicePriority.Normal)
                        .unregister(provider);
            }
        }
    }
}
