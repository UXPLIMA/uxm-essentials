package com.uxplima.uxmessentials.ranks.adapter.inbound.command;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.uxplima.uxmessentials.ranks.application.SetRank;
import com.uxplima.uxmessentials.ranks.domain.Rank;
import com.uxplima.uxmessentials.ranks.domain.RankId;
import com.uxplima.uxmessentials.ranks.domain.RankLadder;
import com.uxplima.uxmessentials.ranks.domain.RanksMessageKey;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandFeedback;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.message.TrustedMarkup;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.NameMatch;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.message.MessageKey;
import com.uxplima.uxmessentials.shared.domain.message.MessageLine;
import com.uxplima.uxmessentials.shared.domain.message.SharedMessageKey;
import com.uxplima.uxmlib.command.Args;
import com.uxplima.uxmlib.command.Sender;
import org.jspecify.annotations.NullMarked;

/**
 * The shared {@code setrank} argument subtree and handler that both the {@code /ranks setrank} subcommand and the
 * standalone {@code /setrank} command publish: an administrator sets a player's rank pointer directly through
 * {@link SetRank}, bypassing the requirements, cost and actions {@code /rankup} runs. The player argument is a name,
 * completed from the players online here and resolved through {@link PlayerLookup}, so it reaches a player who is
 * offline or on another server of the network as well, Bedrock names included; a name that only differs in case from
 * several accounts is refused with the spellings to choose from. The rank argument completes against the ladder's
 * rank ids and is refused (an empty result) when it names no rung. Extracted so the two commands run one
 * implementation rather than a copy each, and so both resolve their feedback through the same {@link RanksMessageKey}
 * lines.
 *
 * <p>The name lookup and the write may reach the database, so both run on the async lane; the reply is sent back on
 * the sender's thread (the player's own, or the global region for the console). A player on another server picks the
 * new rank up from the rank change the save announces.
 */
@NullMarked
final class SetRankExecutor {

    private static final String TARGETS = "targets";
    private static final String PLAYER = "player";
    private static final String RANK = "rank";

    private final SetRank setRank;
    private final RankLadder ladder;
    private final PlayerLookup players;
    private final Scheduler scheduler;
    private final Logger log;
    private final CommandFeedback feedback;

    SetRankExecutor(
            SetRank setRank,
            RankLadder ladder,
            PlayerLookup players,
            Scheduler scheduler,
            Logger log,
            Messages messages) {
        this.setRank = Objects.requireNonNull(setRank, "setRank");
        this.ladder = Objects.requireNonNull(ladder, "ladder");
        this.players = Objects.requireNonNull(players, "players");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.log = Objects.requireNonNull(log, "log");
        this.feedback = new CommandFeedback(Objects.requireNonNull(messages, "messages"));
    }

    /**
     * The {@code <player> <rank>} argument subtrees. A selector ({@code @p}, {@code @a[tag=vip]}) is read by the
     * vanilla parser and resolved the usual way, so a command block keeps working; it names the players online here.
     * A name goes to {@link PlayerLookup} instead, whichever branch read it: the vanilla parser also reads a plain
     * name, but answers only for a player online here and settles case on its own. A name the vanilla parser
     * refuses, such as a Bedrock name with a {@code #} prefix, is read by the second branch.
     */
    LiteralArgumentBuilder<CommandSourceStack> under(LiteralArgumentBuilder<CommandSourceStack> literal) {
        return literal.then(Commands.argument(TARGETS, ArgumentTypes.players())
                        .suggests(CommandSuggestions.onlinePlayers())
                        .then(rankArgument(this::runSelected)))
                .then(CommandSuggestions.playerArgument(PLAYER).then(rankArgument(this::runNamed)));
    }

    private ArgumentBuilder<CommandSourceStack, ?> rankArgument(Command<CommandSourceStack> run) {
        return Commands.argument(RANK, Args.token())
                .suggests(CommandSuggestions.fromStrings(this::rankIds))
                .executes(run);
    }

    private List<String> rankIds() {
        return ladder.ranks().stream().map(rank -> rank.id().value()).toList();
    }

    /** The selector branch: a selector resolves to the players online here, a plain name goes to the lookup. */
    private int runSelected(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String typed = raw(ctx, TARGETS);
        if (!typed.startsWith("@")) {
            return runByName(ctx, typed);
        }
        CommandSender sender = Sender.audience(ctx.getSource());
        Optional<RankId> rankId = rankOf(ctx, sender);
        if (rankId.isEmpty()) {
            return 0;
        }
        List<Player> selected =
                ctx.getArgument(TARGETS, PlayerSelectorArgumentResolver.class).resolve(ctx.getSource());
        if (selected.isEmpty()) {
            feedback.send(sender, SharedMessageKey.COMMAND_UNKNOWN_PLAYER, Map.of("player", typed));
            return 0;
        }
        for (Player player : selected) {
            PlayerRef who = BukkitRefs.toRef(player);
            scheduler.async(() -> set(sender, who, rankId.get()));
        }
        return Command.SINGLE_SUCCESS;
    }

    /** The name branch, for a name the vanilla parser refuses. */
    private int runNamed(CommandContext<CommandSourceStack> ctx) {
        return runByName(ctx, ctx.getArgument(PLAYER, String.class));
    }

    private int runByName(CommandContext<CommandSourceStack> ctx, String name) {
        CommandSender sender = Sender.audience(ctx.getSource());
        Optional<RankId> rankId = rankOf(ctx, sender);
        if (rankId.isEmpty()) {
            return 0;
        }
        scheduler.async(() -> {
            NameMatch match;
            try {
                match = players.matchByName(name);
            } catch (RuntimeException failure) {
                log.error("failed to look up player " + name + " to set their rank", failure);
                reply(sender, SharedMessageKey.COMMAND_ERROR, Map.of());
                return;
            }
            Optional<PlayerRef> target = match.player();
            if (target.isEmpty()) {
                reply(sender, match.miss(SharedMessageKey.COMMAND_UNKNOWN_PLAYER, Map.of("player", name)));
                return;
            }
            set(sender, target.get(), rankId.get());
        });
        return Command.SINGLE_SUCCESS;
    }

    /** The rank argument, or empty after telling the sender when it names no rung. */
    private Optional<RankId> rankOf(CommandContext<CommandSourceStack> ctx, CommandSender sender) {
        RankId rankId = RankId.of(ctx.getArgument(RANK, String.class));
        if (!ladder.contains(rankId)) {
            feedback.send(sender, RanksMessageKey.RANKS_SETRANK_UNKNOWN_RANK, Map.of("rank", rankId.value()));
            return Optional.empty();
        }
        return Optional.of(rankId);
    }

    /** On the async lane: write the rank and tell the sender. */
    private void set(CommandSender sender, PlayerRef who, RankId rankId) {
        try {
            Optional<Rank> set = setRank.setRank(who.uuid(), rankId);
            if (set.isEmpty()) {
                reply(sender, RanksMessageKey.RANKS_SETRANK_UNKNOWN_RANK, Map.of("rank", rankId.value()));
                return;
            }
            reply(
                    sender,
                    RanksMessageKey.RANKS_SETRANK_SUCCESS,
                    Map.of(
                            "player",
                            who.name(),
                            "rank",
                            TrustedMarkup.of(set.get().displayName())));
        } catch (RuntimeException failure) {
            log.error("failed to set the rank of player " + who.uuid(), failure);
            reply(sender, SharedMessageKey.COMMAND_ERROR, Map.of());
        }
    }

    /** The text the sender typed for argument {@code name}. */
    private static String raw(CommandContext<CommandSourceStack> ctx, String name) {
        for (ParsedCommandNode<CommandSourceStack> node : ctx.getNodes()) {
            if (node.getNode().getName().equals(name)) {
                return node.getRange().get(ctx.getInput());
            }
        }
        throw new IllegalStateException("no argument " + name + " in " + ctx.getInput());
    }

    private void reply(CommandSender sender, MessageKey key, Map<String, String> placeholders) {
        reply(sender, new MessageLine(key, placeholders));
    }

    /** Send {@code line} on the thread that owns {@code sender}: the player's own, or the global region. */
    private void reply(CommandSender sender, MessageLine line) {
        Runnable send = () -> feedback.send(sender, line);
        if (sender instanceof Player player) {
            scheduler.onEntity(BukkitRefs.toRef(player), send);
        } else {
            scheduler.onGlobal(send);
        }
    }
}
