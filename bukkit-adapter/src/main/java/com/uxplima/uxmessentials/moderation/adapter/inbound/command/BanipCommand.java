package com.uxplima.uxmessentials.moderation.adapter.inbound.command;

import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.moderation.adapter.ModerationServices;
import com.uxplima.uxmessentials.moderation.application.BanIp;
import com.uxplima.uxmessentials.moderation.application.ModerationMessageKey;
import com.uxplima.uxmessentials.moderation.domain.SeenRecord;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /banip <player|ip> [reason]}: ban by stored IP. When the argument is an IP literal it is banned
 * directly with no resolved UUID; when it is a player name, the player's last-seen IP is resolved from the
 * DB-backed seen record (a real indexed lookup) and the UUID recorded for a replayable audit line. The
 * alt-detection and audit are the {@code BanIp} use case's job. A player with no recorded IP yields a
 * not-found notice.
 */
@NullMarked
public final class BanipCommand extends ModerationCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.moderation.banip";
    private static final Pattern IP_LITERAL = Pattern.compile("^[0-9.:a-fA-F]+$");

    public BanipCommand(ModerationServices services, Messages messages, MessageSink sink) {
        super(services, messages, sink);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("banip")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(Commands.argument("target", StringArgumentType.word())
                        .executes(ctx -> run(ctx, Optional.empty()))
                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                                .executes(ctx -> run(ctx, optionalReason(ctx)))))
                .build();
    }

    @Override
    public String description() {
        return "Ban a player or IP by address.";
    }

    private int run(CommandContext<CommandSourceStack> ctx, Optional<String> reason) {
        PlayerRef actor = actor(ctx);
        String argument = ctx.getArgument("target", String.class);
        Optional<BanIp.Target> resolved = resolveTarget(ctx, argument);
        resolved.ifPresent(target -> services.banIp().banIp(actor, target, "", reason));
        return Command.SINGLE_SUCCESS;
    }

    private Optional<BanIp.Target> resolveTarget(CommandContext<CommandSourceStack> ctx, String argument) {
        if (argument.contains(".") || argument.contains(":")) {
            return IP_LITERAL.matcher(argument).matches()
                    ? Optional.of(new BanIp.Target(argument, Optional.empty()))
                    : Optional.empty();
        }
        return targetByName(ctx, argument).flatMap(player -> byPlayer(ctx, player));
    }

    private Optional<BanIp.Target> byPlayer(CommandContext<CommandSourceStack> ctx, PlayerRef player) {
        Optional<String> ip = services.repository().seen(player).flatMap(SeenRecord::lastIp);
        if (ip.isEmpty()) {
            notify(ctx, ModerationMessageKey.SEENIP_NO_IP, Map.of("player", player.name()));
            return Optional.empty();
        }
        return Optional.of(new BanIp.Target(ip.get(), Optional.of(player.uuid())));
    }
}
