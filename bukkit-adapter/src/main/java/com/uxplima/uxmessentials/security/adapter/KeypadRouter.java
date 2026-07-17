package com.uxplima.uxmessentials.security.adapter;

import java.util.Objects;

import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.security.adapter.inbound.gui.KeypadActions;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Routes a keypad submission to whichever verification flow the player is currently in. One keypad view serves both
 * the join freeze and the op-command re-auth prompt, so this thin dispatcher checks the transient re-auth
 * {@link ReauthSessions} first — a re-auth is only ever open when the player is not join-frozen, so the two never
 * overlap — and falls back to the join {@link VerificationController} otherwise. Keeping the routing here means the
 * keypad GUI plumbing stays unchanged from Phase 2 and knows nothing about which flow it is serving.
 */
@NullMarked
public final class KeypadRouter implements KeypadActions {

    private final ReauthSessions reauthSessions;
    private final KeypadActions joinActions;
    private final KeypadActions reauthActions;

    public KeypadRouter(ReauthSessions reauthSessions, KeypadActions joinActions, KeypadActions reauthActions) {
        this.reauthSessions = Objects.requireNonNull(reauthSessions, "reauthSessions");
        this.joinActions = Objects.requireNonNull(joinActions, "joinActions");
        this.reauthActions = Objects.requireNonNull(reauthActions, "reauthActions");
    }

    @Override
    public void submit(Player player, PlayerRef viewer, String candidate) {
        actionsFor(viewer).submit(player, viewer, candidate);
    }

    @Override
    public void requestTotp(Player player, PlayerRef viewer) {
        actionsFor(viewer).requestTotp(player, viewer);
    }

    private KeypadActions actionsFor(PlayerRef viewer) {
        return reauthSessions.isPending(viewer.uuid()) ? reauthActions : joinActions;
    }
}
