package com.uxplima.uxmessentials.presence.application;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

import com.uxplima.uxmessentials.presence.application.port.NickRecords;
import com.uxplima.uxmessentials.presence.domain.PresenceMessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.PlayerNameRepository;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerName;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.message.SharedMessageKey;

/**
 * {@code /nick <name>} and {@code /nick <player> <name>}: set a player's display name. A nick is up to sixteen letters,
 * digits and underscores, or the player's own account name in any case, which keeps a Bedrock player's {@code .Name}
 * open to them. A nick another account holds as its name, in any case, on any server of the database, or as its nick,
 * is refused, so nobody can pass for another player; that is read off the tick. Setting your own nick confirms with
 * {@link PresenceMessageKey#NICK_SET}; setting another player's (gated by the adapter's others-node) confirms with
 * {@link PresenceMessageKey#NICK_SET_OTHER} so the actor sees whose name changed.
 */
public final class SetNick {

    private static final int MAX_LENGTH = 16;
    /** More accounts under one name than a server ever sees; one other is enough to refuse. */
    private static final int ACCOUNTS_READ = 8;

    private static final Pattern ALLOWED = Pattern.compile("[A-Za-z0-9_]+");

    private final PlayerNicks nicks;
    private final NickRecords records;
    private final PlayerNameRepository names;
    private final Scheduler scheduler;
    private final Notifier notifier;
    private final Logger log;

    public SetNick(
            PlayerNicks nicks,
            NickRecords records,
            PlayerNameRepository names,
            Scheduler scheduler,
            Notifier notifier,
            Logger log) {
        this.nicks = Objects.requireNonNull(nicks, "nicks");
        this.records = Objects.requireNonNull(records, "records");
        this.names = Objects.requireNonNull(names, "names");
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
        if (!isValid(trimmed, target)) {
            notifier.send(actor, PresenceMessageKey.NICK_INVALID);
            return;
        }
        scheduler.async(() -> {
            boolean taken;
            try {
                taken = takenByAnother(trimmed, target.uuid());
            } catch (RuntimeException unreachable) {
                log.warn(
                        "event=nick_check_failed player={} reason={}",
                        target.name(),
                        String.valueOf(unreachable.getMessage()));
                notifier.send(actor, SharedMessageKey.COMMAND_ERROR);
                return;
            }
            if (taken) {
                notifier.send(actor, PresenceMessageKey.NICK_TAKEN, Map.of("nick", trimmed));
                return;
            }
            nicks.set(target.uuid(), trimmed);
            confirm(actor, target, trimmed);
        });
    }

    /** Whether an account other than {@code owner} holds {@code nick} as its name or its nick, in any case. */
    private boolean takenByAnother(String nick, UUID owner) {
        for (PlayerName account : names.underName(nick, ACCOUNTS_READ)) {
            if (!account.uuid().equals(owner)) {
                return true;
            }
        }
        for (UUID holder : records.holders(nick)) {
            if (!holder.equals(owner)) {
                return true;
            }
        }
        return false;
    }

    private void confirm(PlayerRef actor, PlayerRef target, String nick) {
        if (actor.equals(target)) {
            notifier.send(actor, PresenceMessageKey.NICK_SET, Map.of("nick", nick));
            return;
        }
        notifier.send(actor, PresenceMessageKey.NICK_SET_OTHER, Map.of("player", target.name(), "nick", nick));
    }

    private static boolean isValid(String nick, PlayerRef target) {
        if (nick.isEmpty() || nick.length() > MAX_LENGTH) {
            return false;
        }
        return ALLOWED.matcher(nick).matches()
                || nick.toLowerCase(Locale.ROOT).equals(target.name().toLowerCase(Locale.ROOT));
    }
}
