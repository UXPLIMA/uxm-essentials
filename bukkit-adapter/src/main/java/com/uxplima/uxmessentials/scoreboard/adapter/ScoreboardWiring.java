package com.uxplima.uxmessentials.scoreboard.adapter;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.scoreboard.ScoreboardManager;

import com.uxplima.uxmessentials.scoreboard.adapter.inbound.command.ScoreboardCommand;
import com.uxplima.uxmessentials.scoreboard.adapter.inbound.listener.ScoreboardConnectionListener;
import com.uxplima.uxmessentials.scoreboard.adapter.outbound.PdcScoreboardVisibilityStore;
import com.uxplima.uxmessentials.scoreboard.adapter.outbound.ScoreboardRenderTask;
import com.uxplima.uxmessentials.scoreboard.adapter.outbound.ScoreboardRenderer;
import com.uxplima.uxmessentials.scoreboard.application.ScoreboardNotifier;
import com.uxplima.uxmessentials.scoreboard.application.ToggleScoreboard;
import com.uxplima.uxmessentials.scoreboard.application.port.ScoreboardVisibilityStore;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmlib.hud.scoreboard.SidebarManager;
import org.jspecify.annotations.NullMarked;

/**
 * Constructs the scoreboard context's adapters and use case over the injected kernel ports and the operator content
 * under {@code modules/scoreboard/config.conf}, and produces everything the plugin must register: the single
 * {@code /scoreboard} (alias {@code /sb}) Brigadier command, the join/quit connection listener, and the
 * self-rescheduling render timer on the {@code Scheduler} port. This is the one place the scoreboard context is wired.
 *
 * <p>The context persists nothing: the per-player "hidden" bit is PDC-backed (survives relog) and the display content
 * is config-authored. The renderer dogfoods uxmLib's {@link SidebarManager} (built over the server's
 * {@code ScoreboardManager}). The tablist header/footer is a separate module now, so this context owns only the
 * sidebar. The {@code /scoreboard} confirmations are {@code MessageKey}s through the {@link ScoreboardNotifier}; the
 * sidebar content is raw operator MiniMessage, keeping the parity-checked keys and the unchecked operator content
 * apart. On stop the render timer is halted and every active board is restored so a disable or reload tears down
 * cleanly.
 */
@NullMarked
public final class ScoreboardWiring {

    private static final String MODULE_DIR = "modules/scoreboard";

    private ScoreboardWiring() {}

    /** Build the scoreboard adapters and use case from {@code plugin} and {@code ctx}, ready to register. */
    public static Wired wire(Plugin plugin, ModuleContext ctx) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(ctx, "ctx");
        KernelPorts kernel = ctx.kernel();
        Path dir = plugin.getDataFolder().toPath().resolve(MODULE_DIR);
        ScoreboardSettings settings = new ScoreboardSettings(dir, kernel.log());
        AtomicBoolean running = new AtomicBoolean(true);

        ScoreboardVisibilityStore visibility = new PdcScoreboardVisibilityStore(plugin);
        ScoreboardRenderer renderer = new ScoreboardRenderer(sidebarManager(), visibility, settings::content);
        ScoreboardNotifier notifier = new ScoreboardNotifier(kernel.messages(), kernel.messageSink());
        ToggleScoreboard toggle = new ToggleScoreboard(visibility, notifier, kernel.events());
        ScoreboardRenderTask renderTask =
                new ScoreboardRenderTask(kernel.scheduler(), renderer, settings::refreshInterval, running::get);

        List<CommandRegistration> commands =
                List.of(new ScoreboardCommand(toggle, renderer, kernel.scheduler(), kernel.messages()));
        List<Listener> listeners = List.of(new ScoreboardConnectionListener(renderer, kernel.scheduler()));
        return new Wired(commands, listeners, renderer, renderTask, running);
    }

    private static SidebarManager sidebarManager() {
        ScoreboardManager manager =
                Objects.requireNonNull(Bukkit.getScoreboardManager(), "the server scoreboard manager is unavailable");
        return new SidebarManager(manager);
    }

    /**
     * Everything the scoreboard module contributes once wired: the {@code /scoreboard} command, the connection
     * listener, the self-rescheduling render timer, and the {@code running} flag the timer observes. The renderer is
     * held so {@link #stop()} can tear down every active board.
     *
     * @param commands the Brigadier command registrations to publish
     * @param listeners the join/quit listener to register
     * @param renderer the per-player renderer, used to tear down boards on stop
     * @param renderTask the self-rescheduling render timer, armed by the caller
     * @param running the flag flipped false on stop so the render timer exits
     */
    public record Wired(
            List<CommandRegistration> commands,
            List<Listener> listeners,
            ScoreboardRenderer renderer,
            ScoreboardRenderTask renderTask,
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

        /** Stop the render timer and tear down every active board so a disable/reload leaves no stale display. */
        public void stop() {
            running.set(false);
            for (Player player : Bukkit.getOnlinePlayers()) {
                renderer.clear(player);
            }
        }
    }
}
