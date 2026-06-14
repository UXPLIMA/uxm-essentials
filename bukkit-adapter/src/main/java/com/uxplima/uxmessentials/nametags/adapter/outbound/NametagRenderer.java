package com.uxplima.uxmessentials.nametags.adapter.outbound;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;

import com.uxplima.uxmessentials.nametags.application.port.NametagVanish;
import com.uxplima.uxmessentials.nametags.domain.NametagAppearance;
import com.uxplima.uxmessentials.nametags.domain.NametagConfig;
import com.uxplima.uxmessentials.nametags.domain.NametagFormat;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.adapter.outbound.hud.AnimationRegistry;
import com.uxplima.uxmessentials.shared.adapter.outbound.hud.HudText;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.PlaceholderApiSupport;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.display.ConditionContext;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmlib.hologram.Hologram;
import com.uxplima.uxmlib.hologram.HologramManager;
import com.uxplima.uxmlib.hologram.Holograms;
import com.uxplima.uxmlib.hologram.follow.HologramFollow;
import com.uxplima.uxmlib.hologram.follow.SelfHiddenNameplate;
import com.uxplima.uxmlib.scheduler.TaskHandle;
import org.jspecify.annotations.NullMarked;

/**
 * Renders the per-wearer above-head nametag from the live {@link NametagConfig} over uxmLib's native-display hologram
 * stack: one viewer-restricted {@code TextDisplay} per wearer, wrapped in a {@link SelfHiddenNameplate} (shown to
 * nearby players, never to the wearer) and kept above the head by a {@link HologramFollow} task. Each wearer is given
 * the format {@link NametagConfig#select selected} for them — the highest-priority format whose condition matches,
 * evaluated against a {@link ConditionContext} built from the live player.
 *
 * <h2>Folia region-thread discipline</h2>
 *
 * Every display-entity mutation runs on the wearer's region thread. {@link #spawn} and {@link #update} are called by
 * the caller (the render task and the lifecycle listener) already hopped onto the wearer's entity thread; the
 * follow task and {@code refreshViewers} fan out from there. {@link #despawn} hops onto the {@link Position} the
 * entity was spawned at (tracked alongside the live entity) rather than reading the live entity off-thread — which
 * matters when the despawn is driven from a quit handler that may not own the entity's region.
 */
@NullMarked
public final class NametagRenderer {

    private final Plugin plugin;
    private final Supplier<NametagConfig> config;
    private final HologramManager manager;
    private final HologramFollow follow;
    private final Scheduler scheduler;
    private final AnimationRegistry animations;
    private final NametagVanish vanish;
    private final java.time.Duration followPeriod;
    private final Map<UUID, Tracked> live = new ConcurrentHashMap<>();

    public NametagRenderer(
            Plugin plugin,
            Supplier<NametagConfig> config,
            HologramManager manager,
            HologramFollow follow,
            Scheduler scheduler,
            AnimationRegistry animations,
            NametagVanish vanish,
            java.time.Duration followPeriod) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.config = Objects.requireNonNull(config, "config");
        this.manager = Objects.requireNonNull(manager, "manager");
        this.follow = Objects.requireNonNull(follow, "follow");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.animations = Objects.requireNonNull(animations, "animations");
        this.vanish = Objects.requireNonNull(vanish, "vanish");
        this.followPeriod = Objects.requireNonNull(followPeriod, "followPeriod");
    }

    /** Spawn {@code wearer}'s nametag from the selected format. Must run on the wearer's region thread. */
    public void spawn(Player wearer, long tick) {
        Objects.requireNonNull(wearer, "wearer");
        Optional<NametagFormat> selected = selectFor(wearer);
        if (selected.isEmpty()) {
            despawn(wearer.getUniqueId());
            return;
        }
        NametagFormat format = selected.get();
        NametagAppearance appearance = format.appearance();
        Location wearerLocation = wearer.getLocation();
        if (wearerLocation == null) {
            // Paper marks getLocation() nullable (only for a dead/removed entity); a wearer mid-removal carries no
            // nametag this tick — the next tick's spawn re-attempts once they are live again.
            return;
        }
        // Capture the wearer's own region position before offsetting (Location#add mutates in place); the spawn
        // position is what despawn hops onto, and the entity spawns at the offset location above the head.
        Position spawnPos = BukkitRefs.toPosition(wearerLocation);
        Location at = wearerLocation.add(0, appearance.yOffset(), 0);
        Hologram hologram = manager.spawn(builder(format, wearer, tick), at);
        SelfHiddenNameplate nameplate = new SelfHiddenNameplate(hologram, wearer);
        TaskHandle followHandle = follow.follow(hologram, wearer, new Vector(0, appearance.yOffset(), 0), followPeriod);
        live.put(wearer.getUniqueId(), new Tracked(hologram, nameplate, followHandle, spawnPos, format));
        nameplate.refreshViewers(plugin, eligibleViewers(wearer, format));
    }

    /** Reconcile {@code wearer}'s nametag with the selected format. Must run on the wearer's region thread. */
    public void update(Player wearer, long tick) {
        Objects.requireNonNull(wearer, "wearer");
        Tracked tracked = live.get(wearer.getUniqueId());
        if (tracked == null) {
            spawn(wearer, tick);
            return;
        }
        Optional<NametagFormat> selected = selectFor(wearer);
        if (selected.isEmpty()) {
            despawn(wearer.getUniqueId());
            return;
        }
        NametagFormat format = selected.get();
        // A different format may carry a different appearance; the appearance is set once at spawn (not re-applied
        // live), so a format switch is a remove-then-spawn rather than an in-place edit.
        if (!format.name().equals(tracked.format().name())
                || !format.appearance().equals(tracked.format().appearance())) {
            despawn(wearer.getUniqueId());
            spawn(wearer, tick);
            return;
        }
        tracked.hologram().setText(renderLines(format, wearer, tick));
        tracked.nameplate().refreshViewers(plugin, eligibleViewers(wearer, format));
    }

    /** Despawn {@code uuid}'s nametag if it has one. Hops onto the entity's own region thread to remove it. */
    public void despawn(UUID uuid) {
        Objects.requireNonNull(uuid, "uuid");
        Tracked tracked = live.remove(uuid);
        if (tracked == null) {
            return;
        }
        tracked.followHandle().cancel();
        scheduler.onRegion(tracked.spawnPos(), () -> manager.remove(tracked.hologram()));
    }

    /** Despawn every tracked nametag now — call on module stop so no display entity is orphaned. */
    public void despawnAll() {
        for (UUID uuid : List.copyOf(live.keySet())) {
            despawn(uuid);
        }
    }

    /** Whether {@code uuid} currently has a tracked nametag (test/observability seam). */
    public boolean isTracked(UUID uuid) {
        return live.containsKey(uuid);
    }

    /**
     * The format this wearer should get, or empty when none applies. Package-private so the selection rule can be
     * tested directly: MockBukkit cannot spawn a real {@code TextDisplay} (its display setters are unimplemented), so
     * the spawn path is not unit-testable, but the pure selection it depends on is.
     */
    Optional<NametagFormat> selectFor(Player wearer) {
        Optional<NametagFormat> selected = config.get().select(conditionContext(wearer));
        if (selected.isEmpty()) {
            return Optional.empty();
        }
        NametagFormat format = selected.get();
        // A format with no lines, or whose show-when gate fails for this wearer, means "no nametag" for them.
        if (format.lines().isEmpty() || !format.visibility().showWhen().matches(conditionContext(wearer))) {
            return Optional.empty();
        }
        return selected;
    }

    private Holograms.Builder builder(NametagFormat format, Player wearer, long tick) {
        Holograms.Builder builder = Holograms.builder().line(renderLines(format, wearer, tick));
        return NametagAppearanceMapper.applyTo(builder, format.appearance());
    }

    private Component renderLines(NametagFormat format, Player wearer, long tick) {
        List<Component> rendered = new ArrayList<>(format.lines().size());
        for (String line : format.lines()) {
            String withName = line.replace("{player}", wearer.getName());
            rendered.add(HudText.render(wearer.getUniqueId(), animations.resolve(withName, tick)));
        }
        return Component.join(JoinConfiguration.newlines(), rendered);
    }

    // The eligible viewers for a wearer's nametag: online players other than the wearer, within the view range if one
    // is set, that the vanish gate lets see the wearer — and, when the format hides on sneak and the wearer is
    // sneaking, nobody (the nameplate then hides from all). SelfHiddenNameplate also drops the wearer, but excluding
    // them here keeps the candidate set tight. Package-private so the cull is testable without spawning a display.
    List<Player> eligibleViewers(Player wearer, NametagFormat format) {
        if (format.visibility().hideWhileSneaking() && wearer.isSneaking()) {
            return List.of();
        }
        OptionalDouble viewRange = format.appearance().viewRange();
        double maxSq = viewRange.isPresent() ? squareBlocks(viewRange.getAsDouble()) : Double.MAX_VALUE;
        List<Player> eligible = new ArrayList<>();
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.getUniqueId().equals(wearer.getUniqueId()) || !canSee(viewer, wearer)) {
                continue;
            }
            if (maxSq != Double.MAX_VALUE && !withinRange(viewer, wearer, maxSq)) {
                continue;
            }
            eligible.add(viewer);
        }
        return eligible;
    }

    private boolean canSee(Player viewer, Player wearer) {
        return vanish.canSee(BukkitRefs.toRef(viewer), BukkitRefs.toRef(wearer));
    }

    private static boolean withinRange(Player viewer, Player wearer, double maxSq) {
        if (!viewer.getWorld().equals(wearer.getWorld())) {
            return false;
        }
        Location viewerLocation = viewer.getLocation();
        Location wearerLocation = wearer.getLocation();
        if (viewerLocation == null || wearerLocation == null) {
            return false;
        }
        return viewerLocation.distanceSquared(wearerLocation) <= maxSq;
    }

    // The view-range field is a render-distance multiplier (the same units the appearance carries); treat it as a
    // block radius for the eligible-viewer cull so a far-away viewer is dropped from the per-tick refresh.
    private static double squareBlocks(double range) {
        double blocks = range <= 0 ? 0 : range;
        return blocks * blocks;
    }

    private ConditionContext conditionContext(Player player) {
        return new ConditionContext(
                player::hasPermission,
                player.getWorld().getName(),
                player.getGameMode().name(),
                PlaceholderApiSupport.messageBridge(player.getUniqueId()));
    }

    /**
     * A live nametag: the uxmLib hologram, the self-hidden nameplate driving its per-viewer visibility, the follow
     * task handle (cancelled on despawn), the {@link Position} it was spawned at (so its region is known off-thread),
     * and the selected {@link NametagFormat} (so a format/appearance change is detected and triggers a respawn).
     */
    private record Tracked(
            Hologram hologram,
            SelfHiddenNameplate nameplate,
            TaskHandle followHandle,
            Position spawnPos,
            NametagFormat format) {}
}
