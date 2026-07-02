package com.uxplima.uxmessentials.poses.adapter.inbound.listener;

import java.util.Objects;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

import com.uxplima.uxmessentials.poses.application.PosesMessageKey;
import com.uxplima.uxmessentials.poses.application.StartPlayerSit;
import com.uxplima.uxmessentials.poses.application.StartPlayerSit.PlayerSitOutcome;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandFeedback;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import org.jspecify.annotations.NullMarked;

/**
 * Seats a player who right-clicks another player with their main hand while not sneaking, gated by
 * {@code uxmessentials.playersit.use}. {@link StartPlayerSit} owns the feature switch, the one-session invariant,
 * and the target's opt-out; this handler only translates the click and renders the outcome, cancelling the
 * interaction when a sit begins so the right-click does nothing else. Sneaking is excluded so a player can still
 * open a trade or shake hands without being seated, and a target who refuses (or a self-click) is told why.
 */
@NullMarked
public final class PlayerSitInteractListener implements Listener {

    private static final String PERMISSION = "uxmessentials.playersit.use";

    private final StartPlayerSit startPlayerSit;
    private final CommandFeedback feedback;

    public PlayerSitInteractListener(StartPlayerSit startPlayerSit, Messages messages) {
        this.startPlayerSit = Objects.requireNonNull(startPlayerSit, "startPlayerSit");
        this.feedback = new CommandFeedback(Objects.requireNonNull(messages, "messages"));
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !(event.getRightClicked() instanceof Player target)) {
            return;
        }
        Player rider = event.getPlayer();
        if (rider.isSneaking() || !rider.hasPermission(PERMISSION)) {
            return;
        }
        seat(event, rider, target);
    }

    private void seat(PlayerInteractEntityEvent event, Player rider, Player target) {
        PlayerSitOutcome outcome = startPlayerSit.start(BukkitRefs.toRef(rider), BukkitRefs.toRef(target));
        switch (outcome) {
            case STARTED -> event.setCancelled(true);
            case DENIED_REGION -> feedback.send(rider, PosesMessageKey.POSES_CANNOT_HERE);
            case TARGET_REFUSES -> feedback.send(rider, PosesMessageKey.POSES_PLAYERSIT_TARGET_REFUSES);
            case SELF -> feedback.send(rider, PosesMessageKey.POSES_CANNOT_SIT_ON_SELF);
            // Already posing, or player-sit switched off: no nag on a stray right-click of a player.
            case ALREADY_POSING, DISABLED -> {}
        }
    }
}
