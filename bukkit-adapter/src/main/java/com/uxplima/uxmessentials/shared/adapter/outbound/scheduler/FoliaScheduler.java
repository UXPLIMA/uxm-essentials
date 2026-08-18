package com.uxplima.uxmessentials.shared.adapter.outbound.scheduler;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link com.uxplima.uxmessentials.shared.application.port.Scheduler} implementation on Paper's
 * region-aware scheduler family. Each port method dispatches to the most specific Folia scheduler so
 * the same code runs on regular Paper (where the schedulers shim onto the single main thread) and on
 * Folia (where they target the owning region thread).
 *
 * <p>The adapter holds {@link Plugin} rather than {@code JavaPlugin}: the concrete plugin class is
 * bootstrap-only, and the schedulers need only the {@code Plugin} contract.
 */
@NullMarked
public final class FoliaScheduler implements com.uxplima.uxmessentials.shared.application.port.Scheduler {

    private final Plugin plugin;

    public FoliaScheduler(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    /**
     * True once the plugin is disabling. Paper's region schedulers reject new tasks on a disabled plugin, so the
     * region-dispatch methods short-circuit here. The only work scheduled during teardown is best-effort display
     * cleanup (tablist, scoreboard, open menus), which is unnecessary on a full shutdown because the affected
     * players are being disconnected anyway. On a module hot-reload the plugin stays enabled, so this is false and
     * the cleanup still runs.
     */
    private boolean disabled() {
        return !plugin.isEnabled();
    }

    @Override
    public void onGlobal(Runnable task) {
        Objects.requireNonNull(task, "task");
        if (disabled()) {
            return;
        }
        Bukkit.getGlobalRegionScheduler().execute(plugin, task);
    }

    @Override
    public void onRegion(Position position, Runnable task) {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(task, "task");
        if (disabled()) {
            return;
        }
        World world = Bukkit.getWorld(position.world().uid());
        if (world == null) {
            return; // the world is unloaded; the region work has nothing to bind to
        }
        Bukkit.getRegionScheduler().execute(plugin, world, position.blockX() >> 4, position.blockZ() >> 4, task);
    }

    @Override
    public void onEntity(PlayerRef player, Runnable task) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(task, "task");
        if (disabled()) {
            return;
        }
        Player bukkit = Bukkit.getPlayer(player.uuid());
        if (bukkit == null || !bukkit.isOnline()) {
            return; // the entity scheduler refuses a despawned entity, silent no-op (docs/02 §2.4)
        }
        bukkit.getScheduler().execute(plugin, task, null, 1L);
    }

    @Override
    public void onEntity(PlayerRef player, Runnable task, Runnable retired) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(retired, "retired");
        if (disabled()) {
            return;
        }
        Player bukkit = Bukkit.getPlayer(player.uuid());
        if (bukkit == null || !bukkit.isOnline()) {
            retired.run(); // no live entity to schedule on; tell the caller now instead of dropping it
            return;
        }
        // The entity scheduler runs the retired callback if the entity is removed before the task fires,
        // so a caller waiting on a result is released with its fallback rather than left to time out.
        bukkit.getScheduler().execute(plugin, task, retired, 1L);
    }

    @Override
    public boolean onGlobalThread() {
        return Bukkit.isGlobalTickThread();
    }

    @Override
    public boolean ownsEntity(PlayerRef player) {
        Objects.requireNonNull(player, "player");
        Player bukkit = Bukkit.getPlayer(player.uuid());
        return bukkit != null && bukkit.isOnline() && Bukkit.isOwnedByCurrentRegion(bukkit);
    }

    @Override
    public void async(Runnable task) {
        Objects.requireNonNull(task, "task");
        Bukkit.getAsyncScheduler().runNow(plugin, ignored -> task.run());
    }

    @Override
    public void asyncAfter(Duration delay, Runnable task) {
        Objects.requireNonNull(delay, "delay");
        Objects.requireNonNull(task, "task");
        long millis = Math.max(0L, delay.toMillis());
        Bukkit.getAsyncScheduler()
                .runDelayed(plugin, ignored -> task.run(), Math.max(1L, millis), TimeUnit.MILLISECONDS);
    }

    @Override
    public void laterGlobal(Duration delay, Runnable task) {
        Objects.requireNonNull(delay, "delay");
        Objects.requireNonNull(task, "task");
        // Ticks, floored to one: the global scheduler rejects a zero delay, and the next tick is the soonest
        // anything can run anyway.
        long ticks = Math.max(1L, delay.toMillis() / 50L);
        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, ignored -> task.run(), ticks);
    }

    @Override
    public AutoCloseable repeatGlobal(Runnable task, Duration initialDelay, Duration period) {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(initialDelay, "initialDelay");
        Objects.requireNonNull(period, "period");
        // Convert durations to ticks (20 ticks/s); floor to 1 so callers that pass Duration.ZERO
        // still get a valid schedule rather than an API exception.
        long initTicks = Math.max(1L, initialDelay.toMillis() / 50L);
        long periodTicks = Math.max(1L, period.toMillis() / 50L);
        ScheduledTask handle =
                Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, ignored -> task.run(), initTicks, periodTicks);
        return handle::cancel;
    }
}
