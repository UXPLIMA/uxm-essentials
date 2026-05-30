package com.uxplima.uxmessentials.teleport.adapter.inbound.command;

import java.util.List;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.teleport.adapter.TeleportServices;
import com.uxplima.uxmessentials.teleport.application.TeleportMessageKey;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /rtp} (alias {@code /wild}): served O(1) from the pre-warmed per-world safe-location queue. The
 * command reads the player's current world, sends the searching notice, and hands the background path to
 * {@link com.uxplima.uxmessentials.teleport.application.ResolveRtp}; the requester never waits on a chunk
 * load — the queue is polled and a refill is fired asynchronously.
 */
@NullMarked
public final class RtpCommand extends TeleportCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.rtp.use";

    public RtpCommand(TeleportServices services, Messages messages) {
        super(services, messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("rtp")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::run)
                .build();
    }

    @Override
    public String description() {
        return "Randomly teleport within the world.";
    }

    @Override
    public List<String> aliases() {
        return List.of("wild");
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        PlayerRef who = ref(sender);
        WorldRef world = BukkitRefs.toRef(sender.getWorld());
        services.notifier().send(who, TeleportMessageKey.RTP_SEARCHING);
        services.resolveRtp().background(who, world);
        return Command.SINGLE_SUCCESS;
    }
}
