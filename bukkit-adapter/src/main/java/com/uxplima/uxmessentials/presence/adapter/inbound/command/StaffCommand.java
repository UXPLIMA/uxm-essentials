package com.uxplima.uxmessentials.presence.adapter.inbound.command;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
import com.uxplima.uxmessentials.presence.domain.PresenceMessageKey;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.outbound.lookup.VisibleNetwork;
import com.uxplima.uxmessentials.shared.application.permission.SocialPermissions;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.OnlinePlayer;
import com.uxplima.uxmessentials.shared.domain.message.MessageKey;
import com.uxplima.uxmlib.command.Gate;
import com.uxplima.uxmlib.command.Sender;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /staff} ({@code uxmessentials.staff.use}): print the online staff roster, vanish-aware. The online set is
 * narrowed to holders of {@code uxmessentials.staff.member}, the marker that designates a player as staff, and
 * then filtered through the running player's {@code canSee} graph, the same seam {@code /list} reads, so a vanished
 * staffer the viewer may not see is absent from both the line and the count. The console has no {@code canSee}
 * graph and sees every staff member. An empty roster resolves a dedicated key rather than an empty list. This is a
 * pure read: no use case, no state mutation, just the online set, a name-sorted join, and one resolved line. On a
 * network the staff on the other backends the viewer may see are listed too, from what each backend announced.
 */
@NullMarked
public final class StaffCommand extends PresenceCommandSupport implements CommandRegistration {

    private static final String PERMISSION = SocialPermissions.STAFF_USE;
    private static final String STAFF_NODE = SocialPermissions.STAFF_MEMBER;
    private static final MessageKey STAFF_LIST = PresenceMessageKey.STAFF_LIST;
    private static final MessageKey STAFF_EMPTY = PresenceMessageKey.STAFF_EMPTY;

    private final VisibleNetwork network;

    public StaffCommand(PresenceServices services, Messages messages, Scheduler scheduler, VisibleNetwork network) {
        super(services, messages, scheduler);
        this.network = Objects.requireNonNull(network, "network");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("staff")
                .requires(Gate.permission(PERMISSION))
                .executes(this::run)
                .build();
    }

    @Override
    public String description() {
        return "List online staff.";
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = Sender.audience(ctx.getSource());
        // The roster (staff membership + per-viewer canSee) is read on the global region thread (Folia forbids
        // iterating Bukkit.getOnlinePlayers() off it); the one reply then lands on the sender's own thread.
        scheduler.onGlobal(() -> {
            List<String> names = collectStaffNames(sender);
            if (names.isEmpty()) {
                replyOnSenderThread(sender, () -> feedback.send(sender, STAFF_EMPTY, Map.of()));
                return;
            }
            Map<String, String> placeholders =
                    Map.of("count", String.valueOf(names.size()), "players", String.join(", ", names));
            replyOnSenderThread(sender, () -> feedback.send(sender, STAFF_LIST, placeholders));
        });
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Online staff the sender may see here and on the other backends, sorted; a player's {@code canSee} hides vanished
     * staff here, their see level elsewhere, and the console sees all.
     */
    private List<String> collectStaffNames(CommandSender sender) {
        Player viewer = sender instanceof Player player ? player : null;
        List<String> names = Bukkit.getOnlinePlayers().stream()
                .filter(target -> viewer == null || viewer.canSee(target))
                .filter(target -> target.hasPermission(STAFF_NODE))
                .map(Player::getName)
                .collect(Collectors.toCollection(ArrayList::new));
        for (OnlinePlayer staff : network.staffElsewhere(sender)) {
            names.add(staff.player().name());
        }
        names.sort(Comparator.naturalOrder());
        return names;
    }
}
