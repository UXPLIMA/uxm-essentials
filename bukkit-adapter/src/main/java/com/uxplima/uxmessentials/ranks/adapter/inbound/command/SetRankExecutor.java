package com.uxplima.uxmessentials.ranks.adapter.inbound.command;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.uxplima.uxmessentials.ranks.application.SetRank;
import com.uxplima.uxmessentials.ranks.domain.Rank;
import com.uxplima.uxmessentials.ranks.domain.RankId;
import com.uxplima.uxmessentials.ranks.domain.RankLadder;
import com.uxplima.uxmessentials.ranks.domain.RanksMessageKey;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandFeedback;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.PlayerArgument;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.message.MessageKey;
import com.uxplima.uxmessentials.shared.domain.message.SharedMessageKey;
import com.uxplima.uxmlib.command.Args;
import com.uxplima.uxmlib.command.Sender;
import org.jspecify.annotations.NullMarked;

/**
 * The shared {@code setrank} argument subtree and handler that both the {@code /ranks setrank} subcommand and the
 * standalone {@code /setrank} command publish: an administrator sets a player's rank pointer directly through
 * {@link SetRank}, bypassing the requirements, cost and actions {@code /rankup} runs. The player argument is a
 * standard selector resolving to an online target; the rank argument completes against the ladder's rank ids and is
 * refused (an empty result) when it names no rung. Extracted so the two commands run one implementation rather than a
 * copy each, and so both resolve their feedback through the same {@link RanksMessageKey} lines.
 *
 * <p>The write reaches the database, so it runs on the async lane; the reply is sent back on the sender's thread
 * (the player's own, or the global region for the console).
 */
@NullMarked
final class SetRankExecutor {

    private final SetRank setRank;
    private final RankLadder ladder;
    private final Scheduler scheduler;
    private final Logger log;
    private final CommandFeedback feedback;

    SetRankExecutor(SetRank setRank, RankLadder ladder, Scheduler scheduler, Logger log, Messages messages) {
        this.setRank = Objects.requireNonNull(setRank, "setRank");
        this.ladder = Objects.requireNonNull(ladder, "ladder");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.log = Objects.requireNonNull(log, "log");
        this.feedback = new CommandFeedback(Objects.requireNonNull(messages, "messages"));
    }

    /** A fresh {@code <player> <rank>} argument subtree, wired with target/rank suggestions and the set executor. */
    ArgumentBuilder<CommandSourceStack, ?> arguments() {
        return Commands.argument("player", PlayerArgument.player())
                .suggests(CommandSuggestions.singlePlayerTarget())
                .then(Commands.argument("rank", Args.token())
                        .suggests(CommandSuggestions.fromStrings(this::rankIds))
                        .executes(this::run));
    }

    private List<String> rankIds() {
        return ladder.ranks().stream().map(rank -> rank.id().value()).toList();
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = Sender.audience(ctx.getSource());
        Optional<PlayerRef> target = resolveTarget(ctx, sender);
        if (target.isEmpty()) {
            return 0;
        }
        RankId rankId = RankId.of(ctx.getArgument("rank", String.class));
        if (!ladder.contains(rankId)) {
            feedback.send(sender, RanksMessageKey.RANKS_SETRANK_UNKNOWN_RANK, Map.of("rank", rankId.value()));
            return 0;
        }
        PlayerRef who = target.get();
        scheduler.async(() -> {
            try {
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
                log.error("failed to set the rank of player " + who.uuid(), failure);
                reply(sender, SharedMessageKey.COMMAND_ERROR, Map.of());
            }
        });
        return Command.SINGLE_SUCCESS;
    }

    /** Send {@code key} on the thread that owns {@code sender}: their own for a player, the global region otherwise. */
    private void reply(CommandSender sender, MessageKey key, Map<String, String> placeholders) {
        Runnable send = () -> feedback.send(sender, key, placeholders);
        if (sender instanceof Player player) {
            scheduler.onEntity(BukkitRefs.toRef(player), send);
        } else {
            scheduler.onGlobal(send);
        }
    }

    private Optional<PlayerRef> resolveTarget(CommandContext<CommandSourceStack> ctx, CommandSender sender) {
        try {
            PlayerSelectorArgumentResolver resolver = ctx.getArgument("player", PlayerSelectorArgumentResolver.class);
            List<Player> resolved = resolver.resolve(ctx.getSource());
            if (resolved.isEmpty()) {
                feedback.send(sender, PlayerArgument.miss(ctx, "player", SharedMessageKey.COMMAND_UNKNOWN_PLAYER));
                return Optional.empty();
            }
            return Optional.of(BukkitRefs.toRef(resolved.get(0)));
        } catch (CommandSyntaxException unmatched) {
            feedback.send(sender, PlayerArgument.miss(ctx, "player", SharedMessageKey.COMMAND_UNKNOWN_PLAYER));
            return Optional.empty();
        }
    }
}
