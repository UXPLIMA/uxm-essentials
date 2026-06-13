package com.uxplima.uxmessentials.vote.adapter.inbound.command;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandFeedback;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vote.adapter.VoteServices;
import com.uxplima.uxmessentials.vote.application.VoteMessageKey;
import com.uxplima.uxmessentials.vote.domain.Vote;
import com.uxplima.uxmessentials.vote.domain.VotePeriod;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * {@code /vote} ({@code uxmessentials.vote.use}): show the server's configured vote links to the invoking
 * player. Subcommands:
 *
 * <ul>
 *   <li>{@code testreward} — simulates a vote for the sender so an operator can verify rewards.
 *   <li>{@code total [player]} — show the sender's (or another player's) accumulated vote totals
 *       across all periods. Gated by {@code uxmessentials.vote.use}.
 *   <li>{@code top [daily|weekly|monthly|alltime]} — show the leaderboard for the given period
 *       (default {@code monthly}). Gated by {@code uxmessentials.vote.top}.
 *   <li>{@code admin givevote <player> [amount]} — inject synthetic votes for a player (offline-capable),
 *       gated by {@code uxmessentials.vote.admin}.
 *   <li>{@code admin reset <player>} — reset all vote totals for a player (offline-capable),
 *       gated by {@code uxmessentials.vote.admin}.
 * </ul>
 *
 * <p>A console source gets the players-only rejection for the base command; admin subcommands accept the
 * console. The {@code total}, {@code top}, and admin reads run off the tick thread so repository I/O stays
 * async.
 */
@NullMarked
public final class VoteCommand implements CommandRegistration {

    private static final String USE_PERMISSION = "uxmessentials.vote.use";
    private static final String TEST_PERMISSION = "uxmessentials.vote.testreward";
    private static final String TOP_PERMISSION = "uxmessentials.vote.top";
    private static final String ADMIN_PERMISSION = "uxmessentials.vote.admin";

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
                .then(Commands.literal("total")
                        .executes(this::totalSelf)
                        .then(CommandSuggestions.playerArgument("player").executes(this::totalOther)))
                .then(Commands.literal("top")
                        .requires(src -> src.getSender().hasPermission(TOP_PERMISSION))
                        .executes(this::topMonthly)
                        .then(Commands.literal("daily").executes(ctx -> topPeriod(ctx, VotePeriod.DAILY)))
                        .then(Commands.literal("weekly").executes(ctx -> topPeriod(ctx, VotePeriod.WEEKLY)))
                        .then(Commands.literal("monthly").executes(ctx -> topPeriod(ctx, VotePeriod.MONTHLY)))
                        .then(Commands.literal("alltime").executes(ctx -> topPeriod(ctx, VotePeriod.ALLTIME))))
                .then(Commands.literal("admin")
                        .requires(src -> src.getSender().hasPermission(ADMIN_PERMISSION))
                        .then(Commands.literal("givevote")
                                .then(CommandSuggestions.playerArgument("player")
                                        .executes(this::giveVoteDefault)
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                                .executes(this::giveVote))))
                        .then(Commands.literal("reset")
                                .then(CommandSuggestions.playerArgument("player")
                                        .executes(this::resetTotals))))
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

    private int totalSelf(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        PlayerRef who = BukkitRefs.toRef(sender);
        services.scheduler().async(() -> services.showVoteTotals().show(who, who));
        return Command.SINGLE_SUCCESS;
    }

    private int totalOther(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        PlayerRef viewer = BukkitRefs.toRef(sender);
        String name = ctx.getArgument("player", String.class);
        Optional<PlayerRef> target = services.playerLookup().findByName(name);
        if (target.isEmpty()) {
            feedback.send(sender, VoteMessageKey.VOTE_TOTAL_UNKNOWN, Map.of("player", name));
            return Command.SINGLE_SUCCESS;
        }
        PlayerRef resolved = target.get();
        services.scheduler().async(() -> services.showVoteTotals().show(viewer, resolved));
        return Command.SINGLE_SUCCESS;
    }

    private int topMonthly(CommandContext<CommandSourceStack> ctx) {
        return topPeriod(ctx, VotePeriod.MONTHLY);
    }

    private int topPeriod(CommandContext<CommandSourceStack> ctx, VotePeriod period) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        PlayerRef viewer = BukkitRefs.toRef(sender);
        // Resolve real names from the PlayerLookup; fall back to UUID string when profile is unknown.
        services.scheduler().async(() -> services.topVoters().top(viewer, period, uuid -> services.playerLookup()
                .findByUuid(uuid)
                .map(PlayerRef::name)
                .orElse(uuid.toString().toLowerCase(Locale.ROOT))));
        return Command.SINGLE_SUCCESS;
    }

    private int giveVoteDefault(CommandContext<CommandSourceStack> ctx) {
        return giveVoteImpl(ctx, 1);
    }

    private int giveVote(CommandContext<CommandSourceStack> ctx) {
        int amount = ctx.getArgument("amount", Integer.class);
        return giveVoteImpl(ctx, amount);
    }

    private int giveVoteImpl(CommandContext<CommandSourceStack> ctx, int amount) {
        CommandSender sender = ctx.getSource().getSender();
        PlayerRef actor = CommandFeedback.refOf(sender);
        String name = ctx.getArgument("player", String.class);
        Optional<PlayerRef> target = services.playerLookup().findByName(name);
        if (target.isEmpty()) {
            feedback.send(sender, VoteMessageKey.VOTE_TOTAL_UNKNOWN, Map.of("player", name));
            return Command.SINGLE_SUCCESS;
        }
        PlayerRef resolved = target.get();
        services.scheduler().async(() -> services.giveVote().give(actor, resolved, amount));
        return Command.SINGLE_SUCCESS;
    }

    private int resetTotals(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        PlayerRef actor = CommandFeedback.refOf(sender);
        String name = ctx.getArgument("player", String.class);
        Optional<PlayerRef> target = services.playerLookup().findByName(name);
        if (target.isEmpty()) {
            feedback.send(sender, VoteMessageKey.VOTE_TOTAL_UNKNOWN, Map.of("player", name));
            return Command.SINGLE_SUCCESS;
        }
        PlayerRef resolved = target.get();
        services.scheduler().async(() -> services.resetVoterTotals().reset(actor, resolved));
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
