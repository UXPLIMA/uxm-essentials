package com.uxplima.uxmessentials.economy.adapter;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.economy.adapter.inbound.command.EconomyCommands;
import com.uxplima.uxmessentials.economy.adapter.outbound.BaltopSnapshots;
import com.uxplima.uxmessentials.economy.adapter.outbound.EconomyProviderRegistrar;
import com.uxplima.uxmessentials.economy.adapter.outbound.LoggingEconomyAudit;
import com.uxplima.uxmessentials.economy.adapter.outbound.PermissionBaltopExemption;
import com.uxplima.uxmessentials.economy.adapter.outbound.ProviderKitEconomy;
import com.uxplima.uxmessentials.economy.adapter.outbound.ProviderWarpEconomy;
import com.uxplima.uxmessentials.economy.adapter.outbound.SchedulerPendingPayRegistry;
import com.uxplima.uxmessentials.economy.adapter.outbound.SnapshotBaltopProvider;
import com.uxplima.uxmessentials.economy.application.BalTop;
import com.uxplima.uxmessentials.economy.application.Balance;
import com.uxplima.uxmessentials.economy.application.EcoAdmin;
import com.uxplima.uxmessentials.economy.application.EconomyNotifier;
import com.uxplima.uxmessentials.economy.application.NativeEconomyProvider;
import com.uxplima.uxmessentials.economy.application.Pay;
import com.uxplima.uxmessentials.economy.application.PayToggle;
import com.uxplima.uxmessentials.economy.application.port.BaltopExemption;
import com.uxplima.uxmessentials.economy.application.port.EconomyAudit;
import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.application.port.PayPreferences;
import com.uxplima.uxmessentials.economy.application.port.PendingPayRegistry;
import com.uxplima.uxmessentials.economy.application.port.WalletRepository;
import com.uxplima.uxmessentials.economy.domain.CurrencyRegistry;
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
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(persistence, "persistence");
        Objects.requireNonNull(bus, "bus");
        KernelPorts kernel = ctx.kernel();
        EconomyConfig settings = new EconomyConfig(ctx.config());
        CurrencyRegistry currencies = settings.currencies();
        Clock clock = Clock.systemUTC();

        // The cached repository is the offline-read accelerator and the cache the bus invalidates on a remote
        // change; the broadcasting decorator wraps it so this backend's balance writes notify peers. The ledger
        // (settle writer + telemetry) is built over the wrapped repository so a coalesced settle also announces.
        CachedWalletRepository cached = WalletRepositories.cachedConcrete(persistence, currencies, clock);
        bus.registry().register(WalletSync.listener(cached));
        WalletLedger ledger = WalletRepositories.ledgerOver(
                WalletSync.repository(cached, bus.publisher()),
                persistence,
                kernel.scheduler(),
                kernel.log(),
                settings.writeDebounce(),
                settings.batchFlush());
        EconomyProvider resolved = resolveProvider(plugin, kernel, settings, currencies, ledger.repository(), clock);
        return assemble(plugin, ctx, persistence, settings, currencies, ledger, resolved);
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
            EconomyProvider resolved) {
        KernelPorts kernel = ctx.kernel();
        BaltopExemption exemption = new PermissionBaltopExemption(kernel.permissions(), settings.baltopExemptNode());
        BaltopSnapshots snapshots = new BaltopSnapshots(
                resolved, exemption, kernel.scheduler(), settings.baltopCacheTtl(), settings.baltopCapacity());
        EconomyServices services =
                useCases(persistence, kernel, settings, currencies, ledger.repository(), resolved, snapshots);
        List<CommandRegistration> commands = EconomyCommands.all(services, kernel.messages());
        WarpEconomy warpEconomy = new ProviderWarpEconomy(resolved, currencies.defaultCurrency());
        KitEconomy kitEconomy = new ProviderKitEconomy(resolved, currencies.defaultCurrency());
        return new Wired(
                commands, warpEconomy, kitEconomy, ledger, snapshots, resolved, plugin, settings.registerProvider());
    }

    private static EconomyServices useCases(
            Persistence persistence,
            KernelPorts kernel,
            EconomyConfig settings,
            CurrencyRegistry currencies,
            WalletRepository repository,
            EconomyProvider resolved,
            BaltopSnapshots snapshots) {
        EconomyNotifier notifier = new EconomyNotifier(kernel.messages(), kernel.messageSink());
        EconomyAudit audit = new LoggingEconomyAudit(kernel.log());
        PayPreferences preferences = WalletRepositories.payPreferences(persistence, settings.payToggleDefault());
        PendingPayRegistry pending =
                new SchedulerPendingPayRegistry(kernel.scheduler(), kernel.log(), settings.confirmTimeout());
        Clock clock = Clock.systemUTC();
        EconomyProvider baltopProvider = new SnapshotBaltopProvider(resolved, snapshots);
        return new EconomyServices(
                new Balance(resolved, notifier),
                new Pay(resolved, preferences, pending, notifier, clock),
                new PayToggle(preferences, notifier),
                new BalTop(baltopProvider, notifier, settings.baltopPageSize()),
                new EcoAdmin(resolved, repository, audit, notifier),
                currencies,
                snapshots,
                kernel.scheduler(),
                kernel.playerLookup(),
                notifier);
    }

    /**
     * Everything the economy module contributes once wired: the Brigadier commands, the {@code WarpEconomy}
     * and {@code KitEconomy} bridges the warps and kits contexts charge through, and the lifecycle for the
     * settle/telemetry/baltop loops and the {@code ServicesManager} registration.
     *
     * @param commands the Brigadier command registrations to publish
     * @param warpEconomy the bridge warps charges a per-warp cost through
     * @param kitEconomy the bridge kits charges a per-kit cost through
     * @param ledger the native-ledger persistence handle whose loops are armed/drained
     * @param snapshots the per-currency baltop snapshots whose refresh loop is armed/stopped
     * @param provider the resolved provider this plugin uses (registered or deferred)
     * @param plugin the owning plugin, for the registration drop on stop
     * @param registered whether this plugin registered the native provider (so stop only unregisters then)
     */
    public record Wired(
            List<CommandRegistration> commands,
            WarpEconomy warpEconomy,
            KitEconomy kitEconomy,
            WalletLedger ledger,
            BaltopSnapshots snapshots,
            EconomyProvider provider,
            Plugin plugin,
            boolean registered) {

        public Wired {
            commands = List.copyOf(commands);
            Objects.requireNonNull(warpEconomy, "warpEconomy");
            Objects.requireNonNull(kitEconomy, "kitEconomy");
            Objects.requireNonNull(ledger, "ledger");
            Objects.requireNonNull(snapshots, "snapshots");
            Objects.requireNonNull(provider, "provider");
            Objects.requireNonNull(plugin, "plugin");
        }

        /** Arm the settle, telemetry, and baltop-refresh loops. Called once after the module starts. */
        public void start() {
            ledger.start();
            snapshots.start();
        }

        /** Drain the queues, stop the loops, and drop this plugin's provider registration. */
        public void stop() {
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
