package com.uxplima.uxmessentials.ranks.adapter.inbound.command;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.ranks.application.SetRank;
import com.uxplima.uxmessentials.ranks.domain.RankLadder;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /setrank <player> <rank>}: the top-level alias administrators reach for, running the same direct rank set as
 * the {@code /ranks setrank} subcommand through the shared {@link SetRankExecutor} (and so the same {@link SetRank}
 * use case, bypassing the requirements, cost and actions a {@code /rankup} runs). It is a distinct command id
 * ({@code setrank}, separate from {@code ranks}) so an operator can rename or disable it on its own through
 * {@code commands.conf}, and gates on the same {@code uxmessentials.ranks.admin} node as {@code /ranks setrank}, so it
 * stays hidden from a non-admin exactly as the subcommand does.
 */
@NullMarked
public final class SetRankCommand implements CommandRegistration {

    /** The permission an administrator holds to set a player's rank directly; shared with {@code /ranks setrank}. */
    public static final String PERMISSION = RanksCommand.PERMISSION;

    private final SetRankExecutor setRank;

    public SetRankCommand(SetRank setRank, RankLadder ladder, Messages messages) {
        this.setRank = new SetRankExecutor(setRank, ladder, messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("setrank")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(setRank.arguments())
                .build();
    }

    @Override
    public String description() {
        return "/setrank <player> <rank> to set a player's rank directly.";
    }
}
