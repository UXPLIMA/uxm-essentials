package com.uxplima.uxmessentials.vanish.adapter;

import java.util.List;
import java.util.Objects;

import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmessentials.vanish.adapter.inbound.command.VanishCommand;
import com.uxplima.uxmessentials.vanish.adapter.inbound.listener.VanishLifecycleListener;
import com.uxplima.uxmessentials.vanish.adapter.outbound.BukkitVanishView;
import com.uxplima.uxmessentials.vanish.adapter.outbound.InMemoryVanishStore;
import com.uxplima.uxmessentials.vanish.application.ToggleVanish;
import com.uxplima.uxmessentials.vanish.application.VanishNotifier;
import com.uxplima.uxmessentials.vanish.application.port.VanishStore;
import org.jspecify.annotations.NullMarked;

/**
 * Constructs the vanish context's adapters and use case over the injected kernel ports, and produces the {@code
 * /vanish} command plus the join/quit listener the plugin registers. This is the one place the vanish context is
 * wired — nothing else news up its classes.
 *
 * <p>The context persists nothing: the vanish state is the transient in-memory {@link InMemoryVanishStore}, the single
 * authority every consumer (messaging, nametags, staff) reads. Its outbound adapters are the store and the
 * {@link BukkitVanishView} (which drives the {@code hidePlayer}/{@code showPlayer} graph on the kernel {@code Scheduler}
 * port). The store and the toggle are exposed on {@link Wired} so bootstrap can hand them to the consumers' vanish
 * gates and to staff-mode vanish. On stop the store is cleared so a disable or reload leaves zero residual state.
 */
@NullMarked
public final class VanishWiring {

    private VanishWiring() {}

    /** Build the vanish adapters and use case from {@code plugin} and {@code ctx}, ready to register. */
    public static Wired wire(Plugin plugin, ModuleContext ctx) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(ctx, "ctx");
        KernelPorts kernel = ctx.kernel();

        InMemoryVanishStore store = new InMemoryVanishStore();
        BukkitVanishView view = new BukkitVanishView(plugin, kernel.scheduler());
        VanishNotifier notifier = new VanishNotifier(kernel.messages(), kernel.messageSink());
        ToggleVanish toggleVanish = new ToggleVanish(store, view, notifier);

        List<CommandRegistration> commands = List.of(new VanishCommand(toggleVanish, kernel.messages()));
        List<Listener> listeners = List.of(new VanishLifecycleListener(store, view, plugin.getServer()));
        return new Wired(commands, listeners, store, toggleVanish);
    }

    /**
     * Everything the vanish module contributes once wired: the {@code /vanish} command, the join/quit listener, the
     * in-memory store (the single vanish authority, cleared on stop), and the {@link ToggleVanish} use case (so
     * staff-mode vanish routes through it).
     *
     * @param commands the Brigadier command registrations to publish
     * @param listeners the join/quit listener to register
     * @param store the in-memory vanish authority, exposed for the messaging/nametags gates and cleared on stop
     * @param toggleVanish the vanish use case, exposed for staff-mode vanish
     */
    public record Wired(
            List<CommandRegistration> commands,
            List<Listener> listeners,
            InMemoryVanishStore store,
            ToggleVanish toggleVanish) {

        public Wired {
            commands = List.copyOf(commands);
            listeners = List.copyOf(listeners);
            Objects.requireNonNull(store, "store");
            Objects.requireNonNull(toggleVanish, "toggleVanish");
        }

        /** Expose the store as the shared {@link VanishStore} authority the consumers' vanish gates read. */
        public VanishStore vanishStore() {
            return store;
        }

        /** Drop the vanish state so a disable or reload leaves no residual runtime state. */
        public void stop() {
            store.clear();
        }
    }
}
