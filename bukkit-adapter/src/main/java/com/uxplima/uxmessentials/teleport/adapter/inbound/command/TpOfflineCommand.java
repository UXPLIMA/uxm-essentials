package com.uxplima.uxmessentials.teleport.adapter.inbound.command;

import java.util.Objects;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.teleport.adapter.TeleportServices;
import com.uxplima.uxmessentials.teleport.application.TeleportMessageKey;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /tpoffline <player>} and {@code /tpofflinehere <player>}: teleport to (or fetch from) a player's
 * last-logout location. That location is read from the persisted last-location row owned by the
 * {@code playerstate} context, which lands in a later phase; until the row exists this verb has no source
 * to read and reports the destination as unresolved.
 *
 * <p><b>Documented stub:</b> the command, its permission gate, and its argument shape are real and wired
 * so the surface is complete and the operator docs match; only the persisted-location read is deferred.
 * When the last-location repository ships, this handler resolves the row and hands the position to the
 * executor exactly like {@link TpPosCommand}.
 */
@NullMarked
public final class TpOfflineCommand extends TeleportCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.tp.offline";

    private final String literal;
    private final String description;

    public TpOfflineCommand(TeleportServices services, Messages messages, String literal, String description) {
        super(services, messages);
        this.literal = Objects.requireNonNull(literal, "literal");
        this.description = Objects.requireNonNull(description, "description");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal(literal)
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(CommandSuggestions.playerArgument("player").executes(this::run))
                .build();
    }

    @Override
    public String description() {
        return description;
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        // The last-location row is not yet available (playerstate context, later phase); report the
        // logout point as unresolved rather than guessing a destination.
        services.notifier().send(ref(sender), TeleportMessageKey.SPAWN_UNRESOLVED);
        return 0;
    }
}
