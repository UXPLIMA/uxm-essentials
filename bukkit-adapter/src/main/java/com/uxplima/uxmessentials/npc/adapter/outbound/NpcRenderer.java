package com.uxplima.uxmessentials.npc.adapter.outbound;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.npc.application.port.NpcView;
import com.uxplima.uxmessentials.npc.domain.Npc;
import com.uxplima.uxmessentials.npc.domain.NpcName;
import com.uxplima.uxmessentials.npc.domain.NpcSkin;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmlib.packet.npc.NpcPackets;
import com.uxplima.uxmlib.packet.tablist.TabSkin;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The outbound seam that keeps the in-world fake players in step with the stored model, realised over the uxmLib
 * NPC packet stack. An NPC has no real entity: each viewer is sent a player-info ADD (carrying the name and
 * skin), then a spawn-player packet, in one bundle so they arrive together; a moment later a tab-remove hides
 * the entry from the tab list while the spawned fake player keeps its skin. The renderer tracks which NPCs each
 * viewer has been shown ({@link #shownTo}), so it can send a clean remove on quit, delete, or a move out of
 * range and re-send on join — a viewer never keeps a ghost.
 *
 * <p>Every NPC has a stable {@link RenderedNpc#profileId() profile uuid} (derived from its name) and an entity
 * id allocated once and reused, so a re-render (move or re-skin) is a remove-then-spawn under the same id and a
 * client never accumulates duplicates. Sends hop onto the viewer's entity region thread through the injected
 * {@link Scheduler} (Folia-correct); resolving the viewer's distance reads the live player there. A viewer
 * within {@link #renderRange} blocks of an NPC in the same world is eligible.
 */
@NullMarked
public final class NpcRenderer implements NpcView {

    /** Profile names are capped at 16 chars by the protocol, so a longer NPC name is truncated for the entry. */
    private static final int MAX_PROFILE_NAME = 16;
    /** A vanilla player's eye height above its feet — where a fake player's head sits for the look aim. */
    private static final double EYE_HEIGHT = 1.62;

    private final NpcPackets packets;
    private final Scheduler scheduler;
    private final double renderRange;
    private final double lookRange;
    private final Duration tabHideDelay;
    private final Map<String, RenderedNpc> live = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> shownTo = new ConcurrentHashMap<>();
    private final Map<Integer, String> nameByEntityId = new ConcurrentHashMap<>();

    public NpcRenderer(
            NpcPackets packets, Scheduler scheduler, double renderRange, double lookRange, Duration tabHideDelay) {
        this.packets = Objects.requireNonNull(packets, "packets");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.renderRange = renderRange;
        this.lookRange = lookRange;
        this.tabHideDelay = Objects.requireNonNull(tabHideDelay, "tabHideDelay");
    }

    @Override
    public void render(Npc npc) {
        Objects.requireNonNull(npc, "npc");
        RenderedNpc rendered = track(npc);
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            reconcileViewer(viewer, rendered);
        }
    }

    @Override
    public void despawn(NpcName name) {
        Objects.requireNonNull(name, "name");
        RenderedNpc removed = live.remove(name.value());
        if (removed == null) {
            return;
        }
        nameByEntityId.remove(removed.entityId());
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            removeFromViewer(viewer, removed);
        }
    }

    /** Show every in-range NPC to a player who just joined, and start tracking them as a viewer. */
    public void showAllTo(Player viewer) {
        Objects.requireNonNull(viewer, "viewer");
        shownTo.computeIfAbsent(viewer.getUniqueId(), id -> ConcurrentHashMap.newKeySet());
        for (RenderedNpc rendered : live.values()) {
            reconcileViewer(viewer, rendered);
        }
    }

    /** Forget a player who quit: drop their shown-set so nothing leaks, no packets needed (they are gone). */
    public void forget(Player viewer) {
        Objects.requireNonNull(viewer, "viewer");
        shownTo.remove(viewer.getUniqueId());
    }

    /** Re-evaluate every NPC for every online viewer (the refresh tick): show in-range, remove out-of-range. */
    public void refresh() {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            for (RenderedNpc rendered : live.values()) {
                reconcileViewer(viewer, rendered);
            }
        }
    }

    /** Remove every NPC from every viewer now — call on module stop so no fake player is orphaned. */
    public void despawnAll() {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            for (RenderedNpc rendered : live.values()) {
                removeFromViewer(viewer, rendered);
            }
        }
        live.clear();
        shownTo.clear();
        nameByEntityId.clear();
    }

    /**
     * The name of the NPC rendered under {@code entityId}, or {@code null} when no NPC owns it. Resolves a click
     * on a fake player's (server-unknown) entity id back to the stored NPC — the entity id is allocated once per
     * NPC and shared by every viewer, so a single map answers for all of them.
     */
    public @Nullable String npcNameAt(int entityId) {
        return nameByEntityId.get(entityId);
    }

    /**
     * Re-aim every looking NPC at its nearby viewers (the look tick). For each online viewer and each NPC the
     * viewer is currently shown, if the NPC has look-at-player on and the viewer is within the look range, send
     * that viewer head- and body-look packets turning the fake player toward the viewer's eyes — a per-viewer
     * rotation, so each viewer sees the NPC face them. Disabled or out-of-range NPCs are left at their fixed
     * facing.
     */
    public void lookTick() {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            for (RenderedNpc rendered : live.values()) {
                if (rendered.npc().lookAtPlayer()) {
                    aimAtViewer(viewer, rendered);
                }
            }
        }
    }

    private void aimAtViewer(Player viewer, RenderedNpc rendered) {
        // Reads the live viewer location, so it must run on the viewer's region thread alongside the send.
        scheduler.onEntity(BukkitRefs.toRef(viewer), () -> {
            if (!isShown(viewer, rendered)
                    || !inLookRange(viewer, rendered.npc().location())) {
                return;
            }
            Location eye = viewer.getEyeLocation();
            Position at = rendered.npc().location();
            LookAngles look = LookAngles.facing(
                    at.x(), at.y() + EYE_HEIGHT, at.z(), eye.getX(), eye.getY(), eye.getZ(), at.yaw());
            packets.send(viewer, packets.headLook(rendered.entityId(), look.yaw()));
            packets.send(viewer, packets.bodyLook(rendered.entityId(), look.yaw(), look.pitch()));
        });
    }

    private boolean isShown(Player viewer, RenderedNpc rendered) {
        Set<String> shown = shownTo.get(viewer.getUniqueId());
        return shown != null && shown.contains(rendered.npc().name().value());
    }

    private boolean inLookRange(Player viewer, Position npcAt) {
        Position viewerAt = BukkitRefs.toPosition(Objects.requireNonNull(viewer.getLocation(), "viewer location"));
        return viewerAt.distanceTo(npcAt) <= lookRange;
    }

    private RenderedNpc track(Npc npc) {
        RenderedNpc rendered = live.compute(
                npc.name().value(),
                (name, existing) ->
                        existing == null ? new RenderedNpc(npc, packets.allocateEntityId()) : existing.withNpc(npc));
        nameByEntityId.put(rendered.entityId(), npc.name().value());
        return rendered;
    }

    /** Show the NPC to the viewer if in range and not yet shown (or refresh it), else remove it if out of range. */
    private void reconcileViewer(Player viewer, RenderedNpc rendered) {
        // The range check reads the live player location, so it must run on the viewer's own region thread along
        // with the send; doing it inline here would touch a Player off its region thread (unsafe on Folia).
        scheduler.onEntity(BukkitRefs.toRef(viewer), () -> {
            if (inRange(viewer, rendered.npc().location())) {
                spawnForViewer(viewer, rendered);
            } else {
                removeFromViewer(viewer, rendered);
            }
        });
    }

    private void spawnForViewer(Player viewer, RenderedNpc rendered) {
        UUID profileId = rendered.profileId();
        Position at = rendered.npc().location();
        Object tabAdd = packets.tabAdd(
                profileId, profileName(rendered.npc()), tabSkin(rendered.npc().skin()));
        Object spawn =
                packets.spawnPlayer(rendered.entityId(), profileId, at.x(), at.y(), at.z(), at.yaw(), at.pitch());
        packets.send(viewer, packets.bundle(List.of(tabAdd, spawn)));
        packets.send(viewer, packets.headLook(rendered.entityId(), at.yaw()));
        packets.send(viewer, packets.bodyLook(rendered.entityId(), at.yaw(), at.pitch()));
        // Hide the entry from the tab list a moment later, once the client has parsed it — the spawned fake
        // player keeps its skin even after the entry is gone.
        scheduler.asyncAfter(tabHideDelay, () -> packets.send(viewer, packets.tabRemove(profileId)));
        shownTo.computeIfAbsent(viewer.getUniqueId(), id -> ConcurrentHashMap.newKeySet())
                .add(rendered.npc().name().value());
    }

    private void removeFromViewer(Player viewer, RenderedNpc rendered) {
        Set<String> shown = shownTo.get(viewer.getUniqueId());
        if (shown == null || !shown.remove(rendered.npc().name().value())) {
            return;
        }
        packets.send(viewer, packets.remove(rendered.entityId()));
        packets.send(viewer, packets.tabRemove(rendered.profileId()));
    }

    private boolean inRange(Player viewer, Position npcAt) {
        Position viewerAt = BukkitRefs.toPosition(Objects.requireNonNull(viewer.getLocation(), "viewer location"));
        return viewerAt.distanceTo(npcAt) <= renderRange;
    }

    private static String profileName(Npc npc) {
        String name = npc.name().value();
        return name.length() <= MAX_PROFILE_NAME ? name : name.substring(0, MAX_PROFILE_NAME);
    }

    private static @Nullable TabSkin tabSkin(@Nullable NpcSkin skin) {
        return skin == null ? null : new TabSkin(skin.texture(), skin.signature());
    }
}
