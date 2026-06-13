package com.uxplima.uxmessentials.homes.adapter.inbound.command;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.homes.adapter.HomeServices;
import com.uxplima.uxmessentials.homes.application.HomesMessageKey;
import com.uxplima.uxmessentials.homes.domain.HomeSlot;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /visit <player> [slot]}: teleport the sender to another player's home. The owner is resolved by the
 * offline-capable {@link com.uxplima.uxmessentials.shared.application.port.PlayerLookup#findByName} so a public
 * home stays reachable while its owner is offline; the optional 1-based slot defaults to the owner's first home
 * (slot index 0). The access gates and the teleport delegation live in
 * {@link com.uxplima.uxmessentials.homes.application.VisitHome}; this handler only resolves the target and slot
 * and dispatches off the tick thread (the use case reads the home and invite list from the DB).
 */
@NullMarked
public final class VisitCommand extends HomeCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.home.visit";

    private final Scheduler scheduler;

    public VisitCommand(HomeServices services, Messages messages, Scheduler scheduler) {
        super(services, messages);
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("visit")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(CommandSuggestions.playerArgument("player")
                        .executes(ctx -> run(ctx, HomeSlot.of(0)))
                        .then(Commands.argument("slot", IntegerArgumentType.integer(1))
                                .executes(ctx -> run(ctx, slotArg(ctx)))))
                .build();
    }

    @Override
    public String description() {
        return "Visit another player's home.";
    }

    private int run(CommandContext<CommandSourceStack> ctx, HomeSlot slot) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        PlayerRef actor = ref(sender);
        String name = ctx.getArgument("player", String.class);
        Optional<PlayerRef> owner = services.players().findByName(name);
        if (owner.isEmpty()) {
            feedback.send(sender, HomesMessageKey.HOME_ADMIN_TARGET_UNKNOWN, Map.of("player", name));
            return Command.SINGLE_SUCCESS;
        }
        PlayerRef resolved = owner.get();
        scheduler.async(() -> services.visitHome().visit(actor, resolved, slot));
        return Command.SINGLE_SUCCESS;
    }

    private static HomeSlot slotArg(CommandContext<CommandSourceStack> ctx) {
        // The player types the 1-based number printed in the grid; the aggregate keys on the zero-based slot.
        return HomeSlot.of(ctx.getArgument("slot", Integer.class) - 1);
    }
}
