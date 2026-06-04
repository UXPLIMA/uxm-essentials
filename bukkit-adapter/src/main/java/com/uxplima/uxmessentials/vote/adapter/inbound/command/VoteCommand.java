package com.uxplima.uxmessentials.vote.adapter.inbound.command;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandFeedback;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vote.adapter.VoteServices;
import com.uxplima.uxmessentials.vote.application.VoteMessageKey;
import com.uxplima.uxmessentials.vote.domain.Vote;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * {@code /vote} ({@code uxmessentials.vote.use}): show the server's configured vote links to the invoking
 * player. Its {@code testreward} subcommand ({@code uxmessentials.vote.testreward}) simulates a vote for the
 * sender — it runs the full {@link com.uxplima.uxmessentials.vote.application.HandleVote} path so an operator
 * can verify the configured rewards, party counter, and broadcast without an upstream Votifier vote — and
 * confirms with {@link VoteMessageKey#VOTE_TESTREWARD}.
 *
 * <p>A console source gets the players-only rejection: the links display and the test reward are both bound to
 * a live player. The test reward runs off-thread so the persistence and dispatch stay off the tick thread.
 */
@NullMarked
public final class VoteCommand implements CommandRegistration {

    private static final String USE_PERMISSION = "uxmessentials.vote.use";
    private static final String TEST_PERMISSION = "uxmessentials.vote.testreward";

    private final VoteServices services;
    private final CommandFeedback feedback;

    public VoteCommand(VoteServices services) {
        this.services = Objects.requireNonNull(services, "services");
        this.feedback = new CommandFeedback(services.messages());
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("vote")
                .requires(src -> src.getSender().hasPermission(USE_PERMISSION))
                .executes(this::showLinks)
                .then(Commands.literal("testreward")
                        .requires(src -> src.getSender().hasPermission(TEST_PERMISSION))
                        .executes(this::testReward))
                .build();
    }

    @Override
    public String description() {
        return "Show the server's vote links.";
    }

    private int showLinks(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        services.voteLinks().show(BukkitRefs.toRef(sender));
        return Command.SINGLE_SUCCESS;
    }

    private int testReward(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        PlayerRef who = BukkitRefs.toRef(sender);
        services.scheduler().async(() -> services.handleVote().handle(new Vote(who, "test", Instant.now())));
        feedback.send(sender, VoteMessageKey.VOTE_TESTREWARD, Map.of());
        return Command.SINGLE_SUCCESS;
    }

    private @Nullable Player player(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (sender instanceof Player player) {
            return player;
        }
        feedback.send(sender, VoteMessageKey.VOTE_PLAYERS_ONLY, Map.of());
        return null;
    }
}
