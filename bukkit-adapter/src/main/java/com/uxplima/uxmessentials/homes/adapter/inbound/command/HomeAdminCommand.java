package com.uxplima.uxmessentials.homes.adapter.inbound.command;

import java.util.Map;
import java.util.Optional;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.homes.adapter.HomeServices;
import com.uxplima.uxmessentials.homes.application.HomesMessageKey;
import com.uxplima.uxmessentials.homes.domain.HomeName;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /homeadmin <player> <list|del|tp> [name]}: full admin management over another player's homes as
 * an explicit, audit-logged verb, distinct from the per-command {@code .others} nodes. The
 * {@link com.uxplima.uxmessentials.homes.application.HomeAdmin} use case reuses the same aggregate
 * transitions the player-facing commands do; this handler resolves the target player and dispatches the
 * verb. The target is resolved online by name here — offline-target admin reaches through the persisted
 * row directly in a later pass.
 */
@NullMarked
public final class HomeAdminCommand extends HomeCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.home.admin";

    public HomeAdminCommand(HomeServices services, Messages messages) {
        super(services, messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("homeadmin")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(Commands.argument("player", StringArgumentType.word())
                        .then(Commands.literal("list").executes(this::runList))
                        .then(withName(Commands.literal("del"), this::runDelete))
                        .then(withName(Commands.literal("tp"), this::runTeleport)))
                .build();
    }

    @Override
    public String description() {
        return "Manage another player's homes.";
    }

    private com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> withName(
            com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> verb,
            com.mojang.brigadier.Command<CommandSourceStack> action) {
        RequiredArgumentBuilder<CommandSourceStack, String> name =
                Commands.argument("name", StringArgumentType.word()).executes(action);
        return verb.then(name);
    }

    private int runList(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        return target(sender, ctx)
                .map(target -> {
                    services.homeAdmin().list(ref(sender), target);
                    return Command.SINGLE_SUCCESS;
                })
                .orElse(Command.SINGLE_SUCCESS);
    }

    private int runDelete(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        return target(sender, ctx)
                .map(target -> {
                    services.homeAdmin().delete(ref(sender), target, nameArg(ctx));
                    return Command.SINGLE_SUCCESS;
                })
                .orElse(Command.SINGLE_SUCCESS);
    }

    private int runTeleport(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        return target(sender, ctx)
                .map(target -> {
                    services.homeAdmin().teleport(ref(sender), target, nameArg(ctx));
                    return Command.SINGLE_SUCCESS;
                })
                .orElse(Command.SINGLE_SUCCESS);
    }

    private Optional<PlayerRef> target(Player sender, CommandContext<CommandSourceStack> ctx) {
        String name = ctx.getArgument("player", String.class);
        Optional<PlayerRef> target = services.players().findOnlineByName(name);
        if (target.isEmpty()) {
            sendUnknown(sender, name);
        }
        return target;
    }

    private void sendUnknown(Player sender, String name) {
        sender.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                .deserialize(messages.resolve(
                        ref(sender), HomesMessageKey.HOME_ADMIN_TARGET_UNKNOWN, Map.of("player", name))));
    }

    private static HomeName nameArg(CommandContext<CommandSourceStack> ctx) {
        return HomeName.of(ctx.getArgument("name", String.class));
    }
}
