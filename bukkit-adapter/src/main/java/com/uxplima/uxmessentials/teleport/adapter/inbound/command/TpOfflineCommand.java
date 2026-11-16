package com.uxplima.uxmessentials.teleport.adapter.inbound.command;

import java.util.Objects;

import org.bukkit.entity.Entity;
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
import com.uxplima.uxmessentials.teleport.domain.TeleportMessageKey;
import com.uxplima.uxmlib.command.Gate;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /tpoffline <player>} and {@code /tpofflinehere <player>}, for a player who is not on this server.
 * {@link Pull#GO} sends the invoking player to the position this server saved when that player left it;
 * {@link Pull#BRING} moves the player to where the invoking player stands once they next join this server, so the
 * invoking player must be standing on the ground. The saved data and the stored move are handled off the tick by
 * {@code OfflineTeleport}; this handler only reads the sender's own position, on the sender's thread.
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
            if (!standing(sender)) {
                // The spot is applied as it is, with no search for ground, so it must be one a player can stand on.
                services.notifier()
                        .send(ref(sender), TeleportMessageKey.TPOFFLINE_AIRBORNE, java.util.Map.of("player", name));
                return 0;
            }
            services.offlineTeleport().bring(ref(sender), name, TeleportRefs.positionOf(sender));
        }
        return Command.SINGLE_SUCCESS;
    }

    /** Whether {@code player} stands on something, rather than flying, gliding, falling or swimming. */
    private static boolean standing(Player player) {
        Entity body = player;
        return body.isOnGround() && !player.isFlying() && !player.isGliding();
    }
}
