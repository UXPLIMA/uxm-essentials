package com.uxplima.uxmessentials.holograms.adapter;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.holograms.adapter.inbound.command.HologramCommands;
import com.uxplima.uxmessentials.holograms.adapter.inbound.listener.HologramVisibilityListener;
import com.uxplima.uxmessentials.holograms.adapter.outbound.HologramRefreshTask;
import com.uxplima.uxmessentials.holograms.adapter.outbound.HologramRenderer;
import com.uxplima.uxmessentials.holograms.application.AddHologramLine;
import com.uxplima.uxmessentials.holograms.application.CreateHologram;
import com.uxplima.uxmessentials.holograms.application.DeleteHologram;
import com.uxplima.uxmessentials.holograms.application.HologramNotifier;
import com.uxplima.uxmessentials.holograms.application.ListHolograms;
import com.uxplima.uxmessentials.holograms.application.MoveHologram;
import com.uxplima.uxmessentials.holograms.application.RemoveHologramLine;
import com.uxplima.uxmessentials.holograms.application.SetHologramAppearance;
import com.uxplima.uxmessentials.holograms.application.SetHologramLine;
import com.uxplima.uxmessentials.holograms.application.SetHologramModel;
import com.uxplima.uxmessentials.holograms.application.SetHologramRefresh;
import com.uxplima.uxmessentials.holograms.application.SetHologramVisibility;
import com.uxplima.uxmessentials.holograms.application.port.HologramRepository;
import com.uxplima.uxmessentials.holograms.domain.Hologram;
import com.uxplima.uxmessentials.persistence.holograms.HologramRepositories;
import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.PlaceholderApiSupport;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmlib.hologram.HologramManager;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Constructs the holograms context's adapters and use cases over the injected kernel ports, the persistence
 * DSL, and the uxmLib native-Display hologram API, and produces the Brigadier command the plugin registers.
 * This is the one place the holograms context is wired — nothing else news up its classes.
 *
 * <p>The repository is the jOOQ adapter behind a Caffeine read-cache decorator (write-through at the
 * delegate, invalidate in the cache). The renderer holds a fresh {@link HologramManager}; the uxmLib lifecycle
 * listener is installed once here so per-player viewer caches stay honest. On wire, every stored hologram is
 * spawned (each on its own region thread through the kernel {@code Scheduler}) so a restart brings the
 * holograms back exactly as configured. On stop the {@code Wired} bundle despawns them all so no display
 * entity is orphaned across a reload.
 */
@NullMarked
public final class HologramsWiring {

    private HologramsWiring() {}

    /** The smallest cadence the refresh timer fires at — one second, the floor a refresh interval rounds to. */
    private static final Duration REFRESH_BASE = Duration.ofSeconds(1);

    private static final int REFRESH_BASE_TICKS = 20;

    /** Build the holograms adapters and use cases, and spawn the stored holograms. */
    public static Wired wire(Plugin plugin, ModuleContext ctx, Persistence persistence) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(persistence, "persistence");
        KernelPorts kernel = ctx.kernel();
        HologramRepository repository = HologramRepositories.cached(persistence);
        HologramManager manager = new HologramManager();
        manager.installLifecycleListener(plugin);
        // Hologram lines are one shared TextDisplay, so placeholders resolve server-globally (online, time, TPS);
        // the identity transform when PlaceholderAPI is absent, so a default server pays nothing.
        HologramRenderer renderer = new HologramRenderer(
                plugin,
                manager,
                kernel.scheduler(),
                kernel.log(),
                kernel.permissions(),
                PlaceholderApiSupport.globalBridge());
        HologramNotifier notifier = new HologramNotifier(kernel.messages(), kernel.messageSink());
        HologramServices services = assemble(kernel, repository, renderer, notifier);
        spawnStored(repository, renderer);
        // A joining player must pick up the permission-gated holograms they qualify for at once, not after a
        // refresh tick; this listener re-evaluates only the gated holograms for that one player.
        plugin.getServer().getPluginManager().registerEvents(new HologramVisibilityListener(renderer), plugin);
        AutoCloseable refreshTask = scheduleRefresh(kernel.scheduler(), repository, renderer);
        return new Wired(HologramCommands.all(services, kernel.messages()), renderer, repository, refreshTask);
    }

    private static AutoCloseable scheduleRefresh(
            Scheduler scheduler, HologramRepository repository, HologramRenderer renderer) {
        // A single global timer ticks every second and re-renders only the holograms whose interval is due, so
        // a server with no refreshing hologram pays just the empty iteration. The handle is closed on stop.
        HologramRefreshTask task = new HologramRefreshTask(repository::all, renderer::refresh, REFRESH_BASE_TICKS);
        return scheduler.repeatGlobal(task::tick, REFRESH_BASE, REFRESH_BASE);
    }

    private static HologramServices assemble(
            KernelPorts kernel, HologramRepository repository, HologramRenderer renderer, HologramNotifier notifier) {
        Clock clock = Clock.systemUTC();
        return new HologramServices(
                new CreateHologram(repository, renderer, notifier, kernel.events(), clock),
                new DeleteHologram(repository, renderer, notifier, kernel.events()),
                new ListHolograms(repository, notifier),
                new AddHologramLine(repository, renderer, notifier),
                new SetHologramLine(repository, renderer, notifier),
                new RemoveHologramLine(repository, renderer, notifier),
                new MoveHologram(repository, renderer, notifier),
                new SetHologramAppearance(repository, renderer, notifier),
                new SetHologramRefresh(repository, renderer, notifier),
                new SetHologramVisibility(repository, renderer, notifier),
                new SetHologramModel(repository, renderer, notifier));
    }

    private static void spawnStored(HologramRepository repository, HologramRenderer renderer) {
        // Each render hops onto the hologram's own region thread inside the renderer, so this is safe to call
        // straight from the enable path; a world that is not yet loaded is skipped with a warning there.
        for (Hologram hologram : repository.all()) {
            renderer.render(hologram);
        }
    }

    /**
     * Everything the holograms module contributes once wired: the single Brigadier command and the renderer
     * whose live display entities must be despawned on stop so a reload re-spawns cleanly.
     *
     * @param commands the Brigadier command registrations to publish
     * @param renderer the live-entity renderer, despawned on stop
     * @param repository the cached hologram repository the PAPI seam reads the server-wide count from
     * @param refreshTask the global refresh timer handle, cancelled on stop so no task outlives a disable
     */
    public record Wired(
            List<CommandRegistration> commands,
            HologramRenderer renderer,
            HologramRepository repository,
            AutoCloseable refreshTask) {

        public Wired {
            commands = List.copyOf(commands);
            Objects.requireNonNull(renderer, "renderer");
            Objects.requireNonNull(repository, "repository");
            Objects.requireNonNull(refreshTask, "refreshTask");
        }

        /** Cancel the refresh timer and despawn every spawned hologram so nothing is orphaned across a reload. */
        public void stop() {
            closeQuietly(refreshTask);
            renderer.despawnAll();
        }

        private static void closeQuietly(@Nullable AutoCloseable task) {
            if (task == null) {
                return;
            }
            try {
                task.close();
            } catch (Exception cancellation) {
                // The repeating-task handle's cancel does not throw; close() only declares the checked type.
            }
        }
    }
}
