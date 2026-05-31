package com.uxplima.uxmessentials.vaults.adapter;

import java.time.Clock;
import java.util.List;
import java.util.Objects;

import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.persistence.vaults.VaultRepositories;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.outbound.log.Slf4jLogger;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.vaults.adapter.inbound.command.VaultCommands;
import com.uxplima.uxmessentials.vaults.adapter.inbound.gui.VaultView;
import com.uxplima.uxmessentials.vaults.adapter.inbound.listener.VaultCloseListener;
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
 * DSL, and produces everything the plugin must register: the Brigadier {@code /vault} command and the
 * {@code InventoryClose} save listener. This is the one place the vaults context is wired — nothing else news
 * up its classes.
 *
 * <p>The repository is the cached jOOQ adapter over {@code persistence.dsl()} (write-through at the database,
 * invalidate in the Caffeine cache); the two numbered-quota families resolve through the shared
 * {@code Permissions} reducer with the {@code vaults.conf} defaults; the audit trail goes to the dedicated
 * {@code com.uxplima.uxmessentials.audit} channel (not the plugin log), so an operator routes it to a retained
 * file per docs/09-deployment. The GUI is inventory-holder based: a {@link VaultView} opens a chest sized to
 * the resolved quota and the close listener resolves the owning vault from the holder to write it through.
 */
@NullMarked
public final class VaultsWiring {

    private static final String AUDIT_CHANNEL = "com.uxplima.uxmessentials.audit";

    private VaultsWiring() {}

    /** Build the vaults adapters and use cases from {@code ctx} and the {@code persistence} DSL. */
    public static Wired wire(Plugin plugin, ModuleContext ctx, Persistence persistence) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(persistence, "persistence");
        KernelPorts kernel = ctx.kernel();
        Clock clock = Clock.systemUTC();
        VaultSettings settings = new VaultSettings(ctx.config());
        VaultRepository repository = VaultRepositories.cached(persistence);
        VaultServices services = assemble(plugin, kernel, settings, repository, clock);
        VaultCloseListener closeListener = new VaultCloseListener(services.saveVault(), kernel.scheduler());
        return new Wired(VaultCommands.all(services), List.of(closeListener), closeListener);
    }

    private static VaultServices assemble(
            Plugin plugin, KernelPorts kernel, VaultSettings settings, VaultRepository repository, Clock clock) {
        VaultAmountQuota amountQuota = new VaultAmountQuota(kernel.permissions(), settings.defaultAmount());
        VaultSizeQuota sizeQuota = new VaultSizeQuota(kernel.permissions(), settings.defaultSize());
        VaultAudit audit = new LoggingVaultAudit(auditLogger());
        VaultNotifier notifier = new VaultNotifier(kernel.messages(), kernel.messageSink());
        VaultView view = new VaultView(plugin.getServer(), kernel.messages());
        return new VaultServices(
                new OpenVault(repository, amountQuota, sizeQuota, clock),
                new ListVaults(repository),
                new OpenAdminVault(repository, sizeQuota, audit, clock),
                new SaveVault(repository, kernel.events(), clock),
                notifier,
                view,
                kernel);
    }

    private static Logger auditLogger() {
        return new Slf4jLogger(LoggerFactory.getLogger(AUDIT_CHANNEL));
    }

    /**
     * Everything the vaults module contributes once wired: the Brigadier {@code /vault} command, the
     * {@code InventoryClose} save listener, and the listener held so {@code stop()} flushes every still-open
     * vault before the pool closes (the {@code open-guis=N} the doctor line reports).
     *
     * @param commands the Brigadier command registrations to publish
     * @param listeners the inventory-close save listener to register
     * @param closeListener the listener, held for the stop-time flush
     */
    public record Wired(
            List<CommandRegistration> commands, List<Listener> listeners, VaultCloseListener closeListener) {

        public Wired {
            commands = List.copyOf(commands);
            listeners = List.copyOf(listeners);
            Objects.requireNonNull(closeListener, "closeListener");
        }

        /** Close-and-save every still-open vault window, then drop the open-window tracking. Called on stop. */
        public void stop() {
            closeListener.flushAll();
            closeListener.clear();
        }
    }
}
