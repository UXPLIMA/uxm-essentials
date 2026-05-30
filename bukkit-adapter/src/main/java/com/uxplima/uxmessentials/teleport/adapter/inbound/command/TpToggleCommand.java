package com.uxplima.uxmessentials.teleport.adapter.inbound.command;

import java.util.List;

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
 * {@code /tptoggle} (alias {@code /toggletp}): flip the invoking player's un-teleportable switch and
 * confirm the new state. The flag itself is owned by the {@code TeleportFlags} port; this command flips
 * it and renders the matching on/off confirmation.
 */
@NullMarked
public final class TpToggleCommand extends TeleportCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.tpa.toggle";

    public TpToggleCommand(TeleportServices services, Messages messages) {
        super(services, messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("tptoggle")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::run)
                .build();
    }

    @Override
    public String description() {
        return "Refuse all incoming teleport requests.";
    }

    @Override
    public List<String> aliases() {
        return List.of("toggletp");
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        PlayerRef ref = ref(sender);
        boolean nowAccepting = services.flags().toggleRequests(ref);
        TeleportMessageKey key = nowAccepting ? TeleportMessageKey.TPA_TOGGLE_ON : TeleportMessageKey.TPA_TOGGLE_OFF;
        services.notifier().send(ref, key);
        return Command.SINGLE_SUCCESS;
    }
}
