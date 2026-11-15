package com.uxplima.uxmessentials.teleport.application;

import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.application.hop.HopDeparture;
import com.uxplima.uxmessentials.shared.application.hop.HopKind;
import com.uxplima.uxmessentials.shared.application.hop.port.HopGate;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.teleport.domain.Destination;
import com.uxplima.uxmessentials.teleport.domain.TeleportKind;

/**
 * The {@link HopGate} the teleport context provides: a cross-server hop passes the same jail, combat-tag and
 * cooldown checks as the local teleport of its kind, through {@link TeleportEngine#gateHop}, so the cooldown key
 * the origin checks is the one the target's landing stamps.
 */
public final class TeleportHopGate implements HopGate {

    private final TeleportEngine engine;

    public TeleportHopGate(TeleportEngine engine) {
        this.engine = Objects.requireNonNull(engine, "engine");
    }

    @Override
    public boolean admits(PlayerRef who, HopDeparture departure) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(departure, "departure");
        if (departure.kind() == HopKind.PLAYER_REQUEST) {
            return engine.gateAcceptedRequest(who).isOk();
        }
        Optional<TeleportKind> kind = teleportKindOf(departure.kind());
        if (kind.isEmpty()) {
            return true;
        }
        Destination destination = Destination.at(departure.destination(), Optional.empty(), departure.cooldown());
        return engine.gateHop(who, destination, kind.get()).isOk();
    }

    /**
     * The local teleport each hop kind is ruled as. A jail hop is not the player's choice, so nothing a player does
     * (being jailed, tagged or on cooldown) may stop it, and it is not gated at all. A staff teleport is not gated
     * either, as a local staff {@code /tp} is not: staff may move a jailed or tagged player. An accepted request is
     * gated by jail and combat only, as a local accepted request is, and so is answered before this mapping.
     */
    static Optional<TeleportKind> teleportKindOf(HopKind kind) {
        return switch (kind) {
            case WARP, PLAYER_WARP -> Optional.of(TeleportKind.WARP);
            case HOME -> Optional.of(TeleportKind.HOME);
            case WORLD -> Optional.of(TeleportKind.SPAWN);
            case PLAYER_REQUEST -> Optional.of(TeleportKind.REQUEST);
            case PLAYER_STAFF, JAIL -> Optional.empty();
            // Never sent by this release; read only from a row a newer one wrote.
            case UNKNOWN -> Optional.empty();
        };
    }
}
