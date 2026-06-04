package com.uxplima.uxmessentials.holograms.adapter.outbound;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import net.kyori.adventure.text.minimessage.MiniMessage;

import com.uxplima.uxmessentials.holograms.application.port.HologramView;
import com.uxplima.uxmessentials.holograms.domain.Hologram;
import com.uxplima.uxmessentials.holograms.domain.HologramLine;
import com.uxplima.uxmessentials.holograms.domain.HologramName;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmlib.hologram.HologramManager;
import com.uxplima.uxmlib.hologram.Holograms;
import org.jspecify.annotations.NullMarked;

/**
 * The outbound seam that keeps the in-world rendering in step with the stored model, realised over the uxmLib
 * native-Display hologram API. Each domain {@link Hologram} maps to one live uxmLib {@code Hologram} (a single
 * multi-line {@code TextDisplay}); the renderer tracks them by name so a re-render or a despawn finds the live
 * entity.
 *
 * <p>Spawning and despawning a display entity must run on the owning region thread (Folia), so every mutation
 * hops through the injected {@link Scheduler} port: {@code render} schedules onto the hologram's location, and
 * {@code despawn} reuses the {@link Position} the entity was spawned at (tracked alongside the live entity)
 * rather than reading the live entity's location off-thread. A render replaces any existing live entity for
 * the same name (remove-then-spawn), so a line edit, a line count change, and a move all converge to "the
 * world matches the model"; the old entity is always removed on its own region thread, which matters on a
 * cross-world move where it lives in a different world from the new one. A world that is not loaded is
 * skipped with a warning rather than throwing.
 */
@NullMarked
public final class HologramRenderer implements HologramView {

    private final HologramManager manager;
    private final Scheduler scheduler;
    private final Logger log;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final Map<String, Tracked> live = new ConcurrentHashMap<>();

    public HologramRenderer(HologramManager manager, Scheduler scheduler, Logger log) {
        this.manager = Objects.requireNonNull(manager, "manager");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public void render(Hologram hologram) {
        Objects.requireNonNull(hologram, "hologram");
        World world = Bukkit.getWorld(hologram.location().world().uid());
        if (world == null) {
            log.warn(
                    "skipping hologram {} — world {} is not loaded",
                    hologram.name().value(),
                    hologram.location().world().name());
            return;
        }
        Location at = BukkitRefs.toLocation(world, hologram.location());
        scheduler.onRegion(hologram.location(), () -> spawnReplacing(hologram, at));
    }

    /** Despawn every tracked hologram now — call on module stop so no display entity is orphaned. */
    public void despawnAll() {
        for (Tracked tracked : live.values()) {
            // Each entity is removed on its own region thread, derived from the position it was spawned at.
            scheduler.onRegion(tracked.position(), () -> manager.remove(tracked.hologram()));
        }
        live.clear();
    }

    @Override
    public void despawn(HologramName name) {
        Objects.requireNonNull(name, "name");
        Tracked existing = live.remove(name.value());
        if (existing == null) {
            return;
        }
        // The display entity must be removed on its own region thread; route through its spawn position.
        scheduler.onRegion(existing.position(), () -> manager.remove(existing.hologram()));
    }

    private void spawnReplacing(Hologram hologram, Location at) {
        Tracked previous = live.remove(hologram.name().value());
        if (previous != null) {
            // Despawn the old entity on its own region thread; on a cross-world move it lives in a
            // different world from the new one, so it must not be removed inline on this region thread.
            scheduler.onRegion(previous.position(), () -> manager.remove(previous.hologram()));
        }
        Holograms.Builder builder = Holograms.builder();
        for (HologramLine line : hologram.lines()) {
            builder.line(miniMessage.deserialize(line.value()));
        }
        live.put(hologram.name().value(), new Tracked(manager.spawn(builder, at), hologram.location()));
    }

    /** A live uxmLib hologram paired with the {@link Position} it was spawned at, so its owning region is known. */
    private record Tracked(com.uxplima.uxmlib.hologram.Hologram hologram, Position position) {}
}
