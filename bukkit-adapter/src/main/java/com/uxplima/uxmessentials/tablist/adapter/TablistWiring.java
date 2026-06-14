package com.uxplima.uxmessentials.tablist.adapter;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmessentials.tablist.adapter.inbound.listener.TablistConnectionListener;
import com.uxplima.uxmessentials.tablist.adapter.outbound.TablistRenderTask;
import com.uxplima.uxmessentials.tablist.adapter.outbound.TablistRenderer;
import com.uxplima.uxmlib.hud.Tablist;
import org.jspecify.annotations.NullMarked;

/**
 * Constructs the tablist context's adapters over the injected kernel ports and the operator content under
 * {@code modules/tablist/config.conf}, and produces everything the plugin must register: the join/quit connection
 * listener and the self-rescheduling render timer on the {@code Scheduler} port. This is the one place the tablist
 * context is wired.
 *
 * <p>The tablist is always-on for every viewer when enabled — there is no per-player visibility toggle, so the context
 * publishes no command. It persists nothing: the header/footer content is config-authored under
 * {@code modules/tablist/config.conf}. The renderer dogfoods uxmLib's {@link Tablist}; the render timer on the
 * {@code Scheduler} port is stopped and every active header/footer cleared on disable so a disable or reload tears
 * down cleanly.
 */
@NullMarked
public final class TablistWiring {

    private static final String MODULE_DIR = "modules/tablist";

    private TablistWiring() {}

    /** Build the tablist adapters from {@code plugin} and {@code ctx}, ready to register. */
    public static Wired wire(Plugin plugin, ModuleContext ctx) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(ctx, "ctx");
        KernelPorts kernel = ctx.kernel();
        Path dir = plugin.getDataFolder().toPath().resolve(MODULE_DIR);
        TablistSettings settings = new TablistSettings(dir, kernel.log());
        AtomicBoolean running = new AtomicBoolean(true);

        TablistRenderer renderer = new TablistRenderer(settings::formats);
        TablistRenderTask renderTask =
                new TablistRenderTask(kernel.scheduler(), renderer, settings::refreshInterval, running::get);

        List<CommandRegistration> commands = List.of();
        List<Listener> listeners = List.of(new TablistConnectionListener(renderer));
        return new Wired(commands, listeners, renderer, renderTask, running);
    }

    /**
     * Everything the tablist module contributes once wired: the connection listener, the self-rescheduling render
     * timer, and the {@code running} flag the timer observes. The renderer is held so {@link #stop()} can clear every
     * active header/footer. The command list is always empty — the tablist has no per-player toggle — but it is kept
     * to mirror the other contexts' {@code Wired} shape so the bootstrap wires every context the same way.
     *
     * @param commands the Brigadier command registrations to publish (always empty for tablist)
     * @param listeners the join/quit listener to register
     * @param renderer the per-player renderer, used to clear header/footer on stop
     * @param renderTask the self-rescheduling render timer, armed by the caller
     * @param running the flag flipped false on stop so the render timer exits
     */
    public record Wired(
            List<CommandRegistration> commands,
            List<Listener> listeners,
            TablistRenderer renderer,
            TablistRenderTask renderTask,
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

        /** Stop the render timer and clear every active header/footer so a disable/reload leaves no stale tablist. */
        public void stop() {
            running.set(false);
            for (Player player : Bukkit.getOnlinePlayers()) {
                renderer.clear(player);
            }
        }
    }
}
