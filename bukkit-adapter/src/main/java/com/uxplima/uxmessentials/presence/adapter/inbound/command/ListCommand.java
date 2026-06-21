package com.uxplima.uxmessentials.presence.adapter.inbound.command;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.presence.adapter.PresenceServices;
import com.uxplima.uxmessentials.presence.application.PresenceMessageKey;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /list} ({@code uxmessentials.list.use}): print the online roster, vanish-aware. When a player runs it the
 * roster is filtered through that player's {@code canSee} graph — the same seam the presence
 * {@code VisibilityApplier} drives and that messaging {@code /msg} and teleport {@code /tpa} already read — so a
 * vanished player they may not see is absent from both the line and the count. The
 * console has no {@code canSee} graph and sees everyone. This is a pure read: no use case, no state mutation, just
 * the online set, a name-sorted join, and one resolved line.
 */
@NullMarked
public final class ListCommand extends PresenceCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.list.use";
    private static final MessageKey LIST_PLAYERS = PresenceMessageKey.LIST_PLAYERS;

    public ListCommand(PresenceServices services, Messages messages, Scheduler scheduler) {
        super(services, messages, scheduler);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("list")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::run)
                .build();
    }

    @Override
    public List<String> aliases() {
        return List.of("who", "online", "playerlist");
    }

    @Override
    public String description() {
        return "List online players.";
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        // The roster (names + per-viewer canSee) is read on the global region thread (Folia forbids iterating
        // Bukkit.getOnlinePlayers() off it); the one reply then lands on the sender's own thread.
        scheduler.onGlobal(() -> {
            List<String> names = collectVisibleNames(sender);
            String joined = String.join(", ", names);
            Map<String, String> placeholders = Map.of("count", String.valueOf(names.size()), "players", joined);
            replyOnSenderThread(sender, () -> feedback.send(sender, LIST_PLAYERS, placeholders));
        });
        return Command.SINGLE_SUCCESS;
    }

    /** Online names the sender may see, sorted; a player's {@code canSee} hides vanished targets, the console sees all. */
    private List<String> collectVisibleNames(CommandSender sender) {
        Player viewer = sender instanceof Player player ? player : null;
        return Bukkit.getOnlinePlayers().stream()
                .filter(target -> viewer == null || viewer.canSee(target))
                .map(Player::getName)
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.toList());
    }
}
