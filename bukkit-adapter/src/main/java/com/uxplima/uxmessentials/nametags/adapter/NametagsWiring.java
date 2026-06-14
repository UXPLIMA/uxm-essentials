package com.uxplima.uxmessentials.nametags.adapter;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.nametags.adapter.inbound.listener.NametagLifecycleListener;
import com.uxplima.uxmessentials.nametags.adapter.outbound.CanSeeNametagVanish;
import com.uxplima.uxmessentials.nametags.adapter.outbound.NametagRenderTask;
import com.uxplima.uxmessentials.nametags.adapter.outbound.NametagRenderer;
import com.uxplima.uxmessentials.nametags.application.port.NametagVanish;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.outbound.hud.AnimationRegistry;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmlib.hologram.HologramManager;
import com.uxplima.uxmlib.hologram.follow.HologramFollow;
import com.uxplima.uxmlib.scheduler.PaperScheduler;
import org.jspecify.annotations.NullMarked;

/**
 * Constructs the nametags context's adapters over the injected kernel ports and the operator content under
 * {@code modules/nametags/config.conf}, and produces everything the plugin must register: the join/quit/world-change
 * lifecycle listener and the self-rescheduling render timer on the {@code Scheduler} port. This is the one place the
 * nametags context is wired.
 *
 * <p>The nametag is always-on for every eligible wearer when enabled — there is no per-player visibility toggle, so
 * the context publishes no command. It persists nothing: the formats are config-authored. The renderer spawns a
 * viewer-restricted {@code TextDisplay} per wearer over uxmLib's {@link HologramManager}; the {@link HologramFollow}
 * that keeps each nametag above the head needs uxmLib's own Folia-aware {@code Scheduler}, obtained as a
 * {@link PaperScheduler} over the plugin exactly as the integration-wiring obtains it for the update notifier (the
 * kernel {@code Scheduler} port drives the render timer and every display mutation hop). The render timer is stopped
 * and every spawned nametag despawned on disable so a disable or reload tears down cleanly with no orphan entity.
 */
@NullMarked
public final class NametagsWiring {

    private static final String MODULE_DIR = "modules/nametags";

    // The follow task interpolation cadence: how often the nametag re-homes above the wearer's head. One tick keeps it
    // glued to fast movement; this is the uxmLib follow period, independent of the content render cadence.
    private static final Duration FOLLOW_PERIOD = Duration.ofMillis(50L);

    private NametagsWiring() {}

    /** Build the nametags adapters from {@code plugin} and {@code ctx}, ready to register. */
    public static Wired wire(Plugin plugin, ModuleContext ctx) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(ctx, "ctx");
        KernelPorts kernel = ctx.kernel();
        Path dir = plugin.getDataFolder().toPath().resolve(MODULE_DIR);
        NametagSettings settings = new NametagSettings(dir, kernel.log());
        AtomicBoolean running = new AtomicBoolean(true);

        // The animation registry holds the stateful uxmLib animators, so it is built once from the load-time catalog,
        // shared by the renderer (which reads frames) and the render task (which advances the clock once a tick).
        AnimationRegistry animations = new AnimationRegistry(settings.animations());
        HologramManager manager = new HologramManager();
        manager.installLifecycleListener(plugin);
        HologramFollow follow = new HologramFollow(new PaperScheduler(plugin));
        // Vanish is soft-coupled through Bukkit's canSee graph; it degrades to "everyone can see everyone" when the
        // presence module is off, so no nametag-side branch is needed for the presence-disabled case.
        NametagVanish vanish = new CanSeeNametagVanish();
        NametagRenderer renderer = new NametagRenderer(
                plugin, settings::formats, manager, follow, kernel.scheduler(), animations, vanish, FOLLOW_PERIOD);
        NametagRenderTask renderTask = new NametagRenderTask(
                kernel.scheduler(), renderer, animations, settings::refreshInterval, running::get);

        List<CommandRegistration> commands = List.of();
        List<Listener> listeners = List.of(new NametagLifecycleListener(renderer, kernel.scheduler(), animations));
        return new Wired(commands, listeners, renderer, renderTask, running);
    }

    /**
     * Everything the nametags module contributes once wired: the connection listener, the self-rescheduling render
     * timer, and the {@code running} flag the timer observes. The renderer is held so {@link #stop()} can despawn
     * every spawned nametag. The command list is always empty — the nametag has no per-player toggle — but it is kept
     * to mirror the other contexts' {@code Wired} shape so the bootstrap wires every context the same way.
     *
     * @param commands the Brigadier command registrations to publish (always empty for nametags)
     * @param listeners the join/quit/world-change listener to register
     * @param renderer the per-wearer renderer, used to despawn every nametag on stop
     * @param renderTask the self-rescheduling render timer, armed by the caller
     * @param running the flag flipped false on stop so the render timer exits
     */
    public record Wired(
            List<CommandRegistration> commands,
            List<Listener> listeners,
            NametagRenderer renderer,
            NametagRenderTask renderTask,
            AtomicBoolean running) {

        public Wired {
            commands = List.copyOf(commands);
            listeners = List.copyOf(listeners);
            Objects.requireNonNull(renderer, "renderer");
            Objects.requireNonNull(renderTask, "renderTask");
            Objects.requireNonNull(running, "running");
        }

        /** Arm the render timer. */
        public void startBackgroundWork() {
            renderTask.start();
        }

        /** Stop the render timer and despawn every spawned nametag so a disable/reload leaves no orphan entity. */
        public void stop() {
            running.set(false);
            renderer.despawnAll();
        }
    }
}
