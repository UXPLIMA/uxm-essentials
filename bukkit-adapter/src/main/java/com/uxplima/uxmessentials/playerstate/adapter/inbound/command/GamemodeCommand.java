package com.uxplima.uxmessentials.playerstate.adapter.inbound.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.playerstate.adapter.PlayerStateServices;
import com.uxplima.uxmessentials.playerstate.application.PlayerstateMessageKey;
import com.uxplima.uxmessentials.playerstate.domain.GameModeRef;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.PlayerTargets;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /gamemode <mode> [player]} ({@code uxmessentials.gamemode.use}): set a player's game mode. The mode
 * is parsed from the full name, the short alias, or the numeric id; an unrecognised mode is rejected with
 * {@link PlayerstateMessageKey#GAMEMODE_INVALID}. The {@code .others} target is gated by the shared
 * {@code uxmessentials.playerstate.others} node. The {@code /gmc /gms /gma /gmsp} shortcuts are separate
 * fixed-mode commands ({@link GamemodeAliasCommand}), not aliases of this literal, because each pins a mode.
 */
@NullMarked
public final class GamemodeCommand extends PlayerstateCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.gamemode.use";

    // Every form GameModeRef.parse accepts — the full name, the short alias, and the numeric id — derived from the
    // enum so the type-ahead never drifts from what the parser resolves.
    private static final List<String> MODE_SUGGESTIONS = modeSuggestions();

    public GamemodeCommand(PlayerStateServices services, Messages messages) {
        super(services, messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("gamemode")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(Commands.argument("mode", StringArgumentType.word())
                        .suggests(CommandSuggestions.fromStrings(() -> MODE_SUGGESTIONS))
                        .executes(this::setMode)
                        .then(PlayerTargets.players("player").executes(this::setMode)))
                .build();
    }

    @Override
    public String description() {
        return "Set game mode.";
    }

    private int setMode(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        Optional<GameModeRef> mode = GameModeRef.parse(ctx.getArgument("mode", String.class));
        if (mode.isEmpty()) {
            feedback.send(sender, PlayerstateMessageKey.GAMEMODE_INVALID, Map.of());
            return 0;
        }
        List<PlayerRef> targets = resolveTargets(ctx, sender);
        if (targets.isEmpty()) {
            return 0;
        }
        for (PlayerRef target : targets) {
            services.setGamemode().setFor(ref(sender), target, mode.get());
        }
        return Command.SINGLE_SUCCESS;
    }

    private static List<String> modeSuggestions() {
        List<String> modes = new ArrayList<>();
        for (GameModeRef mode : GameModeRef.values()) {
            modes.add(mode.canonical());
            modes.add(mode.shortAlias());
            modes.add(Integer.toString(mode.id()));
        }
        return List.copyOf(modes);
    }
}
