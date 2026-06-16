package com.uxplima.uxmessentials.npc.adapter.outbound;

import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.npc.domain.Npc;
import com.uxplima.uxmessentials.npc.domain.NpcSkin;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmlib.packet.npc.EquipmentSlot;
import com.uxplima.uxmlib.packet.npc.NamedColor;
import com.uxplima.uxmlib.packet.npc.NpcPackets;
import com.uxplima.uxmlib.packet.tablist.TabSkin;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Builds and sends the spawn packets for one (viewer, NPC) pair, branching on type. A fake player takes the
 * player path — a player-info ADD (carrying the name and skin) bundled with a spawn-player packet so they arrive
 * together, then a deferred tab-remove that hides the entry while the spawned fake player keeps its skin; any
 * other type spawns the mob through {@code spawnEntity} with no tab entry and no skin. Both paths then aim the
 * entity at its fixed facing and dress it (equipment + glow). The {@link NpcRenderer} owns which NPCs each viewer
 * has been shown and only marks a viewer shown when {@link #spawn(Player, RenderedNpc)} reports the spawn went
 * out, so a skipped bad type never leaves a phantom in the tracking map.
 *
 * <p>An unknown stored entity type resolves to nothing and is skipped (logged once, never thrown on the render
 * thread), so a bad row never spawns. The warn-once cache lives here because it is part of the type-resolution
 * concern: the renderer's 1s reconcile retries an unresolvable NPC for every viewer every tick (the skip never
 * marks the viewer shown), so an unconditional warn would flood the log. This caps it at one line per NPC per
 * distinct bad value and re-arms when the type changes — to a valid type (which renders and clears the record via
 * the {@code spawn} success path) or to a different bad value (which warns afresh). The renderer calls
 * {@link #forget(String)} / {@link #forgetAll()} on its despawn paths so a deleted NPC drops its record too.
 */
@NullMarked
public final class NpcViewSpawner {

    /** Profile names are capped at 16 chars by the protocol, so a longer NPC name is truncated for the entry. */
    private static final int MAX_PROFILE_NAME = 16;

    private final NpcPackets packets;
    private final Scheduler scheduler;
    private final Logger log;
    private final Duration tabHideDelay;
    // NPC name -> the unresolvable entity-type value we already warned about, so a bad row is logged once rather
    // than every refresh tick for every viewer.
    private final Map<String, String> warnedBadType = new ConcurrentHashMap<>();

    public NpcViewSpawner(NpcPackets packets, Scheduler scheduler, Logger log, Duration tabHideDelay) {
        this.packets = Objects.requireNonNull(packets, "packets");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.log = Objects.requireNonNull(log, "log");
        this.tabHideDelay = Objects.requireNonNull(tabHideDelay, "tabHideDelay");
    }

    /**
     * Spawn the NPC for this viewer, branching on type. A fake player takes the player path (tab-add + spawn,
     * deferred tab-hide, skin); any other type spawns the mob through {@code spawnEntity} with no tab entry and no
     * skin. An unknown stored type resolves to nothing and is skipped (logged, never thrown on the render thread),
     * so a bad row never spawns. Both real paths then aim the entity and dress it (equipment + glow).
     *
     * @return {@code true} when the spawn went out (so the caller should mark the viewer shown), {@code false}
     *     when the stored type was unresolvable and the spawn was skipped.
     */
    boolean spawn(Player viewer, RenderedNpc rendered) {
        Npc npc = rendered.npc();
        if (npc.isPlayerType()) {
            spawnPlayerForViewer(viewer, rendered);
        } else {
            String typeKey = bukkitTypeKey(npc.entityType());
            if (typeKey == null) {
                warnBadTypeOnce(npc);
                return false;
            }
            spawnMobForViewer(viewer, rendered, typeKey);
        }
        // The type resolved (player or a real mob), so forget any earlier warning for this NPC — a fixed type
        // re-arms the warn if it ever breaks again.
        warnedBadType.remove(npc.name().value());
        Position at = npc.location();
        packets.send(viewer, packets.headLook(rendered.entityId(), at.yaw()));
        packets.send(viewer, packets.bodyLook(rendered.entityId(), at.yaw(), at.pitch()));
        applyAppearance(viewer, rendered);
        return true;
    }

    /** Drop the warned-bad-type record for a despawned NPC so a later recreate warns afresh on a fresh problem. */
    void forget(String npcName) {
        warnedBadType.remove(npcName);
    }

    /** Drop every warned-bad-type record — call on a full despawn so nothing outlives the module. */
    void forgetAll() {
        warnedBadType.clear();
    }

    private void spawnPlayerForViewer(Player viewer, RenderedNpc rendered) {
        UUID profileId = rendered.profileId();
        Position at = rendered.npc().location();
        Object tabAdd = packets.tabAdd(
                profileId, profileName(rendered.npc()), tabSkin(rendered.npc().skin()));
        Object spawn =
                packets.spawnPlayer(rendered.entityId(), profileId, at.x(), at.y(), at.z(), at.yaw(), at.pitch());
        packets.send(viewer, packets.bundle(List.of(tabAdd, spawn)));
        // Hide the entry from the tab list a moment later, once the client has parsed it — the spawned fake
        // player keeps its skin even after the entry is gone.
        scheduler.asyncAfter(tabHideDelay, () -> packets.send(viewer, packets.tabRemove(profileId)));
    }

    private void spawnMobForViewer(Player viewer, RenderedNpc rendered, String typeKey) {
        // A mob has no tab entry and no skin: the spawn UUID is the stable per-NPC entity uuid, not a profile.
        Position at = rendered.npc().location();
        packets.send(
                viewer,
                packets.spawnEntity(
                        rendered.entityId(),
                        rendered.profileId(),
                        typeKey,
                        at.x(),
                        at.y(),
                        at.z(),
                        at.yaw(),
                        at.pitch()));
    }

    /**
     * Dress the just-spawned fake player for this viewer: send its equipment, then its glow toggle and (when the
     * NPC carries a colour) the team that tints the outline. An equipment slot whose stored token does not resolve
     * to a real item — an unknown material name, or a corrupt serialized payload — is dropped from the map, so the
     * slot shows empty rather than failing the whole spawn, and an unparseable colour falls back to the default
     * white outline; the appearance is always fail-soft.
     */
    private void applyAppearance(Player viewer, RenderedNpc rendered) {
        Npc npc = rendered.npc();
        if (npc.hasEquipment()) {
            packets.send(viewer, packets.equipment(rendered.entityId(), resolveEquipment(npc)));
        }
        if (npc.glowing()) {
            packets.send(viewer, packets.glow(rendered.entityId(), true));
            NamedColor color = npc.hasGlowColor() ? parseColor(npc.glowColor()) : null;
            packets.send(viewer, packets.glowColor(glowTeam(npc), profileName(npc), color));
        }
    }

    /**
     * Log the unresolvable stored type for {@code npc} once. The 1s reconcile retries a bad NPC for every viewer
     * every tick (the skip never marks the viewer shown), so warning unconditionally would flood the log; this
     * warns only the first time a given NPC carries a given bad value, and re-warns if the value later changes.
     */
    private void warnBadTypeOnce(Npc npc) {
        String name = npc.name().value();
        String badType = npc.entityType();
        if (!badType.equals(warnedBadType.put(name, badType))) {
            log.warn("NPC {} has an unknown entity type {}, skipping its spawn", name, badType);
        }
    }

    private static String profileName(Npc npc) {
        String name = npc.name().value();
        return name.length() <= MAX_PROFILE_NAME ? name : name.substring(0, MAX_PROFILE_NAME);
    }

    private static @Nullable TabSkin tabSkin(@Nullable NpcSkin skin) {
        return skin == null ? null : new TabSkin(skin.texture(), skin.signature());
    }

    /** Resolve each stored token (a serialized item or a legacy material name) to a real item, dropping a slot
     * whose token this server cannot resolve. */
    private static Map<EquipmentSlot, ItemStack> resolveEquipment(Npc npc) {
        Map<EquipmentSlot, ItemStack> resolved = new EnumMap<>(EquipmentSlot.class);
        for (Map.Entry<com.uxplima.uxmessentials.npc.domain.EquipmentSlot, String> entry :
                npc.equipment().entrySet()) {
            EquipmentSlot slot = toPacketSlot(entry.getKey());
            EquipmentPayloads.resolve(entry.getValue()).ifPresent(item -> resolved.put(slot, item));
        }
        return resolved;
    }

    /** Map a domain equipment slot onto the uxmLib packet slot — the single place those two enums meet. */
    private static EquipmentSlot toPacketSlot(com.uxplima.uxmessentials.npc.domain.EquipmentSlot slot) {
        return switch (slot) {
            case MAINHAND -> EquipmentSlot.MAINHAND;
            case OFFHAND -> EquipmentSlot.OFFHAND;
            case HEAD -> EquipmentSlot.HEAD;
            case CHEST -> EquipmentSlot.CHEST;
            case LEGS -> EquipmentSlot.LEGS;
            case FEET -> EquipmentSlot.FEET;
        };
    }

    /** Parse a stored colour name to a {@link NamedColor}, falling back to the default white outline when unknown. */
    private static @Nullable NamedColor parseColor(@Nullable String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            return NamedColor.valueOf(name.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

    /** The stable per-NPC scoreboard team name that tints its glow (capped well under the 16-char team-name limit). */
    private static String glowTeam(Npc npc) {
        return profileName(npc);
    }

    /**
     * Resolve a stored uppercase entity-type name to its canonical {@code minecraft:…} key, or {@code null} when
     * the name no longer names a real Bukkit type. A type that vanished between saves (a removed type, a typo in a
     * hand-edited row) returns {@code null} so the caller skips the spawn rather than throwing on the render thread.
     */
    private static @Nullable String bukkitTypeKey(String entityTypeName) {
        try {
            return EntityType.valueOf(entityTypeName.toUpperCase(Locale.ROOT))
                    .getKey()
                    .asString();
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }
}
