package com.uxplima.uxmessentials.shared.adapter.outbound.scheduler;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

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

    @Override
    public void onGlobal(Runnable task) {
        Objects.requireNonNull(task, "task");
        Bukkit.getGlobalRegionScheduler().execute(plugin, task);
    }

    @Override
    public void onRegion(Position position, Runnable task) {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(task, "task");
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
        Player bukkit = Bukkit.getPlayer(player.uuid());
        if (bukkit == null || !bukkit.isOnline()) {
            return; // the entity scheduler refuses a despawned entity — silent no-op (docs/02 §2.4)
        }
        bukkit.getScheduler().execute(plugin, task, null, 1L);
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
}
