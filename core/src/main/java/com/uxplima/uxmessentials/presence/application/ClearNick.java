package com.uxplima.uxmessentials.presence.application;

import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.presence.application.port.NickWrite;
import com.uxplima.uxmessentials.presence.domain.PresenceMessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.message.SharedMessageKey;

/**
 * {@code /nick off}: clear a player's display name and restore their account name, on every backend. Confirms with
 * {@link PresenceMessageKey#NICK_CLEARED} once the database holds no nick for them. Clearing is idempotent: clearing a
 * player who has no nick confirms too, so the command never has to branch on whether a nick was set.
 */
public final class ClearNick {

    private final PlayerNicks nicks;
    private final Scheduler scheduler;
    private final Notifier notifier;
    private final Logger log;

    public ClearNick(PlayerNicks nicks, Scheduler scheduler, Notifier notifier, Logger log) {
        this.nicks = Objects.requireNonNull(nicks, "nicks");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.log = Objects.requireNonNull(log, "log");
    }

    /** Clear {@code who}'s nick and confirm to {@code actor}. */
    public void clear(PlayerRef actor, PlayerRef who) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(who, "who");
        scheduler.async(() -> {
            NickWrite outcome;
            try {
                outcome = nicks.clear(who.uuid());
            } catch (RuntimeException unreachable) {
                log.warn(
                        "event=nick_write_failed player={} reason={}",
                        who.name(),
                        String.valueOf(unreachable.getMessage()));
                notifier.send(actor, SharedMessageKey.COMMAND_ERROR);
                return;
            }
            if (outcome instanceof NickWrite.Written) {
                notifier.send(actor, PresenceMessageKey.NICK_CLEARED);
            } else {
                notifier.send(actor, PresenceMessageKey.NICK_CONFLICT, Map.of("player", who.name()));
            }
        });
    }

    /** Clear {@code who}'s own nick and confirm to them. */
    public void clear(PlayerRef who) {
        clear(who, who);
    }
}
