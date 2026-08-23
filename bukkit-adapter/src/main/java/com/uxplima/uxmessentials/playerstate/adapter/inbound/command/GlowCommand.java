package com.uxplima.uxmessentials.playerstate.adapter.inbound.command;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.playerstate.adapter.PlayerStateServices;
import com.uxplima.uxmessentials.playerstate.application.PlayerstateMessageKey;
import com.uxplima.uxmessentials.playerstate.domain.GlowColor;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.PlayerTargets;
import com.uxplima.uxmessentials.shared.application.message.SharedMessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /glow [colour] [player]} ({@code uxmessentials.glow.use}): the glowing outline. Bare {@code /glow} toggles it
 * on yourself; naming one of the sixteen vanilla colours turns it on and draws it in that colour, and a trailing
 * player targets somebody else. The colour form takes {@code uxmessentials.glow.color} and the target form
 * {@code uxmessentials.glow.others} (or the cross-cutting {@code uxmessentials.playerstate.others}); the
 * {@code ToggleGlow} use case owns the outline mutation and the confirmations.
 *
 * <p>The colour argument is a plain word rather than a Brigadier enum so an unknown value is answered with the
 * catalog's own rejection instead of a raw parser error.
 */
@NullMarked
public final class GlowCommand extends PlayerstateCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.glow.use";
    private static final String COLOUR_PERMISSION = "uxmessentials.glow.color";

    /** The colour ids offered in completion, in the enum's own order (dark to light, as vanilla lists them). */
    private static final List<String> COLOUR_IDS =
            Arrays.stream(GlowColor.values()).map(GlowColor::id).toList();

    public GlowCommand(PlayerStateServices services, Messages messages) {
        super(services, messages);
    }

    /** Targeting somebody else takes this node, or the cross-cutting playerstate one. */
    @Override
    String othersNode() {
        return "uxmessentials.glow.others";
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("glow")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::toggle)
                .then(Commands.argument("color", StringArgumentType.word())
                        .suggests(CommandSuggestions.fromStrings(() -> COLOUR_IDS))
                        .executes(this::colour)
                        .then(PlayerTargets.players("player").executes(this::colour)))
                .build();
    }

    @Override
    public String description() {
        return "Toggle a glowing outline, in a colour of your choice.";
    }

    private int toggle(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        services.toggleGlow().toggle(ref(sender));
        return Command.SINGLE_SUCCESS;
    }

    private int colour(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        String typed = StringArgumentType.getString(ctx, "color");
        Optional<GlowColor> colour = GlowColor.fromId(typed);
        if (colour.isEmpty()) {
            feedback.send(sender, PlayerstateMessageKey.GLOW_COLOR_INVALID, Map.of("color", typed));
            return 0;
        }
        if (!sender.hasPermission(COLOUR_PERMISSION)) {
            feedback.send(sender, SharedMessageKey.COMMAND_NO_PERMISSION, Map.of());
            return 0;
        }
        List<PlayerRef> targets = resolveTargets(ctx, sender);
        if (targets.isEmpty()) {
            return 0;
        }
        for (PlayerRef target : targets) {
            services.toggleGlow().colourFor(actor(ctx), target, colour.get());
        }
        return Command.SINGLE_SUCCESS;
    }
}
