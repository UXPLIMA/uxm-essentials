package com.uxplima.uxmessentials.communication.adapter.inbound.command;

import java.util.Objects;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.communication.adapter.outbound.BukkitAnnouncerBroadcaster;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.permission.SocialPermissions;
import com.uxplima.uxmlib.command.Gate;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /broadcast <message>} ({@code uxmessentials.communication.broadcast}): fire a one-off server-wide
 * announcement. Unlike the per-player {@code /broadcasttoggle}, this is an operator action and is allowed from the
 * console, so it does not extend the players-only {@link CommunicationCommandSupport} gate.
 *
 * <p>The body is operator-authored MiniMessage content, exactly like announcer lines and info pages, so it flows
 * through the {@link BukkitAnnouncerBroadcaster} (and the {@code MessageSink} beneath it) as a raw source string
 * rather than a {@code MessageKey}, and is never parity-checked. The shared broadcaster reuses the same fan-out the
 * scheduled announcer uses: it skips players who opted out via {@code /broadcasttoggle} and hops to each viewer's
 * region thread inside the sink. The broadcaster frames the body with the catalogue's broadcast line, so a manual
 * broadcast reads like an announcement rather than plain chat, in each reader's language.
 */
@NullMarked
public final class BroadcastCommand implements CommandRegistration {

    private static final String PERMISSION = SocialPermissions.COMMUNICATION_BROADCAST;
    private static final String MESSAGE_ARG = "message";

    private final BukkitAnnouncerBroadcaster broadcaster;

    public BroadcastCommand(BukkitAnnouncerBroadcaster broadcaster) {
        this.broadcaster = Objects.requireNonNull(broadcaster, "broadcaster");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("broadcast")
                .requires(Gate.permission(PERMISSION))
                .then(Commands.argument(MESSAGE_ARG, StringArgumentType.greedyString())
                        .executes(this::run))
                .build();
    }

    @Override
    public String description() {
        return "Send an announcement to all online players.";
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        String message = StringArgumentType.getString(ctx, MESSAGE_ARG);
        broadcaster.broadcast(message);
        return Command.SINGLE_SUCCESS;
    }
}
