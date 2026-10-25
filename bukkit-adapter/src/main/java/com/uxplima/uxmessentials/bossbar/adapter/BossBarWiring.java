package com.uxplima.uxmessentials.bossbar.adapter;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.bossbar.adapter.inbound.command.BossBarCommand;
import com.uxplima.uxmessentials.bossbar.adapter.inbound.listener.BossBarConnectionListener;
import com.uxplima.uxmessentials.bossbar.adapter.outbound.BossBarAnnouncements;
import com.uxplima.uxmessentials.bossbar.adapter.outbound.BossBarRenderTask;
import com.uxplima.uxmessentials.bossbar.adapter.outbound.BossBarRenderer;
import com.uxplima.uxmessentials.bossbar.adapter.outbound.PdcBossBarVisibilityStore;
import com.uxplima.uxmessentials.bossbar.adapter.outbound.RemainingTime;
import com.uxplima.uxmessentials.bossbar.application.ToggleBossBars;
import com.uxplima.uxmessentials.bossbar.application.port.BossBarVisibilityStore;
import com.uxplima.uxmessentials.bossbar.domain.BossBarConfig;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.adapter.outbound.hud.AnimationRegistry;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Builds the boss bar context over the kernel ports and {@code modules/bossbar/config.conf}: the renderer, the refresh
 * loop, {@code /bossbars} and the listener. The one place the context is put together.
 */
@NullMarked
public final class BossBarWiring {

    private static final String MODULE_DIR = "modules/bossbar";

    private BossBarWiring() {}

    public static Wired wire(Plugin plugin, ModuleContext ctx) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(ctx, "ctx");
        KernelPorts kernel = ctx.kernel();
        Path dir = plugin.getDataFolder().toPath().resolve(MODULE_DIR);
        BossBarSettings settings = new BossBarSettings(dir, kernel.log());
        AtomicBoolean running = new AtomicBoolean(true);

        PdcBossBarVisibilityStore visibility = new PdcBossBarVisibilityStore(plugin, settings::hiddenByDefault);
        AnimationRegistry animations = new AnimationRegistry(settings.animations());
        BossBarAnnouncements announcements = new BossBarAnnouncements();
        RemainingTime remaining = new RemainingTime(kernel.messages());
        BossBarRenderer renderer =
                new BossBarRenderer(settings::bars, visibility, animations, announcements, remaining, Instant::now);
        BossBarRenderTask renderTask = new BossBarRenderTask(
                kernel.scheduler(), renderer, animations, kernel.log(), settings::refreshInterval, running::get);
        ToggleBossBars toggle =
                new ToggleBossBars(visibility, new Notifier(kernel.messages(), kernel.messageSink()), kernel.events());

        List<CommandRegistration> commands = List.of(new BossBarCommand(
                toggle,
                renderer,
                announcements,
                settings::bars,
                renderTask::refreshNow,
                remaining,
                Instant::now,
                kernel.scheduler(),
                kernel.messages()));
        List<Listener> listeners = List.of(new BossBarConnectionListener(renderer, announcements, kernel.scheduler()));
        Runnable reload = () -> {
            settings.reload();
            animations.replace(settings.animations());
            renderTask.refreshNow();
        };
        return new Wired(
                commands,
                listeners,
                renderer,
                renderTask,
                running,
                kernel.scheduler(),
                reload,
                visibility,
                toggle,
                announcements,
                settings::bars);
    }

    /**
     * What the boss bar module puts on the server once wired.
     *
     * @param commands {@code /bossbars}
     * @param listeners the join, quit, world and game mode listener
     * @param renderer the bars on each screen, taken down on stop
     * @param renderTask the refresh loop, started by the caller
     * @param running cleared on stop so the loop ends
     * @param scheduler the kernel scheduler, to reach every player on stop
     * @param reload reads the file again and draws every player's bars
     * @param visibility the kept choice to hide the bars, read by the placeholders and the published query
     * @param toggle the use case the command and the published actions both hide and show through
     * @param announcements the bars a command or a consumer has put up for a while
     * @param bars the bars the file writes, as read last
     */
    public record Wired(
            List<CommandRegistration> commands,
            List<Listener> listeners,
            BossBarRenderer renderer,
            BossBarRenderTask renderTask,
            AtomicBoolean running,
            Scheduler scheduler,
            Runnable reload,
            BossBarVisibilityStore visibility,
            ToggleBossBars toggle,
            BossBarAnnouncements announcements,
            Supplier<BossBarConfig> bars) {

        public Wired {
            commands = List.copyOf(commands);
            listeners = List.copyOf(listeners);
            Objects.requireNonNull(renderer, "renderer");
            Objects.requireNonNull(renderTask, "renderTask");
            Objects.requireNonNull(running, "running");
            Objects.requireNonNull(scheduler, "scheduler");
            Objects.requireNonNull(reload, "reload");
            Objects.requireNonNull(visibility, "visibility");
            Objects.requireNonNull(toggle, "toggle");
            Objects.requireNonNull(announcements, "announcements");
            Objects.requireNonNull(bars, "bars");
        }

        /** Start the refresh loop and draw everybody already online, as after a reload of the plugin. */
        public void startBackgroundWork() {
            renderTask.start();
            renderTask.refreshNow();
        }

        /**
         * End the loop and take every bar off every screen. The players are read on the global region and each one's
         * bars are taken down on their own thread.
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
