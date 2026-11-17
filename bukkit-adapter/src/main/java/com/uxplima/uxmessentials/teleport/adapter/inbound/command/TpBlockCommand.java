package com.uxplima.uxmessentials.teleport.adapter.inbound.command;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

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
import com.uxplima.uxmessentials.shared.domain.NameMatch;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.message.SharedMessageKey;
import com.uxplima.uxmessentials.teleport.adapter.TeleportServices;
import com.uxplima.uxmessentials.teleport.domain.TeleportMessageKey;
import com.uxplima.uxmlib.command.Gate;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /tpblock <player>} and {@code /tpunblock <player>}: add or remove a requester from the invoking
 * player's block list. The target is resolved by name (it may be offline). The block list is session
 * state owned by the {@code TeleportFlags} port; this command mutates it and confirms.
 */
@NullMarked
public final class TpBlockCommand extends TeleportCommandSupport implements CommandRegistration {

    private static final String PERMISSION = TeleportPermissions.TPA_BLOCK;

    private final String literal;
    private final String description;
    private final boolean blocking;

    public TpBlockCommand(
            TeleportServices services, Messages messages, String literal, String description, boolean blocking) {
        super(services, messages);
        this.literal = Objects.requireNonNull(literal, "literal");
        this.description = Objects.requireNonNull(description, "description");
        this.blocking = blocking;
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
        PlayerRef actor = ref(sender);
        String name = ctx.getArgument("player", String.class);
        // A block is kept by account, so the player need not be here: one who logged off can be unblocked and one on
        // another server blocked. The name may need the stored name list, which is read off the tick.
        services.scheduler().async(() -> {
            NameMatch match = services.players().matchByName(name);
            Optional<PlayerRef> target = match.player();
            if (target.isEmpty()) {
                services.notifier()
                        .send(actor, match.miss(SharedMessageKey.COMMAND_UNKNOWN_PLAYER, Map.of("player", name)));
                return;
            }
            apply(actor, target.get());
        });
        return Command.SINGLE_SUCCESS;
    }

    private void apply(PlayerRef actor, PlayerRef target) {
        if (blocking) {
            services.flags().block(actor, target);
            services.notifier().send(actor, TeleportMessageKey.TPA_TOGGLE_OFF);
        } else {
            services.flags().unblock(actor, target);
            services.notifier().send(actor, TeleportMessageKey.TPA_TOGGLE_ON);
        }
    }
}
