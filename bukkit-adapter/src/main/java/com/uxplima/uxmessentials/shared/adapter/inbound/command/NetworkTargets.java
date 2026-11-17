package com.uxplima.uxmessentials.shared.adapter.inbound.command;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import org.bukkit.command.CommandSender;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.uxplima.uxmessentials.shared.application.port.NetworkPresence;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.OnlinePlayer;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.message.MessageKey;
import com.uxplima.uxmessentials.shared.domain.message.MessageLine;
import com.uxplima.uxmessentials.shared.domain.message.SharedMessageKey;
import com.uxplima.uxmlib.command.Sender;
import org.jspecify.annotations.NullMarked;

/**
 * Finds the account a command names, wherever on the network that player is: the name is settled off the tick
 * ({@link OffTickNames}), and a player online on another backend is answered with the server they are on, since what
 * this server holds of them is not what they have now.
 *
 * <p>A player the sender may not see (vanished, by the {@link PlayerArgument.Visibility} the vanish module binds) is
 * never located: the command treats
 * them as it treats a player who is offline, which is what a vanished player must look like.
 */
@NullMarked
public final class NetworkTargets {

    private final OffTickNames names;
    private final Scheduler scheduler;
    private final NetworkPresence presence;
    private final PlayerArgument.Visibility visibility;
    private final CommandFeedback feedback;

    public NetworkTargets(
            PlayerLookup lookup,
            Scheduler scheduler,
            NetworkPresence presence,
            PlayerArgument.Visibility visibility,
            CommandFeedback feedback) {
        this.feedback = Objects.requireNonNull(feedback, "feedback");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.names = new OffTickNames(lookup, scheduler, feedback);
        this.presence = Objects.requireNonNull(presence, "presence");
        this.visibility = Objects.requireNonNull(visibility, "visibility");
    }

    /**
     * Resolve {@code name} off the tick and run {@code then} on the async lane with the account, wherever the player
     * is. A name no account holds is answered with {@code unknown} on the sender's own thread.
     */
    public void resolve(CommandSender sender, String name, MessageKey unknown, Consumer<PlayerRef> then) {
        names.resolve(sender, name, unknown, then);
    }

    /**
     * Resolve {@code name} off the tick for a command that acts on what this server holds of the player. A player the
     * sender may locate on another backend is answered with the server they are on; otherwise {@code here} runs with
     * the account on the sender's own thread, the player online here or offline.
     */
    public void resolveHere(CommandSourceStack source, String name, MessageKey unknown, Consumer<PlayerRef> here) {
        Objects.requireNonNull(here, "here");
        CommandSender sender = Sender.audience(source);
        // Whether the sender may see the player reads their permissions, so it is asked on the sender's own thread.
        names.resolve(
                sender,
                name,
                unknown,
                target -> names.onSenderThread(
                        sender,
                        () -> elsewhere(source, target)
                                .ifPresentOrElse(line -> feedback.send(sender, line), () -> here.accept(target))));
    }

    /**
     * The line naming the other backend {@code target} is online on, when the sender may be told it. Call it on the
     * sender's own thread: whether they may see the player reads their permissions.
     */
    public Optional<MessageLine> elsewhere(CommandSourceStack source, PlayerRef target) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        return presence.find(target.uuid()).flatMap(online -> located(source, online));
    }

    /**
     * The line naming the other backend a player online under {@code name} is on, when the sender may be told it; for
     * a command that reads a live player and found nobody of that name here.
     */
    public Optional<MessageLine> elsewhere(CommandSourceStack source, String name) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(name, "name");
        return presence.matchByName(name)
                .player()
                .flatMap(ref -> presence.find(ref.uuid()))
                .flatMap(online -> located(source, online));
    }

    /** Run {@code task} where {@code sender} may be spoken to: a player's own region, or the global one. */
    public void onSenderThread(CommandSender sender, Runnable task) {
        names.onSenderThread(sender, task);
    }

    /**
     * Run {@code task} on the async lane, where {@link #resolve} runs its continuation: for a command whose work reads
     * the database whether it names the sender or somebody else.
     */
    public void async(Runnable task) {
        scheduler.async(Objects.requireNonNull(task, "task"));
    }

    private Optional<MessageLine> located(CommandSourceStack source, OnlinePlayer online) {
        if (online.server().equals(presence.localServer()) || !visibility.mayLocate(source, online.player())) {
            return Optional.empty();
        }
        return Optional.of(new MessageLine(
                SharedMessageKey.COMMAND_TARGET_ELSEWHERE,
                Map.of("player", online.player().name(), "server", online.server())));
    }
}
