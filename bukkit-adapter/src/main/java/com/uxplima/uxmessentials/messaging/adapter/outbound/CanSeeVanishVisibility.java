package com.uxplima.uxmessentials.messaging.adapter.outbound;

import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.messaging.application.port.VanishVisibility;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link VanishVisibility} implementation, soft-coupled to the presence context through Bukkit's own
 * {@code Player#canSee} visibility graph — the same seam the teleport context's {@code /tpa} uses. When the
 * presence module is active it hides a vanished player from those who lack the vanish-see node by removing
 * the entity from their view, which {@code canSee} reflects; when presence is disabled nothing is hidden, so
 * {@code canSee} is always true and a target is never resolved as hidden. This is why the binding degrades
 * to "fully visible" with presence off without any messaging-side branch.
 *
 * <p>A target who is not online cannot be seen and is treated as not-hidden here: offline resolution is the
 * caller's job (the command lookup), and reporting an offline player as "hidden" would conflate two
 * outcomes. This adapter answers only the vanish question for an online target.
 */
@NullMarked
public final class CanSeeVanishVisibility implements VanishVisibility {

    @Override
    public boolean isHiddenFrom(PlayerRef viewer, PlayerRef target) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(target, "target");
        Player viewerPlayer = Bukkit.getPlayer(viewer.uuid());
        Player targetPlayer = Bukkit.getPlayer(target.uuid());
        if (viewerPlayer == null || targetPlayer == null) {
            return false;
        }
        return !viewerPlayer.canSee(targetPlayer);
    }
}
