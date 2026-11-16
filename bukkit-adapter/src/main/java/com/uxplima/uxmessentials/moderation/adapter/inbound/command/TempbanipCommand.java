package com.uxplima.uxmessentials.moderation.adapter.inbound.command;

import java.util.Map;
import java.util.Optional;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.moderation.adapter.ModerationServices;
import com.uxplima.uxmessentials.moderation.application.BanIp;
import com.uxplima.uxmessentials.moderation.domain.ModerationMessageKey;
import com.uxplima.uxmessentials.moderation.domain.SeenRecord;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import com.uxplima.uxmessentials.shared.application.permission.EnforcementPermissions;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.command.Args;
import com.uxplima.uxmlib.command.Gate;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /tempbanip <player|ip> <duration> [reason]}: ban an address until a wall-clock expiry. The target is
 * resolved exactly as {@code /banip} does. An IP literal is banned directly, a player name resolves their
 * last-seen IP from the DB-backed seen record, and the {@code TempBanIp} use case enforces that the duration
 * is timed (a permanent IP ban is {@code /banip}). Shares the {@code moderation.banip} node, like
 * {@code /unbanip} does.
 */
@NullMarked
public final class TempbanipCommand extends ModerationCommandSupport implements CommandRegistration {

    private static final String PERMISSION = EnforcementPermissions.MODERATION_BANIP;

    public TempbanipCommand(ModerationServices services, Messages messages, MessageSink sink) {
        super(services, messages, sink);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("tempbanip")
                .requires(Gate.permission(PERMISSION))
                .then(CommandSuggestions.playerArgument("target")
                        .then(Commands.argument("duration", Args.token())
                                .executes(ctx -> run(ctx, Optional.empty()))
                                .then(Commands.argument("reason", StringArgumentType.greedyString())
                                        .executes(ctx -> run(ctx, optionalReason(ctx))))))
                .build();
    }

    @Override
    public String description() {
        return "Ban an IP for a duration.";
    }

    private int run(CommandContext<CommandSourceStack> ctx, Optional<String> reason) {
        PlayerRef actor = actor(ctx);
        String argument = ctx.getArgument("target", String.class);
        String duration = ctx.getArgument("duration", String.class);
        resolveTarget(ctx, argument, actor)
                .ifPresent(resolving -> offTick(
                        actor,
                        () -> resolving
                                .resolve()
                                .ifPresent(target -> services.tempBanIp().tempBanIp(actor, target, duration, reason))));
        return Command.SINGLE_SUCCESS;
    }

    /**
     * How the command finds the address to ban, decided on the command thread: an address typed is used as it is, a
     * player name is looked up here and their last address read from their seen record on the async lane. Empty, after
     * a line to the sender, when the argument is neither.
     */
    private Optional<Resolving> resolveTarget(
            CommandContext<CommandSourceStack> ctx, String argument, PlayerRef actor) {
        if (IpLiteral.looksLikeAddress(argument)) {
            Optional<String> ip = IpLiteral.canonical(argument);
            if (ip.isEmpty()) {
                notify(ctx, ModerationMessageKey.BANIP_INVALID_IP, Map.of());
            }
            return ip.map(address -> () -> Optional.of(new BanIp.Target(address, Optional.empty())));
        }
        return targetByName(ctx, argument).map(player -> () -> byPlayer(actor, player));
    }

    /** The address a player was last seen on, read from storage; empty, after a line to the actor, when none. */
    private Optional<BanIp.Target> byPlayer(PlayerRef actor, PlayerRef player) {
        Optional<String> ip = services.repository().seen(player).flatMap(SeenRecord::lastIp);
        if (ip.isEmpty()) {
            tell(actor, ModerationMessageKey.SEENIP_NO_IP, Map.of("player", player.name()));
            return Optional.empty();
        }
        return Optional.of(new BanIp.Target(ip.get(), Optional.of(player.uuid())));
    }

    /** The address to ban, found on the async lane. */
    @FunctionalInterface
    private interface Resolving {
        Optional<BanIp.Target> resolve();
    }
}
