package com.uxplima.uxmessentials.teleport.adapter.inbound.command;

import java.util.Objects;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import com.uxplima.uxmessentials.shared.application.permission.TeleportPermissions;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.teleport.adapter.TeleportRefs;
import com.uxplima.uxmessentials.teleport.adapter.TeleportServices;
import com.uxplima.uxmlib.command.Gate;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /tpoffline <player>} and {@code /tpofflinehere <player>}: reach a player who is not on this server
 * through the position this server saved when they left it. {@link Pull#GO} sends the invoking player there;
 * {@link Pull#BRING} makes the player log in where the invoking player stands. The saved data is read and written
 * off the tick by {@code OfflineTeleport}; this handler only reads the sender's own position, on the sender's thread.
 */
@NullMarked
public final class TpOfflineCommand extends TeleportCommandSupport implements CommandRegistration {

    private static final String PERMISSION = TeleportPermissions.TP_OFFLINE;

    /** Whether the invoking player goes to the saved position ({@code GO}) or it is moved to them ({@code BRING}). */
    public enum Pull {
        GO,
        BRING
    }

    private final String literal;
    private final String description;
    private final Pull pull;

    public TpOfflineCommand(
            TeleportServices services, Messages messages, String literal, String description, Pull pull) {
        super(services, messages);
        this.literal = Objects.requireNonNull(literal, "literal");
        this.description = Objects.requireNonNull(description, "description");
        this.pull = Objects.requireNonNull(pull, "pull");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal(literal)
                .requires(Gate.permission(PERMISSION))
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
        String name = ctx.getArgument("player", String.class);
        if (pull == Pull.GO) {
            services.offlineTeleport().goTo(ref(sender), name);
        } else {
            services.offlineTeleport().bring(ref(sender), name, TeleportRefs.positionOf(sender));
        }
        return Command.SINGLE_SUCCESS;
    }
}
