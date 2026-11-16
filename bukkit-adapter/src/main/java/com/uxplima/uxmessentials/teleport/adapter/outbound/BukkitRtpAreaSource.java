package com.uxplima.uxmessentials.teleport.adapter.outbound;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.WorldBorder;

import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.teleport.application.port.RtpAreaSource;
import com.uxplima.uxmessentials.teleport.domain.SafeSearchArea;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The single owner of a world's live random-teleport zone. It reads the world border and the current per-world
 * radii to build the {@link SafeSearchArea} both the pre-warmed queue refills against and the {@code /rtp biome}
 * search draws from, so the two paths share one source of truth and one {@code /settpr} swap. The radii sit behind
 * an {@link AtomicReference} so {@code /settpr} can reset the zone at runtime with a single whole-record swap, an
 * in-flight read sees either the whole previous tuning or the whole new one, never a torn mix.
 *
 * <p>The border is read on the global thread, the only one allowed to read it, every {@link #BORDER_REFRESH}, and
 * kept as plain values; the area is built from those, so the queue, its refill on the async lane and the biome search
 * can ask from any thread. A world loaded since the last read has no area until the next one.
 *
 * <h2>Concurrency</h2>
 * Ownership: <b>atomic-reference</b>. {@code settings} is seeded non-null at construction and only ever replaced
 * with a non-null value, read lock-free on every serve/refill/search and replaced whole by {@code /settpr}.
 * {@code borders} is a concurrent map written only by the global refresh.
 */
@NullMarked
public final class BukkitRtpAreaSource implements RtpAreaSource {

    /** How stale a border may be; a served location is still checked against the border read here. */
    static final Duration BORDER_REFRESH = Duration.ofSeconds(1);

    /** One world's border, as read on the global thread. */
    private record Border(double centerX, double centerZ, double size) {}

    private final Server server;
    private final AtomicReference<RtpWorldSettings> settings;
    private final Map<UUID, Border> borders = new ConcurrentHashMap<>();
    private final AtomicReference<@Nullable AutoCloseable> refreshing = new AtomicReference<>();

    public BukkitRtpAreaSource(Server server, RtpWorldSettings settings) {
        this.server = Objects.requireNonNull(server, "server");
        this.settings = new AtomicReference<>(Objects.requireNonNull(settings, "settings"));
    }

    /** The live per-world tuning (radii, queue sizing, and the search budget) read lock-free. */
    public RtpWorldSettings current() {
        // Seeded non-null and only ever replaced with a non-null value; requireNonNull makes that explicit.
        return Objects.requireNonNull(settings.get(), "settings");
    }

    /**
     * Swap the live search radii (the {@code /settpr} runtime path), keeping the queue sizing and search budget
     * untouched, and return the new tuning. A single {@link AtomicReference#set} so an in-flight read is coherent.
     */
    public RtpWorldSettings updateRadii(double minRadius, double maxRadius) {
        RtpWorldSettings updated = current().withRadii(minRadius, maxRadius);
        settings.set(updated);
        return updated;
    }

    /** Start reading every world's border on the global thread; call once the module runs. */
    public void startBorderRefresh(Scheduler scheduler) {
        Objects.requireNonNull(scheduler, "scheduler");
        closeQuietly(refreshing.getAndSet(scheduler.repeatGlobal(this::refreshBorders, Duration.ZERO, BORDER_REFRESH)));
    }

    /** Stop the border reads; call on module stop. */
    public void stopBorderRefresh() {
        closeQuietly(refreshing.getAndSet(null));
    }

    /** On the global thread: read every loaded world's border and forget the worlds that are gone. */
    void refreshBorders() {
        Map<UUID, Border> seen = new HashMap<>();
        for (World world : server.getWorlds()) {
            WorldBorder border = world.getWorldBorder();
            seen.put(
                    world.getUID(),
                    new Border(border.getCenter().getX(), border.getCenter().getZ(), border.getSize()));
        }
        borders.putAll(seen);
        borders.keySet().retainAll(seen.keySet());
    }

    private static void closeQuietly(@Nullable AutoCloseable handle) {
        if (handle == null) {
            return;
        }
        try {
            handle.close();
        } catch (Exception uncancellable) {
            // A repeating task that cannot be cancelled ends with the plugin anyway.
        }
    }

    @Override
    public Optional<SafeSearchArea> areaFor(WorldRef worldRef) {
        Objects.requireNonNull(worldRef, "worldRef");
        Border border = borders.get(worldRef.uid());
        if (border == null) {
            return Optional.empty();
        }
        double borderRadius = border.size() / 2.0;
        RtpWorldSettings current = current();
        double max = Math.min(current.maxRadius(), borderRadius);
        if (max < current.minRadius()) {
            return Optional.empty();
        }
        return Optional.of(new SafeSearchArea(
                worldRef, border.centerX(), border.centerZ(), current.minRadius(), current.maxRadius(), borderRadius));
    }
}
