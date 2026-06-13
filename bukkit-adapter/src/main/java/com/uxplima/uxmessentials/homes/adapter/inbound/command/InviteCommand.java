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
 * {@code /invite <player> [slot]}: grant another player access to one of the sender's homes so they can visit
 * it while it stays private. The invited player is resolved through the offline-capable
 * {@link com.uxplima.uxmessentials.shared.application.port.PlayerLookup#findByName}; the optional 1-based slot
 * defaults to the sender's first home (slot index 0). The slot/empty-home checks live in
 * {@link com.uxplima.uxmessentials.homes.application.InviteToHome}; this handler resolves the invited player and
 * slot and dispatches off the tick thread (the use case writes the invite to the DB).
 */
@NullMarked
public final class InviteCommand extends HomeCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.home.invite";

    private final Scheduler scheduler;

    public InviteCommand(HomeServices services, Messages messages, Scheduler scheduler) {
        super(services, messages);
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("invite")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(CommandSuggestions.playerArgument("player")
                        .executes(ctx -> run(ctx, HomeSlot.of(0)))
                        .then(Commands.argument("slot", IntegerArgumentType.integer(1))
                                .executes(ctx -> run(ctx, slotArg(ctx)))))
                .build();
    }

    @Override
    public String description() {
        return "Invite a player to one of your homes.";
    }

    private int run(CommandContext<CommandSourceStack> ctx, HomeSlot slot) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        PlayerRef owner = ref(sender);
        String name = ctx.getArgument("player", String.class);
        Optional<PlayerRef> invited = services.players().findByName(name);
        if (invited.isEmpty()) {
            feedback.send(sender, HomesMessageKey.HOME_ADMIN_TARGET_UNKNOWN, Map.of("player", name));
            return Command.SINGLE_SUCCESS;
        }
        PlayerRef resolved = invited.get();
        scheduler.async(() -> services.inviteToHome().invite(owner, slot, resolved));
        return Command.SINGLE_SUCCESS;
    }

    private static HomeSlot slotArg(CommandContext<CommandSourceStack> ctx) {
        // The player types the 1-based number printed in the grid; the aggregate keys on the zero-based slot.
        return HomeSlot.of(ctx.getArgument("slot", Integer.class) - 1);
    }
}
