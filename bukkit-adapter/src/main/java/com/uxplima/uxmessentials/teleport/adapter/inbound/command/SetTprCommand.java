package com.uxplima.uxmessentials.teleport.adapter.inbound.command;

import java.util.Map;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.teleport.adapter.TeleportServices;
import com.uxplima.uxmessentials.teleport.adapter.outbound.RtpWorldSettings;
import com.uxplima.uxmessentials.teleport.application.TeleportMessageKey;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /settpr <minRange> <maxRange>} ({@code uxmessentials.teleport.settpr}): set the random-teleport search
 * zone the running {@code /rtp} uses, without an edit-and-reload round trip. The radii are swapped into the
 * live {@code PrewarmedSafeLocationQueue} through its atomic settings reference (the config-swap pattern), and
 * the pre-warmed queue is dropped so the next refill validates candidates against the new zone. The search
 * centre is the world border centre, as it is for the configured zone, so this sets the band rather than a
 * point. An invalid band ({@code maxRange < minRange}) is rejected with no change.
 */
@NullMarked
public final class SetTprCommand extends TeleportCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.teleport.settpr";

    public SetTprCommand(TeleportServices services, Messages messages) {
        super(services, messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("settpr")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(Commands.argument("minRange", DoubleArgumentType.doubleArg(0))
                        .then(Commands.argument("maxRange", DoubleArgumentType.doubleArg(1))
                                .executes(this::set)))
                .build();
    }

    @Override
    public String description() {
        return "Set the random-teleport zone for the running /rtp.";
    }

    private int set(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        PlayerRef who = ref(sender);
        double min = ctx.getArgument("minRange", Double.class);
        double max = ctx.getArgument("maxRange", Double.class);
        if (max < min) {
            services.notifier().send(who, TeleportMessageKey.SETTPR_INVALID);
            return 0;
        }
        RtpWorldSettings updated = services.rtpQueue().updateRadii(min, max);
        services.notifier()
                .send(
                        who,
                        TeleportMessageKey.SETTPR_SET,
                        Map.of(
                                "min", format(updated.minRadius()),
                                "max", format(updated.maxRadius())));
        return Command.SINGLE_SUCCESS;
    }

    private static String format(double value) {
        return Long.toString(Math.round(value));
    }
}
