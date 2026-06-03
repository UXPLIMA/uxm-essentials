package com.uxplima.uxmessentials.kits.adapter;

import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.kits.adapter.inbound.command.KitCommands;
import com.uxplima.uxmessentials.kits.adapter.inbound.gui.KitMenuView;
import com.uxplima.uxmessentials.kits.adapter.outbound.BukkitKitGranter;
import com.uxplima.uxmessentials.kits.adapter.outbound.ConfigurateKitRepository;
import com.uxplima.uxmessentials.kits.adapter.outbound.PdcKitClaims;
import com.uxplima.uxmessentials.kits.application.ClaimKit;
import com.uxplima.uxmessentials.kits.application.CreateKit;
import com.uxplima.uxmessentials.kits.application.DelKit;
import com.uxplima.uxmessentials.kits.application.KitAccess;
import com.uxplima.uxmessentials.kits.application.KitEditor;
import com.uxplima.uxmessentials.kits.application.KitNotifier;
import com.uxplima.uxmessentials.kits.application.KitReset;
import com.uxplima.uxmessentials.kits.application.ListKits;
import com.uxplima.uxmessentials.kits.application.ShowKit;
import com.uxplima.uxmessentials.kits.application.port.KitClaimStore;
import com.uxplima.uxmessentials.kits.application.port.KitEconomy;
import com.uxplima.uxmessentials.kits.application.port.KitGranter;
import com.uxplima.uxmessentials.kits.application.port.KitRepository;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import org.jspecify.annotations.NullMarked;

/**
 * Constructs the kits context's adapters and use cases over the injected kernel ports, the {@code kits.conf}
 * catalog, and the PDC claim store, and produces the Brigadier command list the plugin registers. This is
 * the one place the kits context is wired — nothing else news up its classes.
 *
 * <p>The repository is the Configurate adapter over {@code kits.conf} (read-on-load, write-through on
 * authoring). The claim store and the granter are PDC- and inventory-backed Bukkit adapters; the shared
 * {@code Cooldowns} and {@code Permissions} kernel ports cover the cooldown and permission gates. The per-kit
 * cost soft-couples to the economy context: the {@link KitEconomy} seam is injected as an {@link Optional},
 * {@link Optional#empty()} when economy is disabled, so a priced kit's cost is recorded but not charged until
 * that bridge is wired.
 */
@NullMarked
public final class KitsWiring {

    private static final String KITS_FILE = "kits.conf";

    private KitsWiring() {}

    /** Build the kits adapters and use cases with no economy bridge (a recorded kit cost is not charged). */
    public static Wired wire(Plugin plugin, ModuleContext ctx) {
        return wire(plugin, ctx, Optional.empty());
    }

    /**
     * Build the kits context, charging a recorded per-kit cost through {@code economy} when present. The
     * economy context lands before kits in the registry, so its {@link KitEconomy} bridge is captured during
     * economy wiring and handed in here; when it is empty (economy disabled), a priced kit's cost is recorded
     * but not charged — the soft coupling the kits context owns.
     */
    public static Wired wire(Plugin plugin, ModuleContext ctx, Optional<KitEconomy> economy) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(economy, "economy");
        KernelPorts kernel = ctx.kernel();
        Path file = plugin.getDataFolder().toPath().resolve(KITS_FILE);
        KitRepository repository = ConfigurateKitRepository.load(file, kernel.log());
        KitClaimStore claims = new PdcKitClaims(plugin);
        KitGranter granter = new BukkitKitGranter(kernel.log());
        KitNotifier notifier = new KitNotifier(kernel.messages(), kernel.messageSink());
        KitServices services = assemble(kernel, repository, claims, granter, notifier, economy);
        return new Wired(KitCommands.all(services, kernel.messages()), repository);
    }

    private static KitServices assemble(
            KernelPorts kernel,
            KitRepository repository,
            KitClaimStore claims,
            KitGranter granter,
            KitNotifier notifier,
            Optional<KitEconomy> economy) {
        KitAccess access = new KitAccess(kernel.permissions(), kernel.cooldowns(), claims, economy);
        Clock clock = Clock.systemUTC();
        KitMenuView kitMenu = new KitMenuView(kernel.messages(), kernel.scheduler());
        return new KitServices(
                new ClaimKit(repository, access, granter, notifier, kernel.events(), clock),
                new ListKits(repository, kernel.permissions(), claims, notifier),
                new ShowKit(repository, notifier),
                new CreateKit(repository, notifier),
                new DelKit(repository, notifier),
                new KitEditor(repository, notifier),
                new KitReset(repository, claims, notifier),
                kitMenu,
                kernel.playerLookup());
    }

    /**
     * Everything the kits module contributes once wired: the Brigadier commands plus the {@link
     * KitRepository} the {@code kit_cooldown_<id>} placeholder resolves a kit's cooldown tier against. The
     * kits context holds no repeating scheduled work and no in-memory store beyond the config-backed catalog,
     * so there is nothing to drain on stop — the module's {@code stop()} clears its own bookkeeping.
     *
     * @param commands the Brigadier command registrations to publish
     * @param repository the kit catalog the cooldown placeholder reads
     */
    public record Wired(List<CommandRegistration> commands, KitRepository repository) {

        public Wired {
            commands = List.copyOf(commands);
            Objects.requireNonNull(repository, "repository");
        }
    }
}
