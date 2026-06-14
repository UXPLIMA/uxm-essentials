package com.uxplima.uxmessentials.nametags.adapter.outbound;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.nametags.application.port.NametagVanish;
import com.uxplima.uxmessentials.nametags.domain.NametagConfig;
import com.uxplima.uxmessentials.nametags.domain.NametagFormat;
import com.uxplima.uxmessentials.nametags.domain.NametagVisibility;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.adapter.outbound.hud.AnimationRegistry;
import com.uxplima.uxmessentials.shared.adapter.outbound.hud.HudText;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.PlaceholderApiSupport;
import com.uxplima.uxmessentials.shared.display.ConditionContext;
import com.uxplima.uxmlib.nametag.Appearance;
import com.uxplima.uxmlib.nametag.NametagHandle;
import com.uxplima.uxmlib.nametag.NametagRenderer;
import com.uxplima.uxmlib.nametag.PerViewerText;
import org.jspecify.annotations.NullMarked;

/**
 * Drives the per-wearer above-head nametag from the live {@link NametagConfig} over uxmLib's packet
 * {@link NametagRenderer}: true per-viewer text, multi-line stacks, line-of-sight fading, and a per-target refresh loop
 * the <em>lib</em> owns. Each wearer is given the format {@link NametagConfig#select selected} for them — the
 * highest-priority format whose condition matches, evaluated against a {@link ConditionContext} built from the live
 * player.
 *
 * <h2>Who owns which loop</h2>
 *
 * The lib's {@code show(...)} starts a region-thread refresh task per wearer that re-asks the viewer supplier for the
 * live audience, diffs it (spawn newcomers, drop departed, refresh the rest), re-resolves the per-viewer text, and
 * re-applies line-of-sight fading — so this class runs <em>no</em> render loop of its own. What this class still owns is
 * <em>format selection</em>: a permission/world/gamemode/sneak/show-when change is not something the lib loop sees, so
 * {@link #update} re-selects the wearer's format each reconcile tick and removes-then-re-shows when the selected format
 * (or its appearance) changed. A wearer who matches no format has their handle removed; the eligible-viewer cull
 * (range, vanish, sneak) lives in the viewer supplier and is re-read by the lib loop every refresh.
 *
 * <h2>Folia region-thread discipline</h2>
 *
 * {@link #show} and {@link #update} are called by the caller (the reconcile task and the lifecycle listener) already
 * hopped onto the wearer's entity thread; the lib's refresh task is itself an {@code entityTimer} on the wearer, so its
 * viewer-supplier and text callbacks run on the wearer's region thread too. {@link #remove} only drops the handle and
 * sends remove packets, which is channel I/O safe from any thread.
 */
@NullMarked
public final class PacketNametagPresenter {

    // The cull radius used when a format authors no viewer-distance: nametags past this many blocks are dropped from
    // the per-tick refresh. Chosen above the usual entity-tracking range so a visible player keeps their nametag.
    private static final double DEFAULT_VIEWER_DISTANCE_BLOCKS = 48.0;

    private final Supplier<NametagConfig> config;
    private final NametagRenderer libRenderer;
    private final AnimationRegistry animations;
    private final NametagVanish vanish;
    private final Map<UUID, Tracked> live = new ConcurrentHashMap<>();

    public PacketNametagPresenter(
            Supplier<NametagConfig> config,
            NametagRenderer libRenderer,
            AnimationRegistry animations,
            NametagVanish vanish) {
        this.config = Objects.requireNonNull(config, "config");
        this.libRenderer = Objects.requireNonNull(libRenderer, "libRenderer");
        this.animations = Objects.requireNonNull(animations, "animations");
        this.vanish = Objects.requireNonNull(vanish, "vanish");
    }

    /** Show {@code wearer}'s nametag from the selected format. Must run on the wearer's region thread. */
    public void show(Player wearer) {
        Objects.requireNonNull(wearer, "wearer");
        // A wearer already shown (the reconcile tick showed for them, then a queued onJoin show arrives) must not be
        // shown a second time — that would orphan the first handle and leak its refresh task. Reconcile instead.
        if (live.containsKey(wearer.getUniqueId())) {
            update(wearer);
            return;
        }
        Optional<NametagFormat> selected = selectFor(wearer);
        if (selected.isEmpty()) {
            return;
        }
        NametagFormat format = selected.get();
        Appearance appearance = NametagAppearanceMapper.toAppearance(format.appearance());
        NametagHandle handle =
                libRenderer.show(wearer, appearance, viewerSupplier(wearer, format), perViewerText(format, wearer));
        live.put(wearer.getUniqueId(), new Tracked(handle, format));
    }

    /** Reconcile {@code wearer}'s nametag with the selected format. Must run on the wearer's region thread. */
    public void update(Player wearer) {
        Objects.requireNonNull(wearer, "wearer");
        Tracked tracked = live.get(wearer.getUniqueId());
        if (tracked == null) {
            show(wearer);
            return;
        }
        Optional<NametagFormat> selected = selectFor(wearer);
        if (selected.isEmpty()) {
            remove(wearer.getUniqueId());
            return;
        }
        NametagFormat format = selected.get();
        // A format switch (different format, or the same format with a changed appearance) is a remove-then-show: the
        // appearance is baked into the spawn, and the lib loop keeps text/viewers fresh on the unchanged format, so no
        // in-place format change is needed here.
        if (!format.name().equals(tracked.format().name())
                || !format.appearance().equals(tracked.format().appearance())) {
            remove(wearer.getUniqueId());
            show(wearer);
        }
    }

    /** Remove {@code uuid}'s nametag if it has one, sending the lib's remove packets to every viewer. */
    public void remove(UUID uuid) {
        Objects.requireNonNull(uuid, "uuid");
        Tracked tracked = live.remove(uuid);
        if (tracked != null) {
            tracked.handle().remove();
        }
    }

    /** Remove every tracked nametag now — call on module stop so no nametag leaks. */
    public void removeAll() {
        for (UUID uuid : List.copyOf(live.keySet())) {
            remove(uuid);
        }
    }

    /** Whether {@code uuid} currently has a tracked nametag (test/observability seam). */
    public boolean isTracked(UUID uuid) {
        return live.containsKey(uuid);
    }

    /** How many wearers are currently tracked. Test/observability seam for the show-guard invariant. */
    int trackedCount() {
        return live.size();
    }

    /**
     * Seed a tracked entry directly, bypassing the lib spawn. Test-only seam so a test can put a wearer into the
     * tracked state to exercise the show-when-tracked guard (which must reconcile, not show a second handle) without
     * driving the NMS-backed lib renderer the unit test cannot stand up.
     */
    void trackForTest(Player wearer, NametagHandle handle, NametagFormat format) {
        live.put(wearer.getUniqueId(), new Tracked(handle, format));
    }

    /**
     * The format this wearer should get, or empty when none applies. Package-private so the selection rule can be
     * tested directly without spawning a packet nametag.
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

    // The per-viewer text the lib re-asks for every refresh: resolve the wearer's lines for this specific viewer,
    // reading the live animation frame at call time so the lib's refresh loop animates without a plugin render loop.
    // The lines carry no relational (viewer-aware) placeholder today, so every viewer resolves to identical text, but
    // the resolution still flows through the per-viewer callback so a relational placeholder added later needs no
    // wiring change here — only the viewer would be threaded into the PAPI bridge.
    private PerViewerText perViewerText(NametagFormat format, Player wearer) {
        return viewer -> renderLines(format, wearer);
    }

    private List<Component> renderLines(NametagFormat format, Player wearer) {
        long frame = animations.tick();
        List<Component> rendered = new ArrayList<>(format.lines().size());
        for (String line : format.lines()) {
            String withName = line.replace("{player}", wearer.getName());
            rendered.add(HudText.render(wearer.getUniqueId(), animations.resolve(withName, frame)));
        }
        return rendered;
    }

    // The eligible-viewer supplier the lib loop re-reads each refresh on the wearer's region thread: online players
    // other than the wearer, within the format's viewer-distance cull, that the vanish gate (when respected) lets see
    // the wearer, and — unless the format hides on sneak while the wearer sneaks — at all. Returns UUIDs because that
    // is the lib audience type; the underlying cull mirrors what the old per-tick refresh computed.
    private Supplier<Set<UUID>> viewerSupplier(Player wearer, NametagFormat format) {
        return () -> {
            Set<UUID> uuids = new LinkedHashSet<>();
            for (Player viewer : eligibleViewers(wearer, format)) {
                uuids.add(viewer.getUniqueId());
            }
            return uuids;
        };
    }

    // Package-private so the cull is testable without spawning a nametag. See viewerSupplier for the contract.
    List<Player> eligibleViewers(Player wearer, NametagFormat format) {
        NametagVisibility visibility = format.visibility();
        if (visibility.hideWhileSneaking() && wearer.isSneaking()) {
            return List.of();
        }
        double maxSq = cullRadiusSquared(visibility.viewerDistance());
        List<Player> eligible = new ArrayList<>();
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.getUniqueId().equals(wearer.getUniqueId())) {
                continue;
            }
            if (visibility.respectVanish() && !canSee(viewer, wearer)) {
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

    // The squared cull radius for the eligible-viewer pass: an absent viewer-distance uses the renderer default radius,
    // an authored 0 disables the cull (Double.MAX_VALUE → every online viewer is a candidate, falling back to the
    // packet appearance view-range for distance fading), and a positive value is that flat block radius.
    private static double cullRadiusSquared(OptionalDouble viewerDistance) {
        double blocks = viewerDistance.orElse(DEFAULT_VIEWER_DISTANCE_BLOCKS);
        if (blocks <= 0) {
            return Double.MAX_VALUE;
        }
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
     * A live nametag: the lib {@link NametagHandle} (whose {@code remove()} tears down the entity for every viewer and
     * cancels the lib's refresh task) and the selected {@link NametagFormat} (so a format/appearance change is detected
     * and triggers a re-show).
     */
    private record Tracked(NametagHandle handle, NametagFormat format) {}
}
