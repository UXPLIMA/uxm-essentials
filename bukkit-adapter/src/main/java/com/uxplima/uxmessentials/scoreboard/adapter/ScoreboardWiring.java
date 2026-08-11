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
import com.uxplima.uxmessentials.scoreboard.adapter.inbound.gui.ScoreboardSettingsView;
import com.uxplima.uxmessentials.scoreboard.adapter.inbound.listener.ScoreboardConnectionListener;
import com.uxplima.uxmessentials.scoreboard.adapter.outbound.PdcScoreboardVisibilityStore;
import com.uxplima.uxmessentials.scoreboard.adapter.outbound.ScoreboardRenderTask;
import com.uxplima.uxmessentials.scoreboard.adapter.outbound.ScoreboardRenderer;
import com.uxplima.uxmessentials.scoreboard.application.ToggleScoreboard;
import com.uxplima.uxmessentials.scoreboard.application.port.ScoreboardVisibilityStore;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayouts;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.adapter.outbound.hud.AnimationRegistry;
import com.uxplima.uxmessentials.shared.adapter.outbound.nametag.NameVisibilityCoordinator;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
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
 * sidebar. The {@code /scoreboard} confirmations are {@code MessageKey}s through the {@link Notifier}; the
 * sidebar content is raw operator MiniMessage, keeping the parity-checked keys and the unchecked operator content
 * apart. On stop the render timer is halted and every active board is restored so a disable or reload tears down
 * cleanly.
 */
@NullMarked
public final class ScoreboardWiring {

    private static final String MODULE_DIR = "modules/scoreboard";

    private ScoreboardWiring() {}

    /**
     * Build the scoreboard adapters and use case from {@code plugin} and {@code ctx}, ready to register. The shared
     * {@code nameVisibility} coordinator (built once in the bootstrap and also handed to the nametags wiring) is
     * re-applied after every per-player board switch through the {@code SidebarManager} board-switch callback, so a
     * wearer whose vanilla name is hidden keeps the hide-team after the {@code setScoreboard} that resets the client
     * team registry.
     */
    public static Wired wire(
            Plugin plugin,
            ModuleContext ctx,
            NameVisibilityCoordinator nameVisibility,
            GuiLayouts guiLayouts,
            Menus menus) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(nameVisibility, "nameVisibility");
        Objects.requireNonNull(guiLayouts, "guiLayouts");
        Objects.requireNonNull(menus, "menus");
        KernelPorts kernel = ctx.kernel();
        Path dir = plugin.getDataFolder().toPath().resolve(MODULE_DIR);
        ScoreboardSettings settings = new ScoreboardSettings(dir, kernel.log());
        AtomicBoolean running = new AtomicBoolean(true);

        ScoreboardVisibilityStore visibility = new PdcScoreboardVisibilityStore(plugin);
        // The animation registry holds the stateful uxmLib animators, so it is built once from the load-time catalog,
        // shared by the renderer (which reads frames) and the render task (which advances the clock once a tick).
        AnimationRegistry animations = new AnimationRegistry(settings.animations());
        ScoreboardRenderer renderer =
                new ScoreboardRenderer(sidebarManager(nameVisibility), visibility, settings::boards, animations);
        Notifier notifier = new Notifier(kernel.messages(), kernel.messageSink());
        ToggleScoreboard toggle = new ToggleScoreboard(visibility, notifier, kernel.events());
        ScoreboardRenderTask renderTask = new ScoreboardRenderTask(
                kernel.scheduler(), renderer, animations, kernel.log(), settings::refreshInterval, running::get);

        // The settings panel reuses the SP0 GUI framework over the shared catalog and the data-folder layout loader.
        // It carries the single show/hide toggle the /scoreboard command flips (the board a viewer sees is resolved
        // automatically by condition + priority, so there is no board-picker to expose). The render loop reconciles
        // the live board on its next tick from the same PDC bit. /scoreboard gui and the /uxmess gui hub both open it.
        GuiText guiText = new GuiText(kernel.messages());
        ScoreboardSettingsView settingsView = new ScoreboardSettingsView(
                guiText, kernel.scheduler(), guiLayouts, kernel.messages(), visibility, toggle, menus);

        List<CommandRegistration> commands =
                List.of(new ScoreboardCommand(toggle, renderer, kernel.scheduler(), kernel.messages(), settingsView));
        List<Listener> listeners = List.of(new ScoreboardConnectionListener(renderer, kernel.scheduler()));
        return new Wired(
                commands,
                listeners,
                renderer,
                renderTask,
                running,
                visibility,
                toggle,
                settingsView,
                kernel.scheduler());
    }

    private static SidebarManager sidebarManager(NameVisibilityCoordinator nameVisibility) {
        ScoreboardManager manager =
                Objects.requireNonNull(Bukkit.getScoreboardManager(), "the server scoreboard manager is unavailable");
        SidebarManager sidebars = new SidebarManager(manager);
        // After every board switch (create/show or remove/restore) the client's team registry is reset, so re-apply
        // the vanilla-name-hide team on the player's new current board — survival of the per-player board switch is the
        // whole reason the lib exposes this callback. A no-op for any player without an active hidden nametag.
        sidebars.onBoardSwitch(nameVisibility::reapply);
        return sidebars;
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
     * @param visibility the per-player "hidden" preference store, exposed for the {@code scoreboard_*} PAPI seam
     * @param toggle the flip use case, shared by the command, the settings panel and the published write
     * @param settingsView the per-player settings panel registered on the {@code /uxmess gui} hub
     * @param scheduler the kernel scheduler, used to enumerate the roster on the global thread and clear each board
     *     on its owner's region thread when the module stops
     */
    public record Wired(
            List<CommandRegistration> commands,
            List<Listener> listeners,
            ScoreboardRenderer renderer,
            ScoreboardRenderTask renderTask,
            AtomicBoolean running,
            ScoreboardVisibilityStore visibility,
            ToggleScoreboard toggle,
            ScoreboardSettingsView settingsView,
            Scheduler scheduler) {

        public Wired {
            commands = List.copyOf(commands);
            listeners = List.copyOf(listeners);
            Objects.requireNonNull(renderer, "renderer");
            Objects.requireNonNull(renderTask, "renderTask");
            Objects.requireNonNull(running, "running");
            Objects.requireNonNull(visibility, "visibility");
            Objects.requireNonNull(toggle, "toggle");
            Objects.requireNonNull(settingsView, "settingsView");
            Objects.requireNonNull(scheduler, "scheduler");
        }

        /** Arm the render timer. */
        public void startBackgroundWork() {
            renderTask.start();
        }

        /**
         * Stop the render timer and tear down every active board so a disable/reload leaves no stale display. The
         * roster is enumerated on the global region thread (Folia forbids iterating {@code Bukkit.getOnlinePlayers()}
         * off it) and each board is cleared on its owner's entity thread, where {@code setScoreboard} is valid.
         */
        public void stop() {
            running.set(false);
            scheduler.onGlobal(() -> {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    PlayerRef ref = BukkitRefs.toRef(player);
                    scheduler.onEntity(ref, () -> {
                        Player live = Bukkit.getPlayer(ref.uuid());
                        if (live != null && live.isOnline()) {
                            renderer.clear(live);
                        }
                    });
                }
            });
        }
    }
}
