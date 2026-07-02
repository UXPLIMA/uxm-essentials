package com.uxplima.uxmessentials.poses.adapter.inbound.listener;

import java.util.Objects;
import java.util.Optional;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import com.uxplima.uxmessentials.poses.adapter.inbound.SeatGeometry;
import com.uxplima.uxmessentials.poses.adapter.inbound.SeatGeometry.SeatTarget;
import com.uxplima.uxmessentials.poses.adapter.inbound.command.PoseCooldownNotice;
import com.uxplima.uxmessentials.poses.application.PosesMessageKey;
import com.uxplima.uxmessentials.poses.application.StartSit;
import com.uxplima.uxmessentials.poses.application.StartSit.SitOutcome;
import com.uxplima.uxmessentials.poses.application.port.SeatPort;
import com.uxplima.uxmessentials.poses.domain.SittableBlocks;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandFeedback;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.jspecify.annotations.NullMarked;

/**
 * Seats a player who right-clicks a sittable block with their main hand while not sneaking. The block must be a
 * configured sittable material, within {@code max-distance}, and not already carrying a seat; then {@link StartSit}
 * spawns the seat at the block (with its facing) and the interaction is cancelled so the right-click does nothing
 * else. A region veto is reported; a taken block reports it too. Sneaking is excluded so a player can still open a
 * stair-adjacent container or place a block.
 */
@NullMarked
public final class SeatInteractListener implements Listener {

    private final StartSit startSit;
    private final SeatPort seats;
    private final SittableBlocks sittableBlocks;
    private final CommandFeedback feedback;
    private final PoseCooldownNotice cooldownNotice;
    private final boolean sitOnBlocks;
    private final double maxDistance;

    public SeatInteractListener(
            StartSit startSit,
            SeatPort seats,
            SittableBlocks sittableBlocks,
            Messages messages,
            PoseCooldownNotice cooldownNotice,
            boolean sitOnBlocks,
            double maxDistance) {
        this.startSit = Objects.requireNonNull(startSit, "startSit");
        this.seats = Objects.requireNonNull(seats, "seats");
        this.sittableBlocks = Objects.requireNonNull(sittableBlocks, "sittableBlocks");
        this.feedback = new CommandFeedback(Objects.requireNonNull(messages, "messages"));
        this.cooldownNotice = Objects.requireNonNull(cooldownNotice, "cooldownNotice");
        this.sitOnBlocks = sitOnBlocks;
        this.maxDistance = maxDistance;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (!sitOnBlocks || event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        if (player.isSneaking() || block == null) {
            return;
        }
        Location location = Objects.requireNonNull(player.getLocation(), "player location");
        Optional<SeatTarget> target = SeatGeometry.forBlock(block, sittableBlocks, location.getYaw());
        if (target.isEmpty() || !withinReach(location, block)) {
            return;
        }
        seat(event, player, target.get());
    }

    private void seat(PlayerInteractEvent event, Player player, SeatTarget target) {
        Position seat = target.position();
        if (seats.isOccupied(seat)) {
            feedback.send(player, PosesMessageKey.POSES_SEAT_OCCUPIED);
            event.setCancelled(true);
            return;
        }
        PlayerRef who = BukkitRefs.toRef(player);
        SitOutcome outcome = startSit.start(who, seat, target.yaw(), false);
        switch (outcome) {
            case STARTED -> event.setCancelled(true);
            case DENIED_REGION -> feedback.send(player, PosesMessageKey.POSES_CANNOT_HERE);
            case ON_COOLDOWN -> cooldownNotice.send(player, who);
            // A player who is already posing, or with sitting switched off, gets no nag on a stray right-click.
            case ALREADY_POSING, DISABLED -> {}
        }
    }

    /** Whether {@code playerLocation} is within the configured seat reach of {@code block}'s centre. */
    private boolean withinReach(Location playerLocation, Block block) {
        Location centre = block.getLocation().add(0.5, 0.5, 0.5);
        return centre.distance(playerLocation) <= maxDistance;
    }
}
