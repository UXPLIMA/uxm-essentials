package com.uxplima.uxmessentials.teleport.adapter.inbound.command;

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
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.teleport.adapter.TeleportServices;
import com.uxplima.uxmessentials.teleport.application.TeleportMessageKey;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /tpblock <player>} and {@code /tpunblock <player>}: add or remove a requester from the invoking
 * player's block list. The target is resolved by name (it may be offline). The block list is session
 * state owned by the {@code TeleportFlags} port; this command mutates it and confirms.
 */
@NullMarked
public final class TpBlockCommand extends TeleportCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.tpa.block";

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
        PlayerRef actor = ref(sender);
        String name = ctx.getArgument("player", String.class);
        Optional<PlayerRef> target = services.players().findOnlineByName(name);
        if (target.isEmpty()) {
            services.notifier().send(actor, TeleportMessageKey.TPA_TARGET_OFFLINE, java.util.Map.of("player", name));
            return 0;
        }
        apply(actor, target.get());
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
