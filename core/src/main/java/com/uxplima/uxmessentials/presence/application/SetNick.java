package com.uxplima.uxmessentials.presence.application;

import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.presence.application.port.NickWrite;
import com.uxplima.uxmessentials.presence.domain.NickRules;
import com.uxplima.uxmessentials.presence.domain.PresenceMessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.message.SharedMessageKey;

/**
 * {@code /nick <name>} and {@code /nick <player> <name>}: set a player's display name. The shape is checked at once
 * ({@link NickRules}); whether another account holds the nick as its name or its nick, in any case, on any server of
 * the database, is settled off the tick, where the nick is claimed in the same step, so nobody can pass for another
 * player. Setting your own nick confirms with {@link PresenceMessageKey#NICK_SET}; setting another player's (gated by
 * the adapter's others-node) confirms with {@link PresenceMessageKey#NICK_SET_OTHER} so the actor sees whose name
 * changed. Nothing is confirmed that was not stored.
 */
public final class SetNick {

    private final PlayerNicks nicks;
    private final Scheduler scheduler;
    private final Notifier notifier;
    private final Logger log;

    public SetNick(PlayerNicks nicks, Scheduler scheduler, Notifier notifier, Logger log) {
        this.nicks = Objects.requireNonNull(nicks, "nicks");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.log = Objects.requireNonNull(log, "log");
    }

    /** Set {@code target}'s nick to {@code nick} at {@code actor}'s request, once it is found to be theirs to take. */
    public void set(PlayerRef actor, PlayerRef target, String nick) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(nick, "nick");
        String trimmed = nick.strip();
        if (!NickRules.allowed(trimmed, target.name())) {
            notifier.send(actor, PresenceMessageKey.NICK_INVALID);
            return;
        }
        scheduler.async(() -> {
            NickWrite outcome;
            try {
                outcome = nicks.claim(target.uuid(), trimmed);
            } catch (RuntimeException unreachable) {
                log.warn(
                        "event=nick_write_failed player={} reason={}",
                        target.name(),
                        String.valueOf(unreachable.getMessage()));
                notifier.send(actor, SharedMessageKey.COMMAND_ERROR);
                return;
            }
            switch (outcome) {
                case NickWrite.Written written -> confirm(actor, target, trimmed);
                case NickWrite.Taken taken ->
                    notifier.send(actor, PresenceMessageKey.NICK_TAKEN, Map.of("nick", trimmed));
                case NickWrite.Stale stale ->
                    notifier.send(actor, PresenceMessageKey.NICK_CONFLICT, Map.of("player", target.name()));
            }
        });
    }

    private void confirm(PlayerRef actor, PlayerRef target, String nick) {
        if (actor.equals(target)) {
            notifier.send(actor, PresenceMessageKey.NICK_SET, Map.of("nick", nick));
            return;
        }
        notifier.send(actor, PresenceMessageKey.NICK_SET_OTHER, Map.of("player", target.name(), "nick", nick));
    }
}
