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
 * {@code /copyinv <player>}: copy a target's inventory contents into your own, a staff tool for
 * cloning a loadout. The target may be online here, offline, or on another server, which the command names
 * (else {@link ItemworldMessageKey#UNKNOWN_TARGET}).
 *
 * <p>An online target's inventory is snapshotted on the <em>target's</em> own region thread, an offline one's is read
 * from the data this server stored for them off the tick, and the copy is written to the actor's inventory on the
 * <em>actor's</em> region thread through the kernel {@code Scheduler}, so neither player's entity state is touched off
 * its owning region thread (Folia). The result is reported through {@link ItemworldMessageKey#COPYINV_DONE}.
 */
@NullMarked
public final class CopyInvCommand extends ItemworldCommandSupport implements CommandRegistration {

    private static final String PERMISSION = ItemworldPermissions.COPYINV_USE;

    public CopyInvCommand(ItemworldServices services) {
        super(services, "copyinv", SubFeatureGroup.ITEM_UTILS, "Copy a player's inventory into yours.");
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
                ContainerLayout.INVENTORY,
                Player::getInventory,
                ItemworldMessageKey.COPYINV_DONE);
        return Command.SINGLE_SUCCESS;
    }
}
