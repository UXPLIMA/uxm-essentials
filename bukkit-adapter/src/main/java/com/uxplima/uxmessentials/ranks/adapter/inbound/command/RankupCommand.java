package com.uxplima.uxmessentials.ranks.adapter.inbound.command;

import java.util.Map;
import java.util.Objects;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.ranks.application.Rankup;
import com.uxplima.uxmessentials.ranks.application.RankupResult;
import com.uxplima.uxmessentials.ranks.domain.Rank;
import com.uxplima.uxmessentials.ranks.domain.RanksMessageKey;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandFeedback;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.message.TrustedMarkup;
import com.uxplima.uxmessentials.shared.application.permission.EconomyPermissions;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.message.SharedMessageKey;
import com.uxplima.uxmlib.command.Gate;
import com.uxplima.uxmlib.command.Sender;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /rankup}: advance the invoking player one rung up the ladder. The handler resolves nothing but the
 * player's own identity and hands it to {@link Rankup}, which owns the requirement / cost / action pipeline, saves
 * on the async lane and answers with a typed {@link RankupResult} on the player's thread; this class only maps that
 * outcome to the matching catalog line, the success line carrying the new rank's display name as its {@code {rank}}
 * placeholder. Gated on
 * {@code uxmessentials.ranks.rankup}, which ships {@code true} so every player may try to progress.
 */
@NullMarked
public final class RankupCommand implements CommandRegistration {

    /** The permission every player holds by default so they can climb their own ladder. */
    public static final String PERMISSION = EconomyPermissions.RANKS_RANKUP;

    private final Rankup rankup;
    private final CommandFeedback feedback;

    public RankupCommand(Rankup rankup, Messages messages) {
        this.rankup = Objects.requireNonNull(rankup, "rankup");
        this.feedback = new CommandFeedback(Objects.requireNonNull(messages, "messages"));
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("rankup")
                .requires(Gate.permission(PERMISSION))
                .executes(this::run)
                .build();
    }

    @Override
    public String description() {
        return "/rankup to advance to the next rank when you meet its requirements.";
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = Sender.audience(ctx.getSource());
        if (!(sender instanceof Player player)) {
            feedback.send(sender, SharedMessageKey.COMMAND_PLAYERS_ONLY);
            return 0;
        }
        PlayerRef who = BukkitRefs.toRef(player);
        var unused = rankup.rankUp(who).thenAccept(result -> {
            // A player who left mid-save is answered off their thread; there is nobody to tell.
            if (player.isOnline()) {
                render(player, result);
            }
        });
        return Command.SINGLE_SUCCESS;
    }

    private void render(Player player, RankupResult result) {
        switch (result.status()) {
            case RANKED_UP ->
                feedback.send(player, RanksMessageKey.RANKS_RANKUP_SUCCESS, Map.of("rank", displayName(result)));
            case ALREADY_MAX -> feedback.send(player, RanksMessageKey.RANKS_RANKUP_MAX);
            case REQUIREMENTS_NOT_MET -> feedback.send(player, RanksMessageKey.RANKS_RANKUP_REQUIREMENTS);
            case CANNOT_AFFORD -> feedback.send(player, RanksMessageKey.RANKS_RANKUP_COST);
            case BUSY -> feedback.send(player, RanksMessageKey.RANKS_RANKUP_BUSY);
            case FAILED -> feedback.send(player, RanksMessageKey.RANKS_RANKUP_FAILED);
        }
    }

    private static String displayName(RankupResult result) {
        return TrustedMarkup.of(result.newRank().map(Rank::displayName).orElseThrow());
    }
}
