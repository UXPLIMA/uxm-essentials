package com.uxplima.uxmessentials.villagers.adapter.inbound.command;

import java.util.Objects;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
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
 * {@code /villager}: the villagers context's root command. Its subcommands act on the villager the player is looking at
 * (or the nearest one within reach): {@code manager} opens the trade manager (Phase 2, gated on
 * {@code uxmessentials.villagers.manager}) and {@code protect} toggles the villager's protection mark (Phase 3, gated on
 * {@code uxmessentials.villagers.protect}). Each subcommand is present only when its feature is wired, so the command
 * carries exactly the verbs the operator enabled; a sender who is not a player, or who is not looking at a villager,
 * gets a feedback line instead. The target is resolved on the Brigadier (global) thread and the actual entity work is
 * hopped to the villager's region by the collaborator it is handed to.
 */
@NullMarked
public final class VillagerCommand implements CommandRegistration {

    /** How far the player's line of sight (and the nearest-villager fallback) reaches for a target, in blocks. */
    private static final int REACH = 5;

    private final @Nullable String managerPermission;
    private final @Nullable VillagerManagerView managerView;
    private final @Nullable String protectPermission;
    private final @Nullable VillagerProtectToggle protectToggle;
    private final CommandFeedback feedback;

    public VillagerCommand(
            @Nullable String managerPermission,
            @Nullable VillagerManagerView managerView,
            @Nullable String protectPermission,
            @Nullable VillagerProtectToggle protectToggle,
            Messages messages) {
        this.managerPermission = managerPermission;
        this.managerView = managerView;
        this.protectPermission = protectPermission;
        this.protectToggle = protectToggle;
        this.feedback = new CommandFeedback(Objects.requireNonNull(messages, "messages"));
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("villager");
        if (managerView != null && managerPermission != null) {
            String permission = managerPermission;
            root.then(Commands.literal("manager")
                    .requires(src -> src.getSender().hasPermission(permission))
                    .executes(this::openManager));
        }
        if (protectToggle != null && protectPermission != null) {
            String permission = protectPermission;
            root.then(Commands.literal("protect")
                    .requires(src -> src.getSender().hasPermission(permission))
                    .executes(this::toggleProtect));
        }
        return root.build();
    }

    @Override
    public String description() {
        return "Manage the villager you are looking at (/villager manager, /villager protect).";
    }

    private int openManager(CommandContext<CommandSourceStack> ctx) {
        VillagerManagerView view = Objects.requireNonNull(managerView, "managerView");
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
        view.open(player, BukkitRefs.toRef(player), target);
        return Command.SINGLE_SUCCESS;
    }

    private int toggleProtect(CommandContext<CommandSourceStack> ctx) {
        VillagerProtectToggle toggle = Objects.requireNonNull(protectToggle, "protectToggle");
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            feedback.send(sender, SharedMessageKey.COMMAND_PLAYERS_ONLY);
            return 0;
        }
        Villager target = targetVillager(player);
        if (target == null) {
            feedback.send(player, VillagersMessageKey.VILLAGERS_PROTECT_NO_TARGET);
            return Command.SINGLE_SUCCESS;
        }
        toggle.toggle(player, BukkitRefs.toRef(player), target);
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
