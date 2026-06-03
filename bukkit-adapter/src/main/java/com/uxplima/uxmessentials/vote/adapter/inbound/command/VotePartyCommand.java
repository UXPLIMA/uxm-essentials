package com.uxplima.uxmessentials.vote.adapter.inbound.command;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import net.kyori.adventure.text.minimessage.MiniMessage;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vote.adapter.VoteServices;
import com.uxplima.uxmessentials.vote.application.VoteMessageKey;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * {@code /voteparty} ({@code uxmessentials.voteparty.use}): show the invoking player the progress towards the
 * next vote party — the accumulated count, the configured threshold, and how many votes remain. The count is
 * read from the durable repository through {@link com.uxplima.uxmessentials.vote.application.VotePartyStatus},
 * so the read runs off the tick thread via the {@link Scheduler} port's async seam; the reply hops back to the
 * viewer through the message sink inside the use case.
 *
 * <p>A console source gets the players-only rejection — the status line is delivered to a live player.
 */
@NullMarked
public final class VotePartyCommand implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.voteparty.use";

    private final VoteServices services;

    public VotePartyCommand(VoteServices services) {
        this.services = Objects.requireNonNull(services, "services");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("voteparty")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::run)
                .build();
    }

    @Override
    public String description() {
        return "Show progress towards the next vote party.";
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        PlayerRef who = BukkitRefs.toRef(sender);
        services.scheduler().async(() -> services.votePartyStatus().show(who));
        return Command.SINGLE_SUCCESS;
    }

    private @Nullable Player player(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (sender instanceof Player player) {
            return player;
        }
        sender.sendMessage(MiniMessage.miniMessage()
                .deserialize(
                        services.messages().resolve(consoleRef(sender), VoteMessageKey.VOTE_PLAYERS_ONLY, Map.of())));
        return null;
    }

    private static PlayerRef consoleRef(CommandSender sender) {
        return new PlayerRef(new UUID(0L, 0L), sender.getName());
    }
}
