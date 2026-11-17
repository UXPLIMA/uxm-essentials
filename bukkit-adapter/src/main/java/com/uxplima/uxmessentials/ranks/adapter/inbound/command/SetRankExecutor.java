package com.uxplima.uxmessentials.ranks.adapter.inbound.command;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.uxplima.uxmessentials.ranks.application.SetRank;
import com.uxplima.uxmessentials.ranks.domain.Rank;
import com.uxplima.uxmessentials.ranks.domain.RankId;
import com.uxplima.uxmessentials.ranks.domain.RankLadder;
import com.uxplima.uxmessentials.ranks.domain.RanksMessageKey;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandFeedback;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
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

    /** A fresh {@code <player> <rank>} argument subtree, wired with target/rank suggestions and the set executor. */
    ArgumentBuilder<CommandSourceStack, ?> arguments() {
        return CommandSuggestions.playerArgument("player")
                .then(Commands.argument("rank", Args.token())
                        .suggests(CommandSuggestions.fromStrings(this::rankIds))
                        .executes(this::run));
    }

    private List<String> rankIds() {
        return ladder.ranks().stream().map(rank -> rank.id().value()).toList();
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = Sender.audience(ctx.getSource());
        String name = ctx.getArgument("player", String.class);
        RankId rankId = RankId.of(ctx.getArgument("rank", String.class));
        if (!ladder.contains(rankId)) {
            feedback.send(sender, RanksMessageKey.RANKS_SETRANK_UNKNOWN_RANK, Map.of("rank", rankId.value()));
            return 0;
        }
        scheduler.async(() -> {
            try {
                NameMatch match = players.matchByName(name);
                Optional<PlayerRef> target = match.player();
                if (target.isEmpty()) {
                    reply(sender, match.miss(SharedMessageKey.COMMAND_UNKNOWN_PLAYER, Map.of("player", name)));
                    return;
                }
                PlayerRef who = target.get();
                Optional<Rank> set = setRank.setRank(who.uuid(), rankId);
                if (set.isEmpty()) {
                    reply(sender, RanksMessageKey.RANKS_SETRANK_UNKNOWN_RANK, Map.of("rank", rankId.value()));
                    return;
                }
                reply(
                        sender,
                        RanksMessageKey.RANKS_SETRANK_SUCCESS,
                        Map.of("player", who.name(), "rank", set.get().displayName()));
            } catch (RuntimeException failure) {
                log.error("failed to set the rank of player " + name, failure);
                reply(sender, SharedMessageKey.COMMAND_ERROR, Map.of());
            }
        });
        return Command.SINGLE_SUCCESS;
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
