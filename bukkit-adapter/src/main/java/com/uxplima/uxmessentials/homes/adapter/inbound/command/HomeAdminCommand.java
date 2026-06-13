package com.uxplima.uxmessentials.homes.adapter.inbound.command;

import java.util.Map;
import java.util.Optional;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.homes.adapter.HomeServices;
import com.uxplima.uxmessentials.homes.application.HomesMessageKey;
import com.uxplima.uxmessentials.homes.domain.HomeSlot;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /homeadmin <player> list | del <slot> | tp <slot>}: full admin management over another player's homes
 * as an explicit, audit-logged verb. The {@link com.uxplima.uxmessentials.homes.application.HomeAdmin} use case
 * reuses the same aggregate transitions the player-facing grid does; this handler resolves the target player and
 * dispatches the verb. A slot is the 1-based number the {@code list} verb prints, mapped to the zero-based
 * {@link HomeSlot}. The target is resolved online by name.
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
                .then(CommandSuggestions.playerArgument("player")
                        .then(Commands.literal("list").executes(this::runList))
                        .then(withSlot(Commands.literal("del"), this::runDelete))
                        .then(withSlot(Commands.literal("tp"), this::runTeleport)))
                .build();
    }

    @Override
    public String description() {
        return "Manage another player's homes.";
    }

    private LiteralArgumentBuilder<CommandSourceStack> withSlot(
            LiteralArgumentBuilder<CommandSourceStack> verb, Command<CommandSourceStack> action) {
        RequiredArgumentBuilder<CommandSourceStack, Integer> slot =
                Commands.argument("slot", IntegerArgumentType.integer(1)).executes(action);
        return verb.then(slot);
    }

    private int runList(CommandContext<CommandSourceStack> ctx) {
        return dispatch(ctx, target -> services.homeAdmin().list(refOf(ctx), target));
    }

    private int runDelete(CommandContext<CommandSourceStack> ctx) {
        return dispatch(ctx, target -> services.homeAdmin().delete(refOf(ctx), target, slotArg(ctx)));
    }

    private int runTeleport(CommandContext<CommandSourceStack> ctx) {
        return dispatch(ctx, target -> services.homeAdmin().teleport(refOf(ctx), target, slotArg(ctx)));
    }

    private int dispatch(CommandContext<CommandSourceStack> ctx, java.util.function.Consumer<PlayerRef> verb) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        return target(sender, ctx)
                .map(target -> {
                    verb.accept(target);
                    return Command.SINGLE_SUCCESS;
                })
                .orElse(Command.SINGLE_SUCCESS);
    }

    private Optional<PlayerRef> target(Player sender, CommandContext<CommandSourceStack> ctx) {
        String name = ctx.getArgument("player", String.class);
        Optional<PlayerRef> target = services.players().findOnlineByName(name);
        if (target.isEmpty()) {
            feedback.send(sender, HomesMessageKey.HOME_ADMIN_TARGET_UNKNOWN, Map.of("player", name));
        }
        return target;
    }

    private PlayerRef refOf(CommandContext<CommandSourceStack> ctx) {
        return ref((Player) ctx.getSource().getSender());
    }

    private static HomeSlot slotArg(CommandContext<CommandSourceStack> ctx) {
        // The admin types the 1-based number /homeadmin list prints; the aggregate keys on the zero-based slot.
        return HomeSlot.of(ctx.getArgument("slot", Integer.class) - 1);
    }
}
