package com.uxplima.uxmessentials.moderation.adapter.inbound.command;

import java.util.Optional;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.moderation.adapter.ModerationServices;
import com.uxplima.uxmessentials.moderation.adapter.inbound.gui.PunishmentAction;
import com.uxplima.uxmessentials.moderation.adapter.inbound.gui.PunishmentGuiFlow;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * {@code /mute <player> [duration] [-s] [reason]}: gag a player's outbound messaging. With no duration the mute
 * is permanent; with one ({@code /tempmute} is the explicit-duration alias) it expires. The exempt/duration
 * gating and the audit line are the {@code Mute} use case's job; this handler maps the name, the optional
 * duration token, and the greedy reason; a leading {@code -s} in the reason suppresses the staff broadcast.
 *
 * <p>Bare {@code /mute} (no arguments) opens the player-picker → confirm GUI flow for a permanent mute when the
 * command's catalog {@code gui} flag is on: {@link #guiRoot()} returns the opener and the shared
 * {@code GuiRootBinding} installs it as the root executor. The raw subcommand form is unchanged either way, and
 * the same {@code .requires} permission gate covers the bare-root opener so a non-holder cannot open the picker.
 */
@NullMarked
public final class MuteCommand extends ModerationCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.moderation.mute";

    private final boolean silentByDefault;
    private final @Nullable PunishmentGuiFlow guiFlow;

    public MuteCommand(
            ModerationServices services,
            Messages messages,
            MessageSink sink,
            boolean silentByDefault,
            @Nullable PunishmentGuiFlow guiFlow) {
        super(services, messages, sink);
        this.silentByDefault = silentByDefault;
        this.guiFlow = guiFlow;
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("mute")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(CommandSuggestions.playerArgument("player")
                        .executes(ctx -> run(ctx, "", Optional.empty()))
                        .then(Commands.argument("duration", StringArgumentType.word())
                                .executes(ctx -> run(ctx, ctx.getArgument("duration", String.class), Optional.empty()))
                                .then(Commands.argument("reason", StringArgumentType.greedyString())
                                        .executes(ctx -> run(
                                                ctx, ctx.getArgument("duration", String.class), optionalReason(ctx))))))
                .build();
    }

    @Override
    public String description() {
        return "Mute a player, optionally for a duration (prefix the reason with -s to mute silently).";
    }

    @Override
    public Optional<Command<CommandSourceStack>> guiRoot() {
        if (guiFlow == null) {
            return Optional.empty();
        }
        return Optional.of(ctx -> {
            if (ctx.getSource().getSender() instanceof Player sender) {
                guiFlow.open(sender, BukkitRefs.toRef(sender), PunishmentAction.MUTE);
            }
            return Command.SINGLE_SUCCESS;
        });
    }

    private int run(CommandContext<CommandSourceStack> ctx, String duration, Optional<String> reason) {
        PlayerRef actor = actor(ctx);
        SilentReason parsed = silentReason(reason, silentByDefault);
        Optional<PlayerRef> target = targetByName(ctx, ctx.getArgument("player", String.class));
        target.ifPresent(to -> services.mute().mute(actor, to, duration, parsed.reason(), parsed.silent()));
        return Command.SINGLE_SUCCESS;
    }
}
