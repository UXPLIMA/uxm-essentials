package com.uxplima.uxmessentials.security.adapter;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.security.domain.OperatorList;
import com.uxplima.uxmessentials.security.domain.SecurityMessageKey;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandFeedback;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyledText;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.NameCase;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Holds the server's operators to the {@code allowed-operators} list. An operator who is not listed loses operator and
 * is sent away, whether they join that way or are made one while online; an {@code /op} naming somebody who is not
 * listed is refused, from a player or from the console. Reading the command text cannot catch every route to operator
 * ({@code /execute ... run op}, an alias, a plugin calling {@code setOp}), so the online operators are also checked one
 * tick after any command runs and every few seconds besides. The list is swapped whole on reload.
 *
 * <p>On an offline-mode server a listed name matches only as spelled ({@link OperatorList}), and the server's user
 * cache, which matches any case, may vouch for a uuid only when the name it holds is the one typed. An {@code /op}
 * whose name an online player has only in another case is refused whether or not the list is on: the server finds
 * the player by name in any case, so it would make that other account the operator. The name case is asked on every
 * check, since a server can turn out to be offline-mode while it runs.
 */
@NullMarked
public final class OperatorLock {

    /** How often the online operators are checked against the list. */
    private static final Duration SWEEP_PERIOD = Duration.ofSeconds(5);

    /** How long after a command the online operators are checked: one tick, once the command has run. */
    private static final Duration AFTER_COMMAND = Duration.ofMillis(50);

    private final Server server;
    private final Scheduler scheduler;
    private final Messages messages;
    private final MessageSink sink;
    private final Logger log;
    private final Supplier<NameCase> names;
    private volatile OperatorList list;

    public OperatorLock(
            Server server,
            Scheduler scheduler,
            Messages messages,
            MessageSink sink,
            Logger log,
            Supplier<NameCase> names,
            OperatorList list) {
        this.server = Objects.requireNonNull(server, "server");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.sink = Objects.requireNonNull(sink, "sink");
        this.log = Objects.requireNonNull(log, "log");
        this.names = Objects.requireNonNull(names, "names");
        this.list = Objects.requireNonNull(list, "list");
    }

    /** Put {@code next} in force. */
    public void replace(OperatorList next) {
        this.list = Objects.requireNonNull(next, "next");
    }

    /**
     * Take operator from {@code player} and send them away when the list does not name them. Runs on the player's own
     * thread. Returns whether the player was sent away.
     */
    public boolean enforce(Player player) {
        OperatorList current = list;
        if (!current.enabled()
                || !player.isOp()
                || current.permits(player.getUniqueId(), player.getName(), names.get())) {
            return false;
        }
        player.setOp(false);
        log.warn(
                "event=operator_not_allowed player={} uuid={} action=deop_kick",
                player.getName(),
                player.getUniqueId());
        PlayerRef who = BukkitRefs.toRef(player);
        player.kick(
                StyledText.render(messages.resolve(who, SecurityMessageKey.SECURITY_OPERATORS_NOT_ALLOWED, Map.of())));
        return true;
    }

    /**
     * Whether {@code command}, as typed by {@code sender}, is an {@code /op} the list refuses; when it is, the sender is
     * told why. A selector such as {@code @a} is refused outright, since it can name anybody.
     */
    public boolean refuses(CommandSender sender, String command) {
        Optional<String> target = opTarget(command);
        if (target.isEmpty()) {
            return false;
        }
        PlayerRef who = CommandFeedback.refOf(sender);
        Optional<String> otherSpelling = onlineInAnotherCase(target.get());
        if (otherSpelling.isPresent()) {
            sink.deliver(
                    who,
                    messages.resolve(
                            who,
                            SecurityMessageKey.SECURITY_OPERATORS_OTHER_SPELLING,
                            Map.of("player", target.get(), "online", otherSpelling.get())));
            return true;
        }
        OperatorList current = list;
        if (!current.enabled() || (!target.get().startsWith("@") && permitted(current, target.get()))) {
            return false;
        }
        sink.deliver(
                who,
                messages.resolve(who, SecurityMessageKey.SECURITY_OPERATORS_REFUSED, Map.of("player", target.get())));
        return true;
    }

    /** Check every online operator against the list every few seconds; the handle stops the sweep. */
    public AutoCloseable startSweep() {
        return scheduler.repeatGlobal(this::sweep, SWEEP_PERIOD, SWEEP_PERIOD);
    }

    /** Check the online operators once the command running now has finished, so whatever it did is caught at once. */
    public void sweepSoon() {
        if (list.enabled()) {
            scheduler.laterGlobal(AFTER_COMMAND, this::sweep);
        }
    }

    private void sweep() {
        if (!list.enabled()) {
            return;
        }
        for (Player player : server.getOnlinePlayers()) {
            if (player.isOp()) {
                scheduler.onEntity(BukkitRefs.toRef(player), () -> enforce(player));
            }
        }
    }

    /**
     * On an offline-mode server, the spelling of the online player the server's own {@code /op} would pick for
     * {@code name} when it is not the one typed. The server looks a name up in any case, so it would make that other
     * account the operator.
     */
    private Optional<String> onlineInAnotherCase(String name) {
        if (name.startsWith("@") || names.get() != NameCase.SENSITIVE) {
            return Optional.empty();
        }
        Player online = server.getPlayerExact(name);
        return online == null || online.getName().equals(name) ? Optional.empty() : Optional.of(online.getName());
    }

    private boolean permitted(OperatorList current, String name) {
        NameCase nameCase = names.get();
        OfflinePlayer known = server.getOfflinePlayerIfCached(name);
        @Nullable UUID id = known == null ? null : known.getUniqueId();
        if (nameCase == NameCase.SENSITIVE && known != null && !name.equals(known.getName())) {
            // The cache matched another spelling, which is another account here.
            id = null;
        }
        return current.permits(id, name, nameCase);
    }

    /** The player an {@code /op} or {@code /namespace:op} command names, or empty for any other command. */
    static Optional<String> opTarget(String command) {
        String body = command.strip();
        if (body.startsWith("/")) {
            body = body.substring(1);
        }
        String[] words = body.split("\\s+", 3);
        if (words.length < 2) {
            return Optional.empty();
        }
        String root = words[0].toLowerCase(Locale.ROOT);
        int colon = root.indexOf(':');
        if (!(colon < 0 ? root : root.substring(colon + 1)).equals("op")) {
            return Optional.empty();
        }
        return Optional.of(words[1]);
    }
}
