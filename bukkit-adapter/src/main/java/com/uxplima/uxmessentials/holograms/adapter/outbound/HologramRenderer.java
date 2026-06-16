package com.uxplima.uxmessentials.holograms.adapter.outbound;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
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
 * native-Display hologram API. Each domain {@link Hologram} maps to one live uxmLib hologram by its type: a
 * {@code TEXT} hologram to a single multi-line {@code TextDisplay}, an {@code ITEM} hologram to an
 * {@code ItemDisplay} (its {@code Material} name resolved here), a {@code BLOCK} hologram to a
 * {@code BlockDisplay} (its BlockData string parsed here). The renderer tracks them by name so a re-render, a
 * refresh, or a despawn finds the live entity; an unknown material or unparseable BlockData is failed soft
 * (logged and skipped) so it never crashes the render.
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
 * hologram is a single shared entity, so that transform resolves server-relative placeholders for the broadcast
 * base text every viewer sees by default; a hologram with a positive refresh interval is re-rendered by the
 * refresh task on its cadence, picking up fresh values. A static hologram with no placeholder renders once and
 * never again.
 *
 * <p>On top of that shared base, a text hologram whose lines embed a {@code %...%} token additionally renders
 * <em>per viewer</em>: after the native spawn, each eligible viewer is sent a text-override metadata packet (via
 * the {@link HologramTextOverrides} collaborator over the lib {@code DisplayTextPackets} port) carrying their own
 * resolved placeholder values, so each viewer sees their own text over the one shared {@code TextDisplay} — no
 * per-viewer entity. Overrides are sent on spawn, on join (so a joiner sees their values at once), and on each
 * refresh re-render (a remove-then-spawn re-sends them, keeping a refreshing hologram's per-viewer values
 * fresh). When PlaceholderAPI is absent the per-viewer bridge is the identity, so per-viewer text equals the
 * global text — the path is harmless. A static, no-placeholder, or item/block hologram is never per-viewer and
 * pays nothing.
 *
 * <p>A hologram's {@link Visibility} is applied at the spawn boundary. {@link Visibility.Mode#ALL} is the cheap
 * default — the shared entity is visible by default to everyone. {@link Visibility.Mode#PERMISSION} restricts
 * the entity to an allowed-viewer set (Paper's native {@code show/hideEntity}) recomputed from the online
 * permission-holders on every render and refresh. {@link Visibility.Mode#MANUAL} hides the entity from everyone
 * and restricts it to its persisted shown-viewer set, queried per hologram through the injected
 * {@code manualViewers} lookup; {@link #applyManualViewer(HologramName, java.util.UUID, boolean)} shows or hides
 * one online viewer the instant {@code /hologram show|hide} runs. {@link #recomputeVisibilityFor(Player)}
 * re-evaluates a single joiner so they pick up the permission-gated and manual holograms they qualify for
 * without waiting for a refresh tick. A finite {@link Visibility#distance()} maps onto the native display view
 * range — blocks divided by the vanilla {@value HologramSpawns#VANILLA_VIEW_BLOCKS}-block tracking range, since the lib view
 * range is a multiplier — so the hologram culls beyond that radius; distance 0 leaves the appearance's own
 * view-range multiplier untouched.
 */
@NullMarked
public final class HologramRenderer implements HologramView {

    private final Plugin plugin;
    private final HologramManager manager;
    private final Scheduler scheduler;
    private final Logger log;
    private final UnaryOperator<String> placeholders;
    private final HologramViewers viewers;
    private final HologramTextOverrides textOverrides;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final Map<String, Tracked> live = new ConcurrentHashMap<>();

    public HologramRenderer(
            Plugin plugin,
            HologramManager manager,
            Scheduler scheduler,
            Logger log,
            UnaryOperator<String> placeholders,
            HologramViewers viewers,
            HologramTextOverrides textOverrides) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.manager = Objects.requireNonNull(manager, "manager");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.log = Objects.requireNonNull(log, "log");
        this.placeholders = Objects.requireNonNull(placeholders, "placeholders");
        this.viewers = Objects.requireNonNull(viewers, "viewers");
        this.textOverrides = Objects.requireNonNull(textOverrides, "textOverrides");
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
            scheduler.onRegion(tracked.position(), () -> tracked.live().removeFrom(manager));
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
        scheduler.onRegion(existing.position(), () -> existing.live().removeFrom(manager));
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
     * Re-evaluate every per-viewer hologram for a single {@code joiner} on their region thread. A permission-gated
     * or manual hologram is shown to the joiner when they qualify and hidden otherwise ({@code ALL} holograms are
     * visible by default and need no visibility call); a hologram whose lines embed a placeholder also sends the
     * joiner their own text override (for an {@code ALL} hologram too) when they may see it. Called from the join
     * listener so a joiner sees the holograms — and their own placeholder values — at once, not after a refresh.
     */
    public void recomputeVisibilityFor(Player joiner) {
        Objects.requireNonNull(joiner, "joiner");
        for (Tracked tracked : live.values()) {
            Hologram hologram = tracked.hologram();
            Visibility visibility = hologram.visibility();
            Set<UUID> shown = viewers.shownViewersFor(hologram);
            boolean gated = visibility.isPermissionGated() || visibility.isManual();
            boolean perViewerText = textOverrides.hasPerViewerText(hologram)
                    && tracked.live().textEntityId() != RenderedHologram.NO_ENTITY;
            if (gated || perViewerText) {
                scheduler.onRegion(
                        tracked.position(), () -> applyJoiner(tracked.live(), hologram, joiner, shown, gated));
            }
        }
    }

    /** Apply a joiner's visibility (when gated) and their per-viewer text override (when they may see it). */
    private void applyJoiner(RenderedHologram live, Hologram hologram, Player joiner, Set<UUID> shown, boolean gated) {
        if (gated) {
            // Showing/hiding the shared entity touches the hologram's own viewer set, so it stays on this
            // (the hologram's) region thread.
            viewers.applyViewer(live, hologram.visibility(), joiner, shown);
        }
        if (viewers.maySee(hologram, joiner, shown)
                && textOverrides.hasPerViewerText(hologram)
                && live.textEntityId() != RenderedHologram.NO_ENTITY) {
            dispatchPerViewerText(scheduler, textOverrides, List.of(joiner), live.textEntityId(), hologram);
        }
    }

    /**
     * Apply a single MANUAL viewer change to the live entity at once: show the hologram under {@code name} to
     * the online {@code viewer} when {@code visible}, hide it otherwise — so {@code /hologram show|hide} takes
     * effect without a refresh tick. A no-op when the hologram is not tracked or the viewer is offline; the
     * change is routed onto the entity's region thread.
     */
    @Override
    public void applyManualViewer(HologramName name, UUID viewer, boolean visible) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(viewer, "viewer");
        Tracked tracked = live.get(name.value());
        Player online = Bukkit.getPlayer(viewer);
        if (tracked == null || online == null) {
            return;
        }
        scheduler.onRegion(tracked.position(), () -> {
            if (visible) {
                tracked.live().show(plugin, online);
            } else {
                tracked.live().hide(plugin, online);
            }
        });
    }

    private void spawnReplacing(Hologram hologram, Location at) {
        Tracked previous = live.remove(hologram.name().value());
        if (previous != null) {
            // Despawn the old entity on its own region thread; on a cross-world move it lives in a
            // different world from the new one, so it must not be removed inline on this region thread.
            scheduler.onRegion(previous.position(), () -> previous.live().removeFrom(manager));
        }
        RenderedHologram spawned = HologramSpawns.spawnFor(manager, log, hologram, at, placeholders, miniMessage);
        if (spawned == null) {
            // Invalid item material or block data — already logged; leave nothing tracked rather than crash.
            return;
        }
        viewers.applyOnSpawn(spawned, hologram);
        live.put(hologram.name().value(), new Tracked(spawned, hologram, hologram.location()));
        sendPerViewerText(spawned, hologram);
    }

    /**
     * Send each eligible viewer their per-viewer text override over the just-spawned entity (no-op if not PAPI).
     * The eligibility scan runs here on the hologram's region thread (as the visibility scan does), but each
     * viewer's resolve is hopped onto that viewer's own entity thread: resolving a player-relative placeholder
     * reads the viewer's live state, which is not safe to read off the hologram's region thread under Folia.
     */
    private void sendPerViewerText(RenderedHologram spawned, Hologram hologram) {
        if (!textOverrides.hasPerViewerText(hologram) || spawned.textEntityId() == RenderedHologram.NO_ENTITY) {
            return;
        }
        dispatchPerViewerText(scheduler, textOverrides, viewers.eligible(hologram), spawned.textEntityId(), hologram);
    }

    /**
     * Hop each viewer's per-viewer text resolve onto <em>that viewer's</em> entity thread before resolving and
     * sending the override. Resolving a player-relative {@code %papi%} token reads the viewer's live entity
     * state, which under Folia is only safe to touch from the entity's owning thread — never the hologram's
     * region thread the spawn/refresh runs on, where a viewer may sit in a different region or world (the same
     * rule the scoreboard and tablist render loops follow). Pure of any spawn or live-entity read, so it is
     * unit-testable with a recording scheduler and fake viewers.
     */
    static void dispatchPerViewerText(
            Scheduler scheduler,
            HologramTextOverrides textOverrides,
            List<? extends Player> eligible,
            int entityId,
            Hologram hologram) {
        for (Player viewer : eligible) {
            scheduler.onEntity(BukkitRefs.toRef(viewer), () -> textOverrides.sendOverride(viewer, entityId, hologram));
        }
    }

    /**
     * Whether {@code who} may see a hologram with this {@code visibility}: everyone for {@code ALL}, only a
     * holder of the gating node for {@code PERMISSION}, and only a member of {@code shownViewers} for
     * {@code MANUAL}. Pure (the {@code Permissions} call aside), so the gated and manual viewer sets are
     * unit-testable with a fake {@code Permissions} and an explicit shown set; the visibility collaborator and
     * tests reach it here.
     */
    static boolean maySee(Permissions permissions, Visibility visibility, PlayerRef who, Set<UUID> shownViewers) {
        if (visibility.isManual()) {
            return shownViewers.contains(who.uuid());
        }
        if (!visibility.isPermissionGated()) {
            return true;
        }
        String node = visibility.permission();
        return node != null && permissions.has(who, node);
    }

    /** The pure model-to-builder mapping, kept reachable here so the builder mapping stays unit-testable. */
    static Holograms.Builder builderFor(
            Hologram hologram, UnaryOperator<String> placeholders, MiniMessage miniMessage) {
        return HologramSpawns.builderFor(hologram, placeholders, miniMessage);
    }

    /**
     * A live uxmLib hologram paired with the domain {@link Hologram} it renders (so its {@link Visibility} is
     * known for a viewer recompute) and the {@link Position} it was spawned at (so its owning region is known).
     * The live entity is held as a type-agnostic {@link RenderedHologram} so a text, item or block hologram is
     * tracked, despawned and re-shown the same way.
     */
    private record Tracked(RenderedHologram live, Hologram hologram, Position position) {}
}
