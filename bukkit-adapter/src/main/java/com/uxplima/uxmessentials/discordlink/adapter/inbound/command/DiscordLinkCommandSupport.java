package com.uxplima.uxmessentials.discordlink.adapter.inbound.command;

import java.util.Objects;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.context.CommandContext;
import com.uxplima.uxmessentials.discordlink.adapter.DiscordLinkServices;
import com.uxplima.uxmessentials.discordlink.application.DiscordlinkMessageKey;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Shared collaborators the discord-link Brigadier commands hold: the constructed {@link DiscordLinkServices}.
 * Concrete command classes extend this so each stays focused on building its node and mapping one path to one
 * use-case call. A console invoking a player-only command is told so through the context's notifier, in the
 * sender's locale, before the handler exits.
 */
@NullMarked
abstract class DiscordLinkCommandSupport {

    final DiscordLinkServices services;

    DiscordLinkCommandSupport(DiscordLinkServices services) {
        this.services = Objects.requireNonNull(services, "services");
    }

    /** The invoking player, or {@code null} (after sending the players-only reply) for a console source. */
    final @Nullable Player player(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (sender instanceof Player player) {
            return player;
        }
        services.notifier().send(consoleRef(sender), DiscordlinkMessageKey.DISCORD_LINK_PLAYERS_ONLY);
        return null;
    }

    /** A {@link PlayerRef} for the live player. */
    static PlayerRef ref(Player player) {
        return BukkitRefs.toRef(player);
    }

    private static PlayerRef consoleRef(CommandSender sender) {
        return PlayerRef.system(sender.getName());
    }
}
