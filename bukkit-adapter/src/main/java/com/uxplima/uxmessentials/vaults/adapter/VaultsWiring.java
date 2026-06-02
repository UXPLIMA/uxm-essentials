package com.uxplima.uxmessentials.vaults.adapter;

import java.time.Clock;
import java.util.List;
import java.util.Objects;

import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.persistence.vaults.CachedVaultRepository;
import com.uxplima.uxmessentials.persistence.vaults.VaultRepositories;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.outbound.bus.Bus;
import com.uxplima.uxmessentials.shared.adapter.outbound.bus.VaultSync;
import com.uxplima.uxmessentials.shared.adapter.outbound.log.Slf4jLogger;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.vaults.adapter.inbound.command.VaultCommands;
import com.uxplima.uxmessentials.vaults.adapter.inbound.gui.VaultView;
import com.uxplima.uxmessentials.vaults.adapter.outbound.LoggingVaultAudit;
import com.uxplima.uxmessentials.vaults.application.ListVaults;
import com.uxplima.uxmessentials.vaults.application.OpenAdminVault;
import com.uxplima.uxmessentials.vaults.application.OpenVault;
import com.uxplima.uxmessentials.vaults.application.SaveVault;
import com.uxplima.uxmessentials.vaults.application.VaultAmountQuota;
import com.uxplima.uxmessentials.vaults.application.VaultNotifier;
import com.uxplima.uxmessentials.vaults.application.VaultSizeQuota;
import com.uxplima.uxmessentials.vaults.application.port.VaultAudit;
import com.uxplima.uxmessentials.vaults.application.port.VaultRepository;
import org.jspecify.annotations.NullMarked;
import org.slf4j.LoggerFactory;

/**
 * Constructs the vaults context's adapters and use cases over the injected kernel ports and the persistence
 * DSL, and produces everything the plugin must register: the Brigadier {@code /vault} command, with the vault
 * windows themselves handled by uxmLib's {@code StorageGui}. This is the one place the vaults context is wired
 * — nothing else news up its classes.
 *
 * <p>The repository is the cached jOOQ adapter over {@code persistence.dsl()} (write-through at the database,
 * invalidate in the Caffeine cache); the two numbered-quota families resolve through the shared
 * {@code Permissions} reducer with the {@code vaults.conf} defaults; the audit trail goes to the dedicated
 * {@code com.uxplima.uxmessentials.audit} channel (not the plugin log), so an operator routes it to a retained
 * file per docs/09-deployment. The GUI rides uxmLib's menu framework: the plugin bootstrap installs the one
 * shared menu listener and {@link VaultView} opens a {@code StorageGui} sized to the resolved quota that
 * writes the vault through on close.
 *
 * <p>Cross-server sync rides the {@link Bus} handle: the wiring registers a {@link VaultSync} listener that
 * invalidates exactly the {@code (owner, index)} a peer reports changed and wraps the cached repository so
 * every local vault save announces a {@code VaultChanged} to peers. With the bus disabled the publish is a
 * no-op and the listener is never invoked, so the single-server path is unchanged.
 */
@NullMarked
public final class VaultsWiring {

    private static final String AUDIT_CHANNEL = "com.uxplima.uxmessentials.audit";

    private VaultsWiring() {}

    /** Build the vaults adapters and use cases from {@code ctx}, the {@code persistence} DSL, and the bus. */
    public static Wired wire(Plugin plugin, ModuleContext ctx, Persistence persistence, Bus bus) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(persistence, "persistence");
        Objects.requireNonNull(bus, "bus");
        KernelPorts kernel = ctx.kernel();
        Clock clock = Clock.systemUTC();
        VaultSettings settings = new VaultSettings(ctx.config());
        // The cached repository is the read accelerator; the bus listener invalidates exactly the vault a peer
        // reports changed, and the broadcasting decorator announces this backend's own saves to peers.
        CachedVaultRepository cached = VaultRepositories.cachedConcrete(persistence);
        bus.registry().register(VaultSync.listener(cached));
        VaultRepository repository = VaultSync.repository(cached, bus.publisher());
        VaultServices services = assemble(kernel, settings, repository, clock);
        return new Wired(VaultCommands.all(services), List.of(), services.view(), repository);
    }

    private static VaultServices assemble(
            KernelPorts kernel, VaultSettings settings, VaultRepository repository, Clock clock) {
        VaultAmountQuota amountQuota = new VaultAmountQuota(kernel.permissions(), settings.defaultAmount());
        VaultSizeQuota sizeQuota = new VaultSizeQuota(kernel.permissions(), settings.defaultSize());
        VaultAudit audit = new LoggingVaultAudit(auditLogger());
        VaultNotifier notifier = new VaultNotifier(kernel.messages(), kernel.messageSink());
        SaveVault saveVault = new SaveVault(repository, kernel.events(), clock);
        VaultView view = new VaultView(kernel.messages(), saveVault, kernel.scheduler());
        return new VaultServices(
                new OpenVault(repository, amountQuota, sizeQuota, clock),
                new ListVaults(repository),
                new OpenAdminVault(repository, sizeQuota, audit, clock),
                saveVault,
                notifier,
                view,
                kernel);
    }

    private static Logger auditLogger() {
        return new Slf4jLogger(LoggerFactory.getLogger(AUDIT_CHANNEL));
    }

    /**
     * Everything the vaults module contributes once wired: the Brigadier {@code /vault} command and the
     * {@link VaultView} held so {@code stop()} flushes every still-open vault before the pool closes (the
     * {@code open-guis=N} the doctor line reports). The menu close events are routed by uxmLib's own menu
     * listener (installed once in the plugin bootstrap), so this module registers no inventory listener of its
     * own.
     *
     * @param commands the Brigadier command registrations to publish
     * @param listeners the Bukkit listeners to register (none here; the menu listener is uxmLib's)
     * @param view the GUI, held for the stop-time flush
     * @param repository the vault store the {@code vaults_count} placeholder reads
     */
    public record Wired(
            List<CommandRegistration> commands, List<Listener> listeners, VaultView view, VaultRepository repository) {

        public Wired {
            commands = List.copyOf(commands);
            listeners = List.copyOf(listeners);
            Objects.requireNonNull(view, "view");
            Objects.requireNonNull(repository, "repository");
        }

        /** Save every still-open vault window before the pool closes. Called on module stop. */
        public void stop() {
            view.flushAll();
        }
    }
}
