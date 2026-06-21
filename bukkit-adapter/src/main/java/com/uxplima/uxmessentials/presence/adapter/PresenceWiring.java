package com.uxplima.uxmessentials.presence.adapter;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.presence.adapter.inbound.command.PresenceCommands;
import com.uxplima.uxmessentials.presence.adapter.inbound.command.PresenceSettingsCommand;
import com.uxplima.uxmessentials.presence.adapter.inbound.gui.PresenceSettingsView;
import com.uxplima.uxmessentials.presence.adapter.inbound.listener.AfkPickupListener;
import com.uxplima.uxmessentials.presence.adapter.inbound.listener.PresenceActivityListener;
import com.uxplima.uxmessentials.presence.adapter.inbound.listener.PresenceLifecycleListener;
import com.uxplima.uxmessentials.presence.adapter.inbound.listener.SleepExclusionListener;
import com.uxplima.uxmessentials.presence.adapter.outbound.AfkSweep;
import com.uxplima.uxmessentials.presence.adapter.outbound.BukkitPresenceAudience;
import com.uxplima.uxmessentials.presence.adapter.outbound.BukkitVisibilityApplier;
import com.uxplima.uxmessentials.presence.adapter.outbound.InMemoryPresenceStore;
import com.uxplima.uxmessentials.presence.adapter.outbound.PdcNickStore;
import com.uxplima.uxmessentials.presence.application.ClearAfkOnActivity;
import com.uxplima.uxmessentials.presence.application.ClearNick;
import com.uxplima.uxmessentials.presence.application.MarkAfk;
import com.uxplima.uxmessentials.presence.application.PresenceNotifier;
import com.uxplima.uxmessentials.presence.application.ResolveVisibility;
import com.uxplima.uxmessentials.presence.application.SetNick;
import com.uxplima.uxmessentials.presence.application.ToggleVanish;
import com.uxplima.uxmessentials.presence.application.port.NickStore;
import com.uxplima.uxmessentials.presence.application.port.PresenceAudience;
import com.uxplima.uxmessentials.presence.application.port.PresenceStore;
import com.uxplima.uxmessentials.presence.application.port.VisibilityApplier;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayouts;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.ManagementGuiEntry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.ManagementGuiRegistry;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import org.jspecify.annotations.NullMarked;

/**
 * Constructs the presence context's adapters and use cases over the injected kernel ports, and produces
 * everything the plugin must register: the Brigadier command list, the move/chat activity and join/quit
 * lifecycle listeners, and the self-rescheduling AFK idle sweep. This is the one place the presence context is
 * wired — nothing else news up its classes.
 *
 * <p>The context persists nothing: the presence map is an in-memory {@link InMemoryPresenceStore}. Its outbound
 * adapters are the store, the {@link BukkitVisibilityApplier} (driving the {@code canSee} graph that the
 * messaging and teleport vanish checks read), and the {@link BukkitPresenceAudience}, all sitting on the kernel
 * {@code Scheduler} port. {@link ResolveVisibility} is exposed on {@link Wired} so a cross-context lookup
 * (messaging / teleport) can query the vanish rule explicitly; both also honour vanish structurally through the
 * {@code canSee} graph this wiring drives, so the coupling degrades to "fully visible" when presence is off.
 */
@NullMarked
public final class PresenceWiring {

    private PresenceWiring() {}

    /** Build the presence adapters and use cases from {@code plugin} and {@code ctx}, ready to register. */
    public static Wired wire(
            Plugin plugin, ModuleContext ctx, GuiLayouts guiLayouts, ManagementGuiRegistry guiRegistry) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(guiLayouts, "guiLayouts");
        Objects.requireNonNull(guiRegistry, "guiRegistry");
        KernelPorts kernel = ctx.kernel();
        PresenceSettings settings = new PresenceSettings(ctx.config());
        Clock clock = Clock.systemUTC();
        AtomicBoolean running = new AtomicBoolean(true);

        InMemoryPresenceStore store = new InMemoryPresenceStore(clock);
        VisibilityApplier visibility = new BukkitVisibilityApplier(plugin, kernel.scheduler());
        PresenceAudience audience = new BukkitPresenceAudience();
        PresenceNotifier notifier = new PresenceNotifier(kernel.messages(), kernel.messageSink());
        NickStore nicks = new PdcNickStore(plugin, kernel.scheduler());

        PresenceServices services = assemble(kernel, store, visibility, audience, notifier, nicks, clock);
        AfkSweep sweep = new AfkSweep(
                kernel.scheduler(),
                store,
                services.markAfk(),
                settings.sweepInterval(),
                settings.idleThreshold(),
                running::get,
                clock);
        // The per-player settings panel reuses the SP0 GUI framework over the shared catalog and the data-folder
        // layout loader. It reads the live presence store and flips AFK / vanish through the same use cases the
        // /afk and /vanish commands do, so /presencesettings, the panel, and the commands all see one state. The
        // presence entry on the /uxmess gui hub opens the same panel for an admin (gated uxmessentials.presence.gui).
        GuiText guiText = new GuiText(kernel.messages());
        PresenceSettingsView settingsView =
                new PresenceSettingsView(guiText, kernel.scheduler(), guiLayouts, kernel.messages(), services, store);
        guiRegistry.register(new ManagementGuiEntry(
                "presence",
                com.uxplima.uxmessentials.presence.application.PresenceMessageKey.GUI_SETTINGS_TITLE,
                org.bukkit.Material.CLOCK,
                "uxmessentials.presence.gui",
                settingsView::open));
        List<CommandRegistration> commands = new ArrayList<>(PresenceCommands.all(services, kernel.messages()));
        commands.add(new PresenceSettingsCommand(services, kernel.messages(), settingsView));
        List<Listener> listeners = listeners(kernel, settings, services, store, visibility, notifier);
        return new Wired(commands, listeners, sweep, store, services, running, clock);
    }

    private static List<Listener> listeners(
            KernelPorts kernel,
            PresenceSettings settings,
            PresenceServices services,
            InMemoryPresenceStore store,
            VisibilityApplier visibility,
            PresenceNotifier notifier) {
        List<Listener> listeners = new ArrayList<>();
        listeners.add(new PresenceActivityListener(
                services.clearAfk(), kernel.scheduler(), settings.activityPolicy(), settings.moveThresholdBlocks()));
        listeners.add(new PresenceLifecycleListener(store, visibility));
        if (settings.disablePickupWhileAfk()) {
            listeners.add(new AfkPickupListener(store, notifier));
        }
        if (settings.sleepIgnoresAfk()) {
            listeners.add(new SleepExclusionListener(store));
        }
        return List.copyOf(listeners);
    }

    private static PresenceServices assemble(
            KernelPorts kernel,
            PresenceStore store,
            VisibilityApplier visibility,
            PresenceAudience audience,
            PresenceNotifier notifier,
            NickStore nicks,
            Clock clock) {
        var events = kernel.events();
        MarkAfk markAfk = new MarkAfk(store, audience, notifier, events, clock);
        ClearAfkOnActivity clearAfk = new ClearAfkOnActivity(store, audience, notifier, events, clock);
        ToggleVanish toggleVanish = new ToggleVanish(store, visibility, notifier, events, clock);
        ResolveVisibility resolveVisibility = new ResolveVisibility(store, kernel.permissions());
        SetNick setNick = new SetNick(nicks, notifier);
        ClearNick clearNick = new ClearNick(nicks, notifier);
        return new PresenceServices(markAfk, clearAfk, toggleVanish, resolveVisibility, setNick, clearNick);
    }

    /**
     * Everything the presence module contributes once wired: the Brigadier commands, the activity/lifecycle
     * listeners, the self-rescheduling AFK sweep, the in-memory store (cleared on stop), the use cases (so the
     * vanish query is reachable cross-context), and the {@code running} flag the sweep observes.
     *
     * @param commands the Brigadier command registrations to publish
     * @param listeners the Bukkit listeners to register
     * @param afkSweep the self-rescheduling AFK idle sweep, armed by the caller
     * @param store the in-memory presence map, cleared on stop
     * @param services the constructed use cases, exposing the cross-context vanish query
     * @param running the flag flipped false on stop so the sweep exits
     * @param clock the presence clock the {@code afk_duration} placeholder measures against
     */
    public record Wired(
            List<CommandRegistration> commands,
            List<Listener> listeners,
            AfkSweep afkSweep,
            InMemoryPresenceStore store,
            PresenceServices services,
            AtomicBoolean running,
            Clock clock) {

        public Wired {
            commands = List.copyOf(commands);
            listeners = List.copyOf(listeners);
            Objects.requireNonNull(afkSweep, "afkSweep");
            Objects.requireNonNull(store, "store");
            Objects.requireNonNull(services, "services");
            Objects.requireNonNull(running, "running");
            Objects.requireNonNull(clock, "clock");
        }

        /** Arm the AFK idle sweep. */
        public void startBackgroundWork() {
            afkSweep.start();
        }

        /** Stop the sweep and drop the in-memory presence map. */
        public void stop() {
            running.set(false);
            store.clear();
        }
    }
}
