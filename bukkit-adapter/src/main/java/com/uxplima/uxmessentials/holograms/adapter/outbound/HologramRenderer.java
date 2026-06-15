package com.uxplima.uxmessentials.holograms.adapter.outbound;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import net.kyori.adventure.text.minimessage.MiniMessage;

import com.uxplima.uxmessentials.holograms.application.port.HologramView;
import com.uxplima.uxmessentials.holograms.domain.Hologram;
import com.uxplima.uxmessentials.holograms.domain.HologramLine;
import com.uxplima.uxmessentials.holograms.domain.HologramName;
import com.uxplima.uxmessentials.holograms.domain.Visibility;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmlib.hologram.HologramManager;
import com.uxplima.uxmlib.hologram.Holograms;
import org.jspecify.annotations.NullMarked;

/**
 * The outbound seam that keeps the in-world rendering in step with the stored model, realised over the uxmLib
 * native-Display hologram API. Each domain {@link Hologram} maps to one live uxmLib {@code Hologram} (a single
 * multi-line {@code TextDisplay}); the renderer tracks them by name so a re-render, a refresh, or a despawn
 * finds the live entity.
 *
 * <p>Spawning and despawning a display entity must run on the owning region thread (Folia), so every mutation
 * hops through the injected {@link Scheduler} port: {@code render} schedules onto the hologram's location, and
 * {@code despawn} reuses the {@link Position} the entity was spawned at (tracked alongside the live entity)
 * rather than reading the live entity's location off-thread. A render replaces any existing live entity for
 * the same name (remove-then-spawn), so a line edit, a line-count change, a restyle, and a move all converge
 * to "the world matches the model"; the old entity is always removed on its own region thread, which matters
 * on a cross-world move where it lives in a different world from the new one. A world that is not loaded is
 * skipped with a warning rather than throwing.
 *
 * <p>Each line's MiniMessage source is run through the injected {@code placeholders} transform before it is
 * deserialised, so an operator may embed server-global {@code %papi%} tokens (online count, time, TPS). The
 * hologram is a single shared entity (not one per viewer), so the transform resolves server-relative
 * placeholders; a hologram with a positive refresh interval is re-rendered by the refresh task on its cadence,
 * picking up fresh values. A static hologram with no placeholder renders once and never again.
 *
 * <p>A hologram's {@link Visibility} is applied at the spawn boundary. {@link Visibility.Mode#ALL} is the cheap
 * default — the shared entity is visible by default to everyone. {@link Visibility.Mode#PERMISSION} restricts
 * the entity to an allowed-viewer set (Paper's native {@code show/hideEntity}) recomputed from the online
 * permission-holders on every render and refresh; {@link #recomputeVisibilityFor(Player)} re-evaluates a single
 * joiner so they pick up the permission-gated holograms they qualify for without waiting for a refresh tick. A
 * finite {@link Visibility#distance()} maps onto the native display view range — blocks divided by the vanilla
 * {@value #VANILLA_VIEW_BLOCKS}-block tracking range, since the lib view range is a multiplier — so the hologram
 * culls beyond that radius; distance 0 leaves the appearance's own view-range multiplier untouched.
 */
@NullMarked
public final class HologramRenderer implements HologramView {

    /** The vanilla display entity tracking range in blocks at view-range multiplier 1.0. */
    static final float VANILLA_VIEW_BLOCKS = 64.0f;

    private final Plugin plugin;
    private final HologramManager manager;
    private final Scheduler scheduler;
    private final Logger log;
    private final Permissions permissions;
    private final UnaryOperator<String> placeholders;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final Map<String, Tracked> live = new ConcurrentHashMap<>();

    public HologramRenderer(
            Plugin plugin,
            HologramManager manager,
            Scheduler scheduler,
            Logger log,
            Permissions permissions,
            UnaryOperator<String> placeholders) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.manager = Objects.requireNonNull(manager, "manager");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.log = Objects.requireNonNull(log, "log");
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.placeholders = Objects.requireNonNull(placeholders, "placeholders");
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
            scheduler.onRegion(tracked.position(), () -> manager.remove(tracked.live()));
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
        scheduler.onRegion(existing.position(), () -> manager.remove(existing.live()));
    }

    /**
     * Re-render the lines of a currently-tracked hologram in place, picking up fresh placeholder values. A
     * full remove-then-spawn (rather than {@code setText}) keeps the path identical to {@link #render} and
     * works even when a re-render coincides with a region hop; a hologram no longer tracked is a no-op.
     */
    public void refresh(Hologram hologram) {
        Objects.requireNonNull(hologram, "hologram");
        if (!live.containsKey(hologram.name().value())) {
            return;
        }
        render(hologram);
    }

    /**
     * Re-evaluate every permission-gated hologram for a single {@code joiner} on their region thread, showing it
     * to them when they hold its node and hiding it otherwise. Called from the join listener so a player who logs
     * in sees the holograms they qualify for at once rather than after the next refresh tick. {@code ALL}
     * holograms are visible by default and need no per-viewer call, so the loop touches only the gated ones.
     */
    public void recomputeVisibilityFor(Player joiner) {
        Objects.requireNonNull(joiner, "joiner");
        for (Tracked tracked : live.values()) {
            Visibility visibility = tracked.hologram().visibility();
            if (visibility.isPermissionGated()) {
                scheduler.onRegion(tracked.position(), () -> applyViewer(tracked.live(), visibility, joiner));
            }
        }
    }

    private void spawnReplacing(Hologram hologram, Location at) {
        Tracked previous = live.remove(hologram.name().value());
        if (previous != null) {
            // Despawn the old entity on its own region thread; on a cross-world move it lives in a
            // different world from the new one, so it must not be removed inline on this region thread.
            scheduler.onRegion(previous.position(), () -> manager.remove(previous.live()));
        }
        Holograms.Builder builder = builderFor(hologram, placeholders, miniMessage);
        com.uxplima.uxmlib.hologram.Hologram spawned = manager.spawn(builder, at);
        applyVisibility(spawned, hologram.visibility());
        live.put(hologram.name().value(), new Tracked(spawned, hologram, hologram.location()));
    }

    /**
     * Apply {@code visibility} to a freshly spawned entity on its own region thread. {@code ALL} keeps the
     * entity visible by default (no work). {@code PERMISSION} restricts it to an explicit viewer set, then shows
     * it to each online player who holds the node — so a non-holder never sees it and a holder sees it at once.
     */
    private void applyVisibility(com.uxplima.uxmlib.hologram.Hologram spawned, Visibility visibility) {
        if (!visibility.isPermissionGated()) {
            return;
        }
        spawned.restrictToViewers();
        for (Player online : Bukkit.getOnlinePlayers()) {
            applyViewer(spawned, visibility, online);
        }
    }

    private void applyViewer(com.uxplima.uxmlib.hologram.Hologram spawned, Visibility visibility, Player viewer) {
        if (maySee(permissions, visibility, BukkitRefs.toRef(viewer))) {
            spawned.show(plugin, viewer);
        } else {
            spawned.hide(plugin, viewer);
        }
    }

    /**
     * Whether {@code who} may see a hologram with this {@code visibility}: everyone for {@code ALL}, and only a
     * holder of the gating node for {@code PERMISSION}. Pure (the {@code Permissions} call aside), so the
     * permission-gated viewer set is unit-testable with a fake {@code Permissions}.
     */
    static boolean maySee(Permissions permissions, Visibility visibility, PlayerRef who) {
        if (!visibility.isPermissionGated()) {
            return true;
        }
        String node = visibility.permission();
        return node != null && permissions.has(who, node);
    }

    /**
     * Build the configured uxmLib builder for {@code hologram}: each line's MiniMessage source run through
     * {@code placeholders} before it is deserialised, the appearance applied, and a finite visibility distance
     * mapped onto the native view range. Pure (no world, no entity), so the placeholder resolution, appearance
     * mapping and distance-to-view-range mapping are unit-testable through {@code builder.spec()}.
     */
    static Holograms.Builder builderFor(
            Hologram hologram, UnaryOperator<String> placeholders, MiniMessage miniMessage) {
        Holograms.Builder builder = Holograms.builder();
        for (HologramLine line : hologram.lines()) {
            builder.line(miniMessage.deserialize(placeholders.apply(line.value())));
        }
        HologramAppearances.apply(builder, hologram.appearance());
        Visibility visibility = hologram.visibility();
        if (visibility.hasDistanceLimit()) {
            // The lib view range is a multiplier on the vanilla tracking range, so a block radius maps to
            // blocks / 64; this overrides the appearance's own view-range multiplier when a distance is set.
            builder.viewRange(visibility.distance() / VANILLA_VIEW_BLOCKS);
        }
        return builder;
    }

    /**
     * A live uxmLib hologram paired with the domain {@link Hologram} it renders (so its {@link Visibility} is
     * known for a viewer recompute) and the {@link Position} it was spawned at (so its owning region is known).
     */
    private record Tracked(com.uxplima.uxmlib.hologram.Hologram live, Hologram hologram, Position position) {}
}
