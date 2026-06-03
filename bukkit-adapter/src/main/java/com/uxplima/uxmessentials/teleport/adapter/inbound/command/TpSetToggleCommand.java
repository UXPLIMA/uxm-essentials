package com.uxplima.uxmessentials.teleport.adapter.inbound.command;

import java.util.Objects;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.teleport.adapter.TeleportServices;
import com.uxplima.uxmessentials.teleport.application.TeleportMessageKey;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /tpon} and {@code /tpoff}: the explicit, idempotent on/off counterparts to the flip-style
 * {@code /tptoggle}. Running {@code /tpon} twice keeps requests on rather than flipping back off, which is
 * what players coming from AdvancedTeleport and EssentialsX expect. The state is owned by the
 * {@code TeleportFlags} port; this command sets it and renders the matching on/off confirmation.
 */
@NullMarked
public final class TpSetToggleCommand extends TeleportCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.tpa.toggle";

    private final String literal;
    private final boolean accepting;

    public TpSetToggleCommand(TeleportServices services, Messages messages, String literal, boolean accepting) {
        super(services, messages);
        this.literal = Objects.requireNonNull(literal, "literal");
        this.accepting = accepting;
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal(literal)
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::run)
                .build();
    }

    @Override
    public String description() {
        return accepting ? "Accept incoming teleport requests." : "Refuse incoming teleport requests.";
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        PlayerRef ref = ref(sender);
        services.flags().setAcceptsRequests(ref, accepting);
        TeleportMessageKey key = accepting ? TeleportMessageKey.TPA_TOGGLE_ON : TeleportMessageKey.TPA_TOGGLE_OFF;
        services.notifier().send(ref, key);
        return Command.SINGLE_SUCCESS;
    }
}
