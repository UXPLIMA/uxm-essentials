package com.uxplima.uxmessentials.playerstate.adapter.inbound.command;

import java.util.Objects;
import java.util.Optional;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.playerstate.adapter.PlayerStateServices;
import com.uxplima.uxmessentials.playerstate.domain.GameModeRef;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * A fixed-mode game-mode shortcut: {@code /gmc} (creative), {@code /gms} (survival), {@code /gma} (adventure),
 * {@code /gmsp} (spectator). Each pins one {@link GameModeRef}, so they are separate command literals rather
 * than aliases of {@code /gamemode}. The {@code [player]} target is gated by the shared
 * {@code uxmessentials.playerstate.others} node and the command base is {@code uxmessentials.gamemode.use},
 * matching the {@code /gamemode} surface.
 */
@NullMarked
public final class GamemodeAliasCommand extends PlayerstateCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.gamemode.use";

    private final String literal;
    private final GameModeRef mode;

    public GamemodeAliasCommand(String literal, GameModeRef mode, PlayerStateServices services, Messages messages) {
        super(services, messages);
        this.literal = Objects.requireNonNull(literal, "literal");
        this.mode = Objects.requireNonNull(mode, "mode");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal(literal)
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::set)
                .then(CommandSuggestions.playerArgument("player").executes(this::set))
                .build();
    }

    @Override
    public String description() {
        return "Set game mode to " + mode.canonical() + ".";
    }

    private int set(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        Optional<PlayerRef> target = resolveTarget(ctx, sender);
        if (target.isEmpty()) {
            return 0;
        }
        services.setGamemode().setFor(ref(sender), target.get(), mode);
        return Command.SINGLE_SUCCESS;
    }
}
