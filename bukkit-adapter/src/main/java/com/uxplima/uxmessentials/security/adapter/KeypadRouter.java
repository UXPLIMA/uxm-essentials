package com.uxplima.uxmessentials.security.adapter;

import java.util.Objects;

import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.security.adapter.inbound.gui.KeypadActions;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Routes a keypad submission to whichever flow the player is currently in. One keypad view serves three: creating a
 * PIN the server requires, proving a factor on join, and proving one again for a protected command. This thin
 * dispatcher picks between them by asking which transient session the player holds, so the keypad GUI plumbing knows
 * nothing about which flow it is serving.
 *
 * <p>The order matters and reflects which state can coexist. Creating a PIN is checked first because a player at the
 * create pad is also join-frozen (the freeze is what holds them there), so testing the freeze first would send their
 * new PIN to be verified against the factor they do not have yet. It only counts while the freeze that holds them
 * there is on: a create-a-PIN session outliving its freeze would otherwise take a later verification's entries as a
 * new PIN. The join freeze comes before a re-auth because the
 * two can overlap: an operator can force a player back into verification while a re-auth prompt is still open, and the
 * entry then answers the freeze. Sent to the re-auth instead, it would run the command and leave the player frozen
 * with nothing asking them for anything.
 */
@NullMarked
public final class KeypadRouter implements KeypadActions {

    private final ReauthSessions reauthSessions;
    private final PinEnrolmentSessions enrolmentSessions;
    private final VerificationSessions freezes;
    private final KeypadActions joinActions;
    private final KeypadActions reauthActions;
    private final KeypadActions enrolmentActions;

    public KeypadRouter(
            ReauthSessions reauthSessions,
            PinEnrolmentSessions enrolmentSessions,
            VerificationSessions freezes,
            KeypadActions joinActions,
            KeypadActions reauthActions,
            KeypadActions enrolmentActions) {
        this.reauthSessions = Objects.requireNonNull(reauthSessions, "reauthSessions");
        this.enrolmentSessions = Objects.requireNonNull(enrolmentSessions, "enrolmentSessions");
        this.freezes = Objects.requireNonNull(freezes, "freezes");
        this.joinActions = Objects.requireNonNull(joinActions, "joinActions");
        this.reauthActions = Objects.requireNonNull(reauthActions, "reauthActions");
        this.enrolmentActions = Objects.requireNonNull(enrolmentActions, "enrolmentActions");
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
        if (enrolmentSessions.isPending(viewer.uuid()) && freezes.isPending(viewer.uuid())) {
            return enrolmentActions;
        }
        if (freezes.isPending(viewer.uuid())) {
            return joinActions;
        }
        return reauthSessions.isPending(viewer.uuid()) ? reauthActions : joinActions;
    }
}
