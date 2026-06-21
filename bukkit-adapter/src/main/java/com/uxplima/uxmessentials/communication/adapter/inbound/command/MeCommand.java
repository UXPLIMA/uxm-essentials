package com.uxplima.uxmessentials.communication.adapter.inbound.command;

import java.util.Map;
import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.communication.application.CommunicationMessageKey;
import com.uxplima.uxmessentials.communication.application.CommunicationNotifier;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /me <action>} ({@code uxmessentials.communication.me}): broadcast a third-person action line about the
 * invoking player to everyone online ("* Alice waves"). This is a per-player action, so it extends the players-only
 * {@link CommunicationCommandSupport} gate.
 *
 * <p>Unlike {@code /broadcast}, the typed action is <em>untrusted player input</em>, not operator MiniMessage. It
 * therefore rides as a {@code {action}} placeholder of the parity-checked {@link CommunicationMessageKey#ME} string
 * rather than as a raw source line, so a player can never inject MiniMessage tags into the broadcast. The fan-out
 * mirrors {@code BukkitAnnouncerBroadcaster}: the online roster is enumerated on the global region thread (Folia
 * forbids iterating {@code Bukkit.getOnlinePlayers()} off it), then the notifier resolves in each viewer's locale
 * and hops to their region thread.
 */
@NullMarked
public final class MeCommand extends CommunicationCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.communication.me";
    private static final String ACTION_ARG = "action";

    private final CommunicationNotifier notifier;
    private final Scheduler scheduler;

    public MeCommand(Messages messages, CommunicationNotifier notifier, Scheduler scheduler) {
        super(messages);
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("me")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(Commands.argument(ACTION_ARG, StringArgumentType.greedyString())
                        .executes(this::run))
                .build();
    }

    @Override
    public String description() {
        return "Broadcast an action message about yourself.";
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        Player actor = player(ctx);
        if (actor == null) {
            return 0;
        }
        Map<String, String> placeholders =
                Map.of("player", actor.getName(), ACTION_ARG, StringArgumentType.getString(ctx, ACTION_ARG));
        scheduler.onGlobal(() -> {
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                notifier.send(ref(viewer), CommunicationMessageKey.ME, placeholders);
            }
        });
        return Command.SINGLE_SUCCESS;
    }
}
