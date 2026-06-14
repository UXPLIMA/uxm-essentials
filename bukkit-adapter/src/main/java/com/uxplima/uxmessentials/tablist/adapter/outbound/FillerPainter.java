package com.uxplima.uxmessentials.tablist.adapter.outbound;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.tablist.domain.TablistFiller;
import com.uxplima.uxmessentials.tablist.domain.TablistLayout;
import com.uxplima.uxmessentials.tablist.domain.TablistSkinSource;
import com.uxplima.uxmlib.packet.tablist.TabEntry;
import com.uxplima.uxmlib.packet.tablist.TabListPackets;
import com.uxplima.uxmlib.packet.tablist.TabSkin;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Paints the fixed-slot {@link TablistLayout filler grid} a {@link TablistRenderer}'s selected format may carry. Split out
 * of the renderer so each class stays a cohesive size: the renderer owns the header/footer/name/order/skin row of the real
 * players, this owns the synthetic filler cells.
 *
 * <p>A selected layout's {@link TablistFiller} rows fill the tab cells the real players do not. Each filler is painted at
 * its {@link TablistLayout#slotToListOrder slot list-order} through a packet sent to the one viewer, with its text resolved
 * through the renderer's pipeline (the injected {@link TextRenderer}) and its skin through the {@link TablistSkinResolver}.
 * Each filler entry carries a <em>deterministic</em> UUID derived from {@code (viewer, slot)} ({@link #fillerId}) so a
 * re-paint updates the same entry and never leaks a fresh fake row per tick. The painted set is tracked per viewer
 * ({@link #appliedFillers}) and diffed: a steady tick whose filler text and skin are unchanged re-sends nothing, while a
 * switch away (a format with no or fewer fillers), a {@link #clear}/{@link #forget}, or a disable removes the filler
 * entries by their tracked UUIDs so no fake row is ever orphaned.
 *
 * <p>Every method touches the live viewer, so the renderer must call this on the viewer's region/entity thread — the same
 * contract as {@link TablistRenderer#renderFor(Player)}.
 */
@NullMarked
final class FillerPainter {

    /**
     * Resolves a filler's raw MiniMessage source to a rendered {@link Component} for a viewer at a render tick — the
     * renderer's own pipeline (built-in tokens → PlaceholderAPI → MiniMessage), handed in so the filler cells render
     * identically to the header/footer/name without this class re-implementing it.
     */
    @FunctionalInterface
    interface TextRenderer {
        Component render(Player viewer, String source, long tick);
    }

    private final TabListPackets packets;
    private final TablistSkinResolver skinResolver;
    private final TextRenderer textRenderer;

    /**
     * The filler grid currently painted for each viewer, keyed by viewer UUID then by 1-based slot. The value remembers
     * what was last sent for that (viewer, slot) cell — the rendered text, list order, skin source, and whether the skin
     * was still resolving — so a steady-state tick whose cell is unchanged re-sends nothing (no flicker), while a switch
     * to a format with different/fewer fillers removes the cells that fell away by their deterministic {@link #fillerId}
     * UUIDs. An absent viewer key means no fillers are painted for that viewer. The inner map is a {@link LinkedHashMap}
     * guarded by the outer {@link ConcurrentHashMap}; every mutation runs on the viewer's region/entity thread, so the
     * inner map needs no concurrency of its own.
     */
    private final Map<UUID, Map<Integer, AppliedFiller>> appliedFillers = new ConcurrentHashMap<>();

    FillerPainter(TabListPackets packets, TablistSkinResolver skinResolver, TextRenderer textRenderer) {
        this.packets = Objects.requireNonNull(packets, "packets");
        this.skinResolver = Objects.requireNonNull(skinResolver, "skinResolver");
        this.textRenderer = Objects.requireNonNull(textRenderer, "textRenderer");
    }

    /**
     * Reconcile the filler grid painted for {@code viewer} with the selected format's {@link TablistLayout}. Each filler
     * is painted at its slot's list-order through a packet sent to the one viewer, with its text and skin resolved per
     * viewer. The painted set is diffed against {@link #appliedFillers}: an unchanged cell re-sends nothing, a changed
     * cell is re-sent, and a cell that fell away (the new layout omits it) is removed by its deterministic
     * {@link #fillerId} UUID so no fake row is orphaned. An empty layout removes every filler this viewer carried.
     */
    void applyFillers(Player viewer, TablistLayout layout, long tick) {
        UUID viewerId = viewer.getUniqueId();
        Map<Integer, AppliedFiller> previous = appliedFillers.get(viewerId);
        if (layout.isEmpty()) {
            if (previous != null) {
                removeFillers(viewer, previous);
                appliedFillers.remove(viewerId);
            }
            return;
        }
        Map<Integer, AppliedFiller> next = new LinkedHashMap<>();
        for (TablistFiller filler : layout.fillers()) {
            paintFiller(viewer, layout, filler, tick, previous, next);
        }
        removeStaleFillers(viewer, previous, next.keySet());
        appliedFillers.put(viewerId, next);
    }

    /**
     * Paint one filler into {@code viewer}'s grid, re-sending only when its <em>resolved</em> text, order, or skin
     * changed since the last tick. The text is keyed on the rendered component, not the raw source, so an animated or
     * relational-placeholder filler re-sends as it changes (the animation steps) while a static filler re-sends nothing
     * on a steady tick — the per-cell flicker guard. The entry id is the deterministic {@link #fillerId} so the same
     * cell is always the same tab entry: an update targets it and a later removal removes it exactly.
     *
     * <p>An offline filler skin that is still resolving (the {@link TablistSkinResolver} returns empty while its async
     * fetch is in flight) is painted skinless this tick and recorded as a <em>pending</em> cell. The cell is still
     * tracked (so a later removal targets its entry and never orphans the skinless row painted now) but the
     * {@link AppliedFiller#pending() pending flag} keeps it from ever equalling the resolved cell, so the next tick
     * re-evaluates and repaints the texture once the fetch lands rather than early-returning on an unchanged tuple.
     */
    private void paintFiller(
            Player viewer,
            TablistLayout layout,
            TablistFiller filler,
            long tick,
            @Nullable Map<Integer, AppliedFiller> previous,
            Map<Integer, AppliedFiller> next) {
        int slot = filler.slot();
        int order = TablistLayout.slotToListOrder(slot, layout.direction(), layout.gridRows());
        Optional<TablistSkinSource> skinSource = filler.skin();
        Optional<TabSkin> skin = skinSource.flatMap(skinResolver::resolve);
        boolean pending = skinSource.isPresent() && skin.isEmpty();
        Component text = textRenderer.render(viewer, filler.text(), tick);
        AppliedFiller desired = new AppliedFiller(text, order, skinSource.orElse(null), pending);
        AppliedFiller current = previous == null ? null : previous.get(slot);
        if (current != null && desired.equals(current)) {
            // Unchanged, fully-resolved cell: keep the entry as-is, send nothing this tick (the flicker guard). A
            // pending desired never equals a current (the flag differs once resolved), so it always repaints below.
            next.put(slot, current);
            return;
        }
        UUID id = fillerId(viewer.getUniqueId(), slot);
        packets.send(viewer, packets.addOrUpdate(new TabEntry(id, text, order, skin.orElse(null), "")));
        next.put(slot, desired);
    }

    /** Remove the filler entries whose slots are no longer in {@code keptSlots} — the cells the new layout dropped. */
    private void removeStaleFillers(
            Player viewer, @Nullable Map<Integer, AppliedFiller> previous, Set<Integer> keptSlots) {
        if (previous == null) {
            return;
        }
        List<UUID> stale = new ArrayList<>();
        for (Integer slot : previous.keySet()) {
            if (!keptSlots.contains(slot)) {
                stale.add(fillerId(viewer.getUniqueId(), slot));
            }
        }
        if (!stale.isEmpty()) {
            packets.send(viewer, packets.remove(stale));
        }
    }

    /** Remove every filler entry the viewer carried — used on a switch to an empty layout, clear, forget, or stop. */
    private void removeFillers(Player viewer, Map<Integer, AppliedFiller> painted) {
        List<UUID> ids = new ArrayList<>(painted.size());
        for (Integer slot : painted.keySet()) {
            ids.add(fillerId(viewer.getUniqueId(), slot));
        }
        if (!ids.isEmpty()) {
            packets.send(viewer, packets.remove(ids));
        }
    }

    /** Remove every filler entry this painter sent for {@code viewer}, and drop the viewer's filler tracking. */
    void clear(Player viewer) {
        Map<Integer, AppliedFiller> painted = appliedFillers.remove(viewer.getUniqueId());
        if (painted != null) {
            removeFillers(viewer, painted);
        }
    }

    /**
     * Drop the viewer's filler tracking on quit <em>without</em> sending a remove packet. The connection is closing, so
     * a remove packet to a dead channel is pointless; just forgetting the tracking lets a relog re-paint from scratch.
     */
    void forget(Player viewer) {
        appliedFillers.remove(viewer.getUniqueId());
    }

    /**
     * The deterministic, stable UUID for a (viewer, slot) filler cell, so a re-paint updates the same tab entry and a
     * removal targets it exactly — never a fresh random id per tick (which would leak a new fake row each refresh). The
     * id is derived from the viewer's id and the slot, so the same cell is always the same entry across ticks and relogs.
     */
    private static UUID fillerId(UUID viewer, int slot) {
        return UUID.nameUUIDFromBytes(("uxmf:" + viewer + ":" + slot).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * The filler cell last painted for a (viewer, slot): its <em>rendered</em> text component, list order, skin source
     * (or {@code null} for no skin), and whether the skin was still resolving when the cell was painted. Two paints with
     * an equal tuple are the same cell, so the entry is not re-sent — the per-cell flicker guard. Keying on the rendered
     * component (not the raw source) means an animated or relational filler re-sends as its frame/placeholder changes —
     * acceptable, mirroring the scoreboard/header animation surface — while a static filler on a steady-state tick
     * re-sends nothing. The {@code pending} flag distinguishes a cell painted skinless while its offline texture is still
     * being fetched from the same cell once resolved, so the next tick repaints rather than treating the skinless paint
     * as the steady state. {@link Component} has a value-based {@code equals}.
     */
    private record AppliedFiller(Component text, int order, @Nullable TablistSkinSource skinSource, boolean pending) {}
}
