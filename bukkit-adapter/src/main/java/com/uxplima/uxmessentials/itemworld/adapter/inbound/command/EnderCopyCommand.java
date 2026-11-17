package com.uxplima.uxmessentials.itemworld.adapter.inbound.command;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.itemworld.adapter.ItemworldServices;
import com.uxplima.uxmessentials.itemworld.domain.ItemworldMessageKey;
import com.uxplima.uxmessentials.itemworld.domain.SubFeatureGroup;
import com.uxplima.uxmessentials.playerstate.adapter.outbound.playerdata.ContainerLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import com.uxplima.uxmessentials.shared.application.permission.ItemworldPermissions;
import com.uxplima.uxmlib.command.Gate;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /endercopy <player>}: copy a target's ender-chest contents into your own, the ender-chest
 * sibling of {@code /copyinv}. The target may be online here, offline, or on another server, which the command names
 * (else {@link ItemworldMessageKey#UNKNOWN_TARGET}).
 *
 * <p>An online target's ender chest is snapshotted on the <em>target's</em> own region thread, an offline one's is read
 * from the data this server stored for them off the tick, and the copy is written to the actor's ender chest on the
 * <em>actor's</em> region thread through the kernel {@code Scheduler}, so neither player's entity state is touched off
 * its owning region thread (Folia). The result is reported through {@link ItemworldMessageKey#ENDERCOPY_DONE}.
 */
@NullMarked
public final class EnderCopyCommand extends ItemworldCommandSupport implements CommandRegistration {

    private static final String PERMISSION = ItemworldPermissions.ENDERCOPY_USE;

    public EnderCopyCommand(ItemworldServices services) {
        super(services, "endercopy", SubFeatureGroup.ITEM_UTILS, "Copy a player's ender chest into yours.");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal(literal())
                .requires(Gate.permission(PERMISSION))
                .then(CommandSuggestions.playerArgument("player").executes(this::run))
                .build();
    }

    @Override
    public String description() {
        return describe();
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        if (!enabled(ctx)) {
            return Command.SINGLE_SUCCESS;
        }
        Player self = player(ctx);
        if (self == null) {
            return Command.SINGLE_SUCCESS;
        }
        copyContainer(
                ctx,
                self,
                StringArgumentType.getString(ctx, "player"),
                ContainerLayout.ENDER,
                Player::getEnderChest,
                ItemworldMessageKey.ENDERCOPY_DONE);
        return Command.SINGLE_SUCCESS;
    }
}
