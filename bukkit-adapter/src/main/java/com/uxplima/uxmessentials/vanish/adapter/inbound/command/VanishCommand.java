package com.uxplima.uxmessentials.vanish.adapter.inbound.command;

import java.util.Map;
import java.util.Objects;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandFeedback;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.message.SharedMessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vanish.application.ToggleVanish;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /vanish [on|off]} ({@code uxmessentials.vanish.use}): become invisible to players who cannot see vanished
 * players. Bare {@code /vanish} toggles; {@code /vanish on} / {@code /vanish off} set the state absolutely (idempotent).
 * The {@link ToggleVanish} use case owns the store flip, the packet hide/show, and the feedback; this handler only maps
 * the invoking player. The vanish-see node ({@code uxmessentials.vanish.see}) is enforced inside the view, not here.
 */
@NullMarked
public final class VanishCommand implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.vanish.use";

    private final ToggleVanish toggleVanish;
    private final CommandFeedback feedback;

    public VanishCommand(ToggleVanish toggleVanish, Messages messages) {
        this.toggleVanish = Objects.requireNonNull(toggleVanish, "toggleVanish");
        this.feedback = new CommandFeedback(Objects.requireNonNull(messages, "messages"));
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("vanish")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::toggle)
                .then(Commands.literal("on").executes(ctx -> set(ctx, true)))
                .then(Commands.literal("off").executes(ctx -> set(ctx, false)))
                .build();
    }

    @Override
    public String description() {
        return "Become invisible to other players.";
    }

    private int toggle(CommandContext<CommandSourceStack> ctx) {
        PlayerRef who = playerRef(ctx);
        if (who == null) {
            return 0;
        }
        toggleVanish.toggle(who);
        return Command.SINGLE_SUCCESS;
    }

    private int set(CommandContext<CommandSourceStack> ctx, boolean vanished) {
        PlayerRef who = playerRef(ctx);
        if (who == null) {
            return 0;
        }
        toggleVanish.setVanished(who, vanished);
        return Command.SINGLE_SUCCESS;
    }

    /** The invoking player's ref, or {@code null} (after the players-only reply) for a console source. */
    private @org.jspecify.annotations.Nullable PlayerRef playerRef(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (sender instanceof Player player) {
            return BukkitRefs.toRef(player);
        }
        feedback.send(sender, SharedMessageKey.COMMAND_PLAYERS_ONLY, Map.of());
        return null;
    }
}
