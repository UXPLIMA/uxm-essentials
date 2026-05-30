package com.uxplima.uxmessentials.homes.adapter.inbound.command;

import java.util.Optional;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.homes.adapter.HomeServices;
import com.uxplima.uxmessentials.homes.domain.HomeName;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /home}, {@code /home <name>}, and {@code /home <player>:<name>}: teleport the player to one of
 * their homes, or — with the {@code uxmessentials.home.others} node — to another player's home. With no
 * name the owner's default (first-created) home is used. Resolution and the delegated gated hop are the
 * {@link com.uxplima.uxmessentials.homes.application.TeleportHome} use case's job; this handler only maps
 * the argument and gates the cross-player form.
 */
@NullMarked
public final class HomeCommand extends HomeCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.home.use";
    private static final String OTHERS_PERMISSION = "uxmessentials.home.others";

    public HomeCommand(HomeServices services, Messages messages) {
        super(services, messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("home")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::runDefault)
                .then(Commands.argument("name", StringArgumentType.greedyString())
                        .executes(this::runNamed))
                .build();
    }

    @Override
    public String description() {
        return "Teleport to one of your homes.";
    }

    private int runDefault(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        services.teleportHome().toDefault(ref(sender));
        return Command.SINGLE_SUCCESS;
    }

    private int runNamed(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        String argument = ctx.getArgument("name", String.class);
        int colon = argument.indexOf(':');
        if (colon > 0 && sender.hasPermission(OTHERS_PERMISSION)) {
            return runOther(sender, argument, colon);
        }
        services.teleportHome().toNamed(ref(sender), HomeName.of(argument));
        return Command.SINGLE_SUCCESS;
    }

    private int runOther(Player sender, String argument, int colon) {
        String ownerName = argument.substring(0, colon);
        HomeName home = HomeName.of(argument.substring(colon + 1));
        Optional<PlayerRef> owner = services.players().findOnlineByName(ownerName);
        if (owner.isEmpty()) {
            return runNamedFallback(sender, argument);
        }
        services.teleportHome().toOther(ref(sender), owner.get(), home);
        return Command.SINGLE_SUCCESS;
    }

    private int runNamedFallback(Player sender, String argument) {
        // The text before the colon did not resolve to an online owner; treat the whole token as one of
        // the sender's own home names so a literal name containing a colon still works.
        services.teleportHome().toNamed(ref(sender), HomeName.of(argument));
        return Command.SINGLE_SUCCESS;
    }
}
