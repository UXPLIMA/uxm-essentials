package com.uxplima.uxmessentials.shared.adapter.inbound.command;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.NameMatch;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.message.MessageKey;
import org.jspecify.annotations.NullMarked;

/**
 * Turns a typed player name into an account on the async lane, for a command whose target may be offline. A name the
 * plugin's index does not hold in memory is settled from the database or the server's player data, and neither is
 * read on a tick thread; on the tick such a name would answer unknown.
 */
@NullMarked
public final class OffTickNames {

    private final PlayerLookup lookup;
    private final Scheduler scheduler;
    private final CommandFeedback feedback;

    public OffTickNames(PlayerLookup lookup, Scheduler scheduler, CommandFeedback feedback) {
        this.lookup = Objects.requireNonNull(lookup, "lookup");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.feedback = Objects.requireNonNull(feedback, "feedback");
    }

    /**
     * Resolve {@code name} on the async lane and run {@code then} there with the account. A name no account holds, or
     * several hold only in case, is answered with {@code unknown} (its {@code <player>} the typed name) on the
     * sender's own thread.
     */
    public void resolve(CommandSender sender, String name, MessageKey unknown, Consumer<PlayerRef> then) {
        Objects.requireNonNull(unknown, "unknown");
        resolve(sender, name, match -> feedback.send(sender, match.miss(unknown, Map.of("player", name))), then);
    }

    /**
     * Resolve {@code name} on the async lane and run {@code then} there with the account; a name that is not one
     * account's runs {@code miss} with the match on the sender's own thread.
     */
    public void resolve(CommandSender sender, String name, Consumer<NameMatch> miss, Consumer<PlayerRef> then) {
        Objects.requireNonNull(sender, "sender");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(miss, "miss");
        Objects.requireNonNull(then, "then");
        scheduler.async(() -> {
            NameMatch match = lookup.matchByName(name);
            Optional<PlayerRef> target = match.player();
            if (target.isPresent()) {
                then.accept(target.get());
                return;
            }
            onSenderThread(sender, () -> miss.accept(match));
        });
    }

    /** Run {@code task} where {@code sender} may be spoken to: a player's own region, or the global one. */
    public void onSenderThread(CommandSender sender, Runnable task) {
        if (sender instanceof Player player) {
            scheduler.onEntity(BukkitRefs.toRef(player), task);
        } else {
            scheduler.onGlobal(task);
        }
    }
}
