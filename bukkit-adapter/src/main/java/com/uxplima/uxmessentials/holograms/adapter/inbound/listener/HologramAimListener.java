package com.uxplima.uxmessentials.holograms.adapter.inbound.listener;

import java.util.Objects;
import java.util.Optional;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import com.uxplima.uxmessentials.holograms.adapter.outbound.PacketHologramView;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * A click on a hologram from further off than a client reaches.
 *
 * <p>A client sends a click on an entity only within its reach, three blocks in survival or adventure, and a board is
 * read from further away than that. Every click still reaches the server as an interaction, so the server asks what
 * the look was aimed at and answers it as a click on the hologram's box. A click that lands on a hologram uses
 * nothing else: the item in hand is not used and the block is not touched, so a menu item in hand does not open its
 * menu as well. Runs first, so the plugins after it see the click already taken.
 *
 * <p>A click on a block the client could reach is the block's when the block stands in front of the hologram. A wall
 * beyond the client's reach is not looked for: that would read the world along the look, which on a region threaded
 * server may cross into a region this thread does not own.
 */
@NullMarked
public final class HologramAimListener implements Listener {

    /** What a look is aimed at, within a reach in blocks. */
    @FunctionalInterface
    public interface Aim {
        Optional<PacketHologramView.Aimed> at(Player player, double reach);
    }

    private final Aim aim;
    private final PacketHologramView.ClickHandler onClick;
    private final double reach;

    /** @param reach how far off a hologram answers a click, in blocks; nothing or less turns the aim off */
    public HologramAimListener(Aim aim, PacketHologramView.ClickHandler onClick, double reach) {
        this.aim = Objects.requireNonNull(aim, "aim");
        this.onClick = Objects.requireNonNull(onClick, "onClick");
        this.reach = reach;
    }

    /** Not ignoring cancelled: the server fires a click in the air already cancelled. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (reach <= 0 || action == Action.PHYSICAL) {
            return;
        }
        Player player = event.getPlayer();
        Optional<PacketHologramView.Aimed> aimed = aim.at(player, reach);
        if (aimed.isEmpty()
                || blockInFront(player, event.getClickedBlock(), aimed.get().distance())) {
            return;
        }
        event.setUseItemInHand(Event.Result.DENY);
        event.setUseInteractedBlock(Event.Result.DENY);
        onClick.clicked(player, aimed.get().name(), action.isLeftClick());
    }

    private boolean blockInFront(Player player, @Nullable Block clicked, double hologram) {
        if (clicked == null) {
            return false;
        }
        Location eye = player.getEyeLocation();
        Vector from = eye.toVector();
        // The whole block rather than its shape: the question is only which of the two stands nearer.
        @Nullable RayTraceResult met = BoundingBox.of(clicked).rayTrace(from, eye.getDirection(), reach);
        return met != null && met.getHitPosition().distance(from) < hologram;
    }
}
