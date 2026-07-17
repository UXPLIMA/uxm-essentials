package com.uxplima.uxmessentials.villagers.adapter.inbound.command;

import java.util.Objects;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandFeedback;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.message.SharedMessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.villagers.adapter.inbound.gui.VillagerManagerView;
import com.uxplima.uxmessentials.villagers.application.VillagersMessageKey;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * {@code /villager manager}: the villagers context's root command, whose one subcommand opens the trade manager on the
 * villager the player is looking at (or the nearest one within reach). Gated on {@code uxmessentials.villagers.manager}.
 * The command resolves the target on the Brigadier (global) thread and hands it to the {@link VillagerManagerView},
 * which schedules the actual window open on the editor's entity thread; a sender who is not a player, or who is not
 * looking at a villager, gets a feedback line instead.
 */
@NullMarked
public final class VillagerCommand implements CommandRegistration {

    /** How far the player's line of sight (and the nearest-villager fallback) reaches for a target, in blocks. */
    private static final int REACH = 5;

    private final String managerPermission;
    private final VillagerManagerView managerView;
    private final CommandFeedback feedback;

    public VillagerCommand(String managerPermission, VillagerManagerView managerView, Messages messages) {
        this.managerPermission = Objects.requireNonNull(managerPermission, "managerPermission");
        this.managerView = Objects.requireNonNull(managerView, "managerView");
        this.feedback = new CommandFeedback(Objects.requireNonNull(messages, "messages"));
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("villager")
                .then(Commands.literal("manager")
                        .requires(src -> src.getSender().hasPermission(managerPermission))
                        .executes(this::openManager))
                .build();
    }

    @Override
    public String description() {
        return "Manage the trades of the villager you are looking at (/villager manager).";
    }

    private int openManager(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            feedback.send(sender, SharedMessageKey.COMMAND_PLAYERS_ONLY);
            return 0;
        }
        Villager target = targetVillager(player);
        if (target == null) {
            feedback.send(player, VillagersMessageKey.VILLAGERS_MANAGER_NO_TARGET);
            return Command.SINGLE_SUCCESS;
        }
        managerView.open(player, BukkitRefs.toRef(player), target);
        return Command.SINGLE_SUCCESS;
    }

    /** The villager the player looks at, else the nearest villager within reach, else {@code null}. */
    private static @Nullable Villager targetVillager(Player player) {
        Entity looked = player.getTargetEntity(REACH);
        if (looked instanceof Villager villager) {
            return villager;
        }
        Villager nearest = null;
        double nearestDistanceSquared = Double.MAX_VALUE;
        for (Entity nearby : player.getNearbyEntities(REACH, REACH, REACH)) {
            if (nearby instanceof Villager villager) {
                double distanceSquared = villager.getLocation().distanceSquared(player.getLocation());
                if (distanceSquared < nearestDistanceSquared) {
                    nearestDistanceSquared = distanceSquared;
                    nearest = villager;
                }
            }
        }
        return nearest;
    }
}
