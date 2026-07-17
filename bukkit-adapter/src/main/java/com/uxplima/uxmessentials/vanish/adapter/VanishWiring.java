package com.uxplima.uxmessentials.vanish.adapter;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmessentials.vanish.adapter.inbound.command.VanishCommand;
import com.uxplima.uxmessentials.vanish.adapter.inbound.listener.VanishLifecycleListener;
import com.uxplima.uxmessentials.vanish.adapter.inbound.listener.VanishProtectionListener;
import com.uxplima.uxmessentials.vanish.adapter.inbound.listener.VanishSilentContainerListener;
import com.uxplima.uxmessentials.vanish.adapter.outbound.BukkitVanishBuffs;
import com.uxplima.uxmessentials.vanish.adapter.outbound.BukkitVanishLevelResolver;
import com.uxplima.uxmessentials.vanish.adapter.outbound.BukkitVanishView;
import com.uxplima.uxmessentials.vanish.adapter.outbound.InMemoryVanishStore;
import com.uxplima.uxmessentials.vanish.adapter.outbound.PdcVanishPickup;
import com.uxplima.uxmessentials.vanish.adapter.outbound.VanishActionBar;
import com.uxplima.uxmessentials.vanish.adapter.outbound.VanishConnectionMessenger;
import com.uxplima.uxmessentials.vanish.application.ListVanished;
import com.uxplima.uxmessentials.vanish.application.SetVanishLevel;
import com.uxplima.uxmessentials.vanish.application.ToggleVanish;
import com.uxplima.uxmessentials.vanish.application.VanishConfig;
import com.uxplima.uxmessentials.vanish.application.VanishNotifier;
import com.uxplima.uxmessentials.vanish.application.port.VanishBuffs;
import com.uxplima.uxmessentials.vanish.application.port.VanishLevelResolver;
import com.uxplima.uxmessentials.vanish.application.port.VanishPickupPreferences;
import com.uxplima.uxmessentials.vanish.application.port.VanishStore;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Constructs the vanish context's adapters and use cases over the injected kernel ports, and produces the {@code
 * /vanish} command plus the join/quit listener the plugin registers. This is the one place the vanish context is
 * wired — nothing else news up its classes.
 *
 * <p>The context persists nothing: the vanish state is the transient in-memory {@link InMemoryVanishStore}, the single
 * authority every consumer (messaging, nametags, staff) reads. Its outbound adapters are the store, the level-aware
 * {@link BukkitVanishView}, and the {@link BukkitVanishLevelResolver} that reads a player's see/use levels from their
 * permissions. The store, the toggle, and the resolver are exposed on {@link Wired} so bootstrap can hand them to the
 * consumers' vanish gates (which now read the layered see level) and to staff-mode vanish. On stop the store is cleared
 * so a disable or reload leaves zero residual state.
 */
@NullMarked
public final class VanishWiring {

    /** How often the action-bar indicator is re-sent to a vanished player, chosen to outrun the vanilla bar fade. */
    private static final Duration ACTION_BAR_REFRESH = Duration.ofSeconds(1);

    private VanishWiring() {}

    /** Build the vanish adapters and use cases from {@code plugin} and {@code ctx}, ready to register. */
    public static Wired wire(Plugin plugin, ModuleContext ctx) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(ctx, "ctx");
        KernelPorts kernel = ctx.kernel();
        VanishConfig config = VanishConfig.from(ctx.config());

        InMemoryVanishStore store = new InMemoryVanishStore();
        BukkitVanishLevelResolver levels = new BukkitVanishLevelResolver();
        BukkitVanishView view = new BukkitVanishView(plugin, kernel.scheduler(), levels);
        VanishNotifier notifier = new VanishNotifier(kernel.messages(), kernel.messageSink());
        VanishBuffs buffs = new BukkitVanishBuffs(
                plugin.getServer(), kernel.scheduler(), config.nightVision(), config.allowFlight());
        VanishPickupPreferences pickup = new PdcVanishPickup(plugin.getServer(), config.pickupItems());
        ToggleVanish toggleVanish = new ToggleVanish(store, view, levels, notifier, buffs);
        SetVanishLevel setVanishLevel = new SetVanishLevel(store, view, levels, buffs);
        ListVanished listVanished = new ListVanished(store, levels);
        VanishConnectionMessenger messenger =
                new VanishConnectionMessenger(kernel.scheduler(), kernel.messageSink(), levels, config);
        VanishActionBar actionBar =
                new VanishActionBar(plugin.getServer(), kernel.scheduler(), kernel.messages(), store, config);

        List<CommandRegistration> commands = List.of(new VanishCommand(
                toggleVanish, listVanished, pickup, messenger, actionBar, plugin.getServer(), kernel.messages()));
        List<Listener> listeners = List.of(
                new VanishLifecycleListener(store, view, setVanishLevel, toggleVanish, config, plugin.getServer()),
                new VanishProtectionListener(store, config, pickup),
                new VanishSilentContainerListener(
                        store, kernel.scheduler(), plugin.getServer(), config.silentChests()));
        // A single global timer re-arms the action-bar indicator; a disabled indicator arms no timer at all.
        @Nullable AutoCloseable actionBarTask = config.actionBar()
                ? kernel.scheduler().repeatGlobal(actionBar::refresh, ACTION_BAR_REFRESH, ACTION_BAR_REFRESH)
                : null;
        return new Wired(commands, listeners, store, toggleVanish, levels, actionBarTask);
    }

    /**
     * Everything the vanish module contributes once wired: the {@code /vanish} command, the join/quit listener, the
     * in-memory store (the single vanish authority, cleared on stop), the {@link ToggleVanish} use case (so staff-mode
     * vanish routes through it), and the {@link VanishLevelResolver} (so the messaging/nametags gates read the same
     * layered see level).
     *
     * @param commands the Brigadier command registrations to publish
     * @param listeners the join/quit listener to register
     * @param store the in-memory vanish authority, exposed for the messaging/nametags gates and cleared on stop
     * @param toggleVanish the vanish use case, exposed for staff-mode vanish
     * @param levels the see/use level resolver, exposed for the messaging/nametags vanish gates
     * @param actionBarTask the repeating action-bar refresh handle, cancelled on stop; {@code null} when the indicator
     *     is disabled and no timer was armed
     */
    public record Wired(
            List<CommandRegistration> commands,
            List<Listener> listeners,
            InMemoryVanishStore store,
            ToggleVanish toggleVanish,
            VanishLevelResolver levels,
            @Nullable AutoCloseable actionBarTask) {

        public Wired {
            commands = List.copyOf(commands);
            listeners = List.copyOf(listeners);
            Objects.requireNonNull(store, "store");
            Objects.requireNonNull(toggleVanish, "toggleVanish");
            Objects.requireNonNull(levels, "levels");
        }

        /** Expose the store as the shared {@link VanishStore} authority the consumers' vanish gates read. */
        public VanishStore vanishStore() {
            return store;
        }

        /** Drop the vanish state and cancel the action-bar timer so a disable or reload leaves no residual state. */
        public void stop() {
            store.clear();
            if (actionBarTask != null) {
                try {
                    actionBarTask.close();
                } catch (Exception cancelFailed) {
                    // The repeating-task handle's cancel does not throw; close() only declares the checked type.
                    throw new IllegalStateException("failed to cancel the vanish action-bar task", cancelFailed);
                }
            }
        }
    }
}
