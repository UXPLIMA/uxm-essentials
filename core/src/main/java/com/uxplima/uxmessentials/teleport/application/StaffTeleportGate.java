package com.uxplima.uxmessentials.teleport.application;

import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.teleport.application.port.TeleportFlags;
import com.uxplima.uxmessentials.teleport.domain.TeleportMessageKey;

/**
 * Whether a staff teleport ({@code /tp}, {@code /tphere}) may reach a player: a player who refuses teleports with
 * {@code /tptoggle} is left alone unless the verb overrides it ({@code /tpo}, {@code /tpohere}). The actor is told
 * which verb would get through, so a refusal is never a dead end.
 */
public final class StaffTeleportGate {

    private final TeleportFlags flags;
    private final Notifier notifier;

    public StaffTeleportGate(TeleportFlags flags, Notifier notifier) {
        this.flags = Objects.requireNonNull(flags, "flags");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /**
     * True when {@code actor} may teleport to or move {@code target}; otherwise the actor is told and this is false.
     * Read on the target's own thread, where its stored switch belongs.
     */
    public boolean admits(PlayerRef actor, PlayerRef target, boolean overridesToggle) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(target, "target");
        if (overridesToggle || target.equals(actor) || flags.acceptsRequests(target)) {
            return true;
        }
        notifier.send(actor, TeleportMessageKey.TP_TOGGLED_OFF, Map.of("player", target.name()));
        return false;
    }
}
