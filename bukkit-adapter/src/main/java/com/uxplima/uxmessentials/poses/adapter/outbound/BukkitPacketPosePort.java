package com.uxplima.uxmessentials.poses.adapter.outbound;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Bed;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.destroystokyo.paper.profile.ProfileProperty;
import com.uxplima.uxmessentials.poses.application.port.PosePort;
import com.uxplima.uxmessentials.poses.domain.PoseType;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.packet.npc.EquipmentSlot;
import com.uxplima.uxmlib.packet.npc.NpcPackets;
import com.uxplima.uxmlib.packet.npc.NpcPose;
import com.uxplima.uxmlib.packet.tablist.TabSkin;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The {@link PosePort} that renders {@code /lay}, {@code /bellyflop}, and {@code /spin} as a stand-in copy of the
 * poser, built from uxmLib's packet layer.
 *
 * <p>The copy exists because of one stubborn client rule: a player riding anything is drawn sitting, whatever pose
 * the server sends. All three of these poses anchor the player on an invisible seat so they cannot walk out of the
 * pose, so overriding the real player's pose produced a body that was half seated and half lying down. Instead the
 * real player is made invisible and stripped of visible gear, and a fake player carrying their name and skin is
 * spawned at the same spot for everyone nearby, the poser included. Nothing is riding the copy, so it strikes the
 * pose cleanly; the real player stays seated, invisible, and exactly where they were.
 *
 * <p>Each pose is a different arrangement of the copy:
 *
 * <ul>
 *   <li>{@code /lay} is the sleeping pose. A sleeping body is laid out along the bed it sleeps in, so the copy is
 *       given a sleeping position and the viewer is sent a client-only bed at that spot. The spot is the bottom of
 *       the world, far under anyone's feet, where the bed can orient the body without ever being seen; the copy is
 *       then teleported back up to the poser, once as it spawns and twice more over the following ticks, because a
 *       client that hears a body is asleep keeps dragging it to its bed for a moment afterwards.
 *   <li>{@code /bellyflop} is the swimming pose, which face-down is exactly a belly flop.
 *   <li>{@code /spin} is the riptide spin. The client keeps that animation turning by itself for as long as the
 *       flag is set, so the spin needs no per-tick rotation, only a body pitched over so it spins flat.
 * </ul>
 *
 * <p>One repeating pass keeps the illusion honest: it re-computes who is close enough to see each copy, showing it
 * to arrivals and dropping it for anyone who walked off, and re-asserts the real player's invisibility and stripped
 * gear, which the server re-syncs whenever their inventory changes. It runs on the global region thread, where
 * {@code getOnlinePlayers()} is coherent under Folia, the {@code npc} renderer's discipline. {@link #shutdown()}
 * cancels it on module stop.
 */
@NullMarked
public final class BukkitPacketPosePort implements PosePort {

    /** How far, in blocks, a copy is shown: past this the viewer would not be tracking the real player either. */
    private static final int VIEW_RANGE = 64;

    /** The lie-down sits a hair above the seat so the body rests on the surface rather than sinking into it. */
    private static final double LAY_HEIGHT = 0.1125;

    /** A belly flop sits a little lower than a lie-down: the body is face-down, not on its back. */
    private static final double BELLYFLOP_HEIGHT = -0.19;

    /** The riptide spin turns around the body's own length, so the copy is pitched over to spin flat. */
    private static final float SPIN_PITCH = -90f;

    /**
     * When the sleeping body is put back where the poser lies. A client that learns an entity is asleep drags it
     * to its bed, and it keeps doing so for a tick after the news arrives, so one correction inside the spawn
     * bundle is not enough: without these two the body settles at the bed, which is the bottom of the world, and
     * the poser appears to have simply vanished.
     */
    private static final List<Duration> LAY_SETTLE = List.of(Duration.ofMillis(50), Duration.ofMillis(100));

    /** Strips every slot the copy's real owner could be wearing, so an invisible player carries nothing visible. */
    private static final Map<EquipmentSlot, ItemStack> NOTHING_WORN = Map.of();

    /** The slots mirrored onto the copy, so it wears what its owner wears, paired with their Bukkit counterparts. */
    private static final Map<EquipmentSlot, org.bukkit.inventory.EquipmentSlot> WORN_SLOTS = Map.of(
            EquipmentSlot.MAINHAND, org.bukkit.inventory.EquipmentSlot.HAND,
            EquipmentSlot.OFFHAND, org.bukkit.inventory.EquipmentSlot.OFF_HAND,
            EquipmentSlot.HEAD, org.bukkit.inventory.EquipmentSlot.HEAD,
            EquipmentSlot.CHEST, org.bukkit.inventory.EquipmentSlot.CHEST,
            EquipmentSlot.LEGS, org.bukkit.inventory.EquipmentSlot.LEGS,
            EquipmentSlot.FEET, org.bukkit.inventory.EquipmentSlot.FEET);

    private final Server server;
    private final Scheduler scheduler;
    private final NpcPackets packets;
    private final Logger log;
    // The copies in flight, keyed by their owner. Written from the poser's own thread, read on the global thread.
    private final ConcurrentHashMap<UUID, Copy> copies = new ConcurrentHashMap<>();
    private final AutoCloseable tickTask;

    public BukkitPacketPosePort(
            Server server, Scheduler scheduler, NpcPackets packets, Logger log, int refreshIntervalTicks) {
        this.server = Objects.requireNonNull(server, "server");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.packets = Objects.requireNonNull(packets, "packets");
        this.log = Objects.requireNonNull(log, "log");
        Duration period = Duration.ofMillis(Math.max(1L, refreshIntervalTicks) * 50L);
        this.tickTask = scheduler.repeatGlobal(this::tick, period, period);
    }

    @Override
    public void applyPose(PlayerRef who, PoseType pose) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(pose, "pose");
        if (pose != PoseType.LAY && pose != PoseType.BELLYFLOP && pose != PoseType.SPIN) {
            throw new IllegalArgumentException("BukkitPacketPosePort renders only the free poses, not " + pose);
        }
        Player player = server.getPlayer(who.uuid());
        if (player == null) {
            return;
        }
        Location at = Objects.requireNonNull(player.getLocation(), "player location");
        Copy copy = new Copy(
                packets.allocateEntityId(),
                UUID.randomUUID(),
                copyName(),
                pose,
                skinOf(player),
                at.getX(),
                at.getY() + height(pose),
                at.getZ(),
                at.getYaw(),
                at.getWorld().getMinHeight(),
                player.isInvisible());
        copies.put(who.uuid(), copy);
        // The owner stays where they are and keeps their seat; they simply stop being drawn, so the copy is the
        // only body anyone sees.
        player.setInvisible(true);
        scheduler.onGlobal(() -> refresh(who.uuid(), copy));
    }

    @Override
    public void clearPose(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        Copy copy = copies.remove(who.uuid());
        if (copy == null) {
            // A plain sit or a player-sit never had a copy, so there is nothing to take down.
            return;
        }
        Player player = server.getPlayer(who.uuid());
        if (player != null) {
            // Restore whatever they were before the pose: someone already invisible (a potion, a vanish) must not
            // be revealed just because they lay down.
            player.setInvisible(copy.wasInvisible);
        }
        scheduler.onGlobal(() -> {
            for (UUID viewerId : copy.viewers) {
                Player viewer = server.getPlayer(viewerId);
                if (viewer != null) {
                    hideFrom(viewer, copy, player);
                }
            }
            copy.viewers.clear();
        });
    }

    /** Cancel the refresh pass and forget every copy, so a disable or reload leaves nothing rendering. */
    public void shutdown() {
        try {
            tickTask.close();
        } catch (Exception e) {
            // A cancel failure must not strand the caller (the wiring disables the module after this); log and
            // carry on so the copies are still dropped and the disable completes.
            log.error("failed to cancel the poses refresh task", e);
        }
        copies.clear();
    }

    /** Whether {@code id} currently has a copy standing in for them, for the tests that pin the render. */
    public boolean isRendering(UUID id) {
        return copies.containsKey(Objects.requireNonNull(id, "id"));
    }

    /**
     * One refresh pass on the global region thread: bring each copy's audience up to date and re-assert what the
     * server keeps undoing, namely the owner's invisibility and their stripped gear.
     */
    public void tick() {
        copies.forEach(this::refresh);
    }

    private void refresh(UUID ownerId, Copy copy) {
        Player owner = server.getPlayer(ownerId);
        if (owner == null) {
            return;
        }
        if (!owner.isInvisible()) {
            owner.setInvisible(true);
        }
        Set<UUID> current = new HashSet<>();
        for (Player viewer : server.getOnlinePlayers()) {
            if (!inRange(viewer, owner)) {
                continue;
            }
            current.add(viewer.getUniqueId());
            if (copy.viewers.add(viewer.getUniqueId())) {
                showTo(viewer, copy, owner);
            } else {
                // Already watching: the server re-sends the owner's real gear whenever their inventory changes, so
                // the strip has to be re-stated or an invisible player starts carrying a floating sword again.
                packets.send(viewer, packets.equipment(owner.getEntityId(), NOTHING_WORN));
            }
        }
        copy.viewers.removeIf(viewerId -> {
            if (current.contains(viewerId)) {
                return false;
            }
            Player gone = server.getPlayer(viewerId);
            if (gone != null) {
                hideFrom(gone, copy, owner);
            }
            return true;
        });
    }

    private void showTo(Player viewer, Copy copy, Player owner) {
        if (copy.pose == PoseType.LAY) {
            // The bed the sleeping body is laid out along. It sits at the bottom of the world, so it orients the
            // copy without any viewer ever seeing it.
            viewer.sendBlockChange(bedLocation(owner.getWorld(), copy), bedFacing(copy.yaw));
        }
        List<Object> shown = new ArrayList<>();
        shown.add(packets.tabAdd(copy.profileId, copy.name, copy.skin, false));
        shown.add(packets.spawnPlayer(copy.entityId, copy.profileId, copy.x, copy.y, copy.z, copy.yaw, 0f));
        // The copy carries its own generated name, so hiding the nametag through its team never touches the owner's.
        shown.add(packets.team(copy.name, copy.name, null, false, true));
        shown.add(packets.pose(copy.entityId, npcPose(copy.pose)));
        shown.add(packets.equipment(copy.entityId, wornBy(owner)));
        shown.add(packets.equipment(owner.getEntityId(), NOTHING_WORN));
        shown.add(packets.headLook(copy.entityId, copy.yaw));
        if (copy.pose == PoseType.LAY) {
            shown.add(packets.sleepingPosition(
                    copy.entityId, (int) Math.floor(copy.x), copy.bedY, (int) Math.floor(copy.z)));
            // The sleep field would otherwise drag the body down to the bed at the bottom of the world, so put it
            // back where the poser actually lies.
            shown.add(packets.teleport(copy.entityId, copy.x, copy.y, copy.z, copy.yaw, 0f));
        }
        if (copy.pose == PoseType.SPIN) {
            shown.add(packets.spinAttack(copy.entityId, true));
            shown.add(packets.bodyLook(copy.entityId, copy.yaw, SPIN_PITCH));
        }
        packets.send(viewer, packets.bundle(shown));
        if (copy.pose == PoseType.LAY) {
            UUID viewerId = viewer.getUniqueId();
            for (Duration settle : LAY_SETTLE) {
                scheduler.laterGlobal(settle, () -> liftOffTheBed(viewerId, copy));
            }
        }
    }

    /**
     * Put a sleeping copy back where its poser lies, once the viewer's client has finished dragging it down to
     * the bed. Skipped when the copy is already gone or the viewer no longer watches it, so a pose that ends
     * within the tick or two this waits leaves nothing behind.
     */
    private void liftOffTheBed(UUID viewerId, Copy copy) {
        if (!copy.viewers.contains(viewerId)) {
            return;
        }
        Player viewer = server.getPlayer(viewerId);
        if (viewer == null) {
            return;
        }
        packets.send(viewer, packets.teleport(copy.entityId, copy.x, copy.y, copy.z, copy.yaw, 0f));
    }

    private void hideFrom(Player viewer, Copy copy, @Nullable Player owner) {
        List<Object> hidden = new ArrayList<>();
        hidden.add(packets.remove(copy.entityId));
        hidden.add(packets.tabRemove(copy.profileId));
        hidden.add(packets.glowColorRemove(copy.name));
        if (owner != null) {
            // Hand the owner's real gear back, undoing the strip that kept their invisible body empty.
            hidden.add(packets.equipment(owner.getEntityId(), wornBy(owner)));
        }
        packets.send(viewer, packets.bundle(hidden));
        if (copy.pose == PoseType.LAY && owner != null) {
            Location bed = bedLocation(owner.getWorld(), copy);
            Block real = bed.getBlock();
            viewer.sendBlockChange(bed, real.getBlockData());
        }
    }

    private boolean inRange(Player viewer, Player owner) {
        return viewer.getWorld().equals(owner.getWorld())
                && Objects.requireNonNull(viewer.getLocation(), "viewer location")
                                .distanceSquared(Objects.requireNonNull(owner.getLocation(), "owner location"))
                        <= (double) VIEW_RANGE * VIEW_RANGE;
    }

    private static Location bedLocation(World world, Copy copy) {
        return new Location(world, Math.floor(copy.x), copy.bedY, Math.floor(copy.z));
    }

    /** The bed data whose head faces the way the poser was looking, which is the way the body ends up lying. */
    private static Bed bedFacing(float yaw) {
        Bed bed = (Bed) Material.WHITE_BED.createBlockData();
        bed.setPart(Bed.Part.HEAD);
        bed.setFacing(facing(yaw));
        return bed;
    }

    private static BlockFace facing(float yaw) {
        int quarter = Math.floorMod(Math.round(yaw / 90f), 4);
        return switch (quarter) {
            case 0 -> BlockFace.NORTH;
            case 1 -> BlockFace.EAST;
            case 2 -> BlockFace.SOUTH;
            default -> BlockFace.WEST;
        };
    }

    private static NpcPose npcPose(PoseType pose) {
        return switch (pose) {
            case LAY -> NpcPose.SLEEPING;
            case BELLYFLOP -> NpcPose.SWIMMING;
            case SPIN -> NpcPose.SPIN_ATTACK;
            default -> throw new IllegalArgumentException("no copy pose for " + pose);
        };
    }

    private static double height(PoseType pose) {
        return switch (pose) {
            case LAY -> LAY_HEIGHT;
            case BELLYFLOP -> BELLYFLOP_HEIGHT;
            default -> 0.0;
        };
    }

    private static Map<EquipmentSlot, ItemStack> wornBy(Player owner) {
        Map<EquipmentSlot, ItemStack> worn = new EnumMap<>(EquipmentSlot.class);
        WORN_SLOTS.forEach(
                (slot, bukkitSlot) -> worn.put(slot, owner.getInventory().getItem(bukkitSlot)));
        return worn;
    }

    private static @Nullable TabSkin skinOf(Player player) {
        for (ProfileProperty property : player.getPlayerProfile().getProperties()) {
            if ("textures".equals(property.getName())) {
                return new TabSkin(property.getValue(), property.getSignature());
            }
        }
        // An offline-mode player carries no textures, so the copy renders as the default skin.
        return null;
    }

    /** A short unique name for the copy, so it never collides with a real player's name or team membership. */
    private static String copyName() {
        return "uxm" + UUID.randomUUID().toString().replace("-", "").substring(0, 13);
    }

    /**
     * One poser's stand-in. Every field but {@link #viewers} is written once when the pose begins; the viewer set
     * is touched only from the global region thread the refresh pass runs on.
     */
    private static final class Copy {

        private final int entityId;
        private final UUID profileId;
        private final String name;
        private final PoseType pose;
        private final @Nullable TabSkin skin;
        private final double x;
        private final double y;
        private final double z;
        private final float yaw;
        private final int bedY;
        private final boolean wasInvisible;
        private final Set<UUID> viewers = new HashSet<>();

        private Copy(
                int entityId,
                UUID profileId,
                String name,
                PoseType pose,
                @Nullable TabSkin skin,
                double x,
                double y,
                double z,
                float yaw,
                int bedY,
                boolean wasInvisible) {
            this.entityId = entityId;
            this.profileId = profileId;
            this.name = name;
            this.pose = pose;
            this.skin = skin;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.bedY = bedY;
            this.wasInvisible = wasInvisible;
        }
    }
}
