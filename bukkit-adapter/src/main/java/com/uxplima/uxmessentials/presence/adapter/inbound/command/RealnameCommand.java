package com.uxplima.uxmessentials.presence.adapter.inbound.command;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.presence.adapter.PresenceServices;
import com.uxplima.uxmessentials.presence.domain.PresenceMessageKey;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import com.uxplima.uxmessentials.shared.adapter.outbound.lookup.VisibleNetwork;
import com.uxplima.uxmessentials.shared.application.permission.SocialPermissions;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.OnlinePlayer;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.command.Gate;
import com.uxplima.uxmlib.command.Sender;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /realname <player>} ({@code uxmessentials.realname.use}): resolve a display name back to the underlying
 * account name, the inverse of the cosmetic rename a chat plugin or nickname feature applies. The query matches
 * either a player's account name or their rendered display name, case-insensitively, against the online set
 * filtered through the sender's {@code canSee} graph, the same seam {@code /list}, {@code /msg} and {@code /tpa}
 * read, so a vanished player the sender may not see is unresolvable, never revealing a name they could not
 * otherwise learn. The console has no {@code canSee} graph and may resolve anyone. A pure read: no use case, no
 * state mutation, just a scan of the visible online set and one resolved reply.
 */
@NullMarked
public final class RealnameCommand extends PresenceCommandSupport implements CommandRegistration {

    private static final String PERMISSION = SocialPermissions.REALNAME_USE;
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private final VisibleNetwork network;

    public RealnameCommand(PresenceServices services, Messages messages, Scheduler scheduler, VisibleNetwork network) {
        super(services, messages, scheduler);
        this.network = Objects.requireNonNull(network, "network");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("realname")
                .requires(Gate.permission(PERMISSION))
                .then(CommandSuggestions.playerArgument("player").executes(this::run))
                .build();
    }

    @Override
    public List<String> aliases() {
        return List.of();
    }

    @Override
    public String description() {
        return "Look up a player's real name.";
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = Sender.audience(ctx.getSource());
        String query = StringArgumentType.getString(ctx, "player");
        // The roster (names + display names + per-viewer canSee) is read on the global region thread (Folia forbids
        // iterating Bukkit.getOnlinePlayers() off it); the one reply then lands on the sender's own thread.
        scheduler.onGlobal(() -> {
            Player match = VisibleNameMatch.find(sender, query);
            if (match == null) {
                // A player on another backend is found by their account name; their display name stays there.
                Optional<OnlinePlayer> elsewhere = network.findElsewhere(query, sender);
                if (elsewhere.isPresent()) {
                    String name = elsewhere.get().player().name();
                    replyOnSenderThread(
                            sender,
                            () -> feedback.send(
                                    sender, PresenceMessageKey.REALNAME_RESULT, Map.of("display", name, "name", name)));
                    return;
                }
                // The nick may be a player's who is offline or on another server: the database knows who carries it.
                Set<UUID> hidden = hiddenFrom(sender);
                scheduler.async(() -> findStored(sender, query, hidden));
                return;
            }
            String display = PLAIN.serialize(match.displayName());
            String name = match.getName();
            replyOnSenderThread(
                    sender,
                    () -> feedback.send(
                            sender, PresenceMessageKey.REALNAME_RESULT, Map.of("display", display, "name", name)));
        });
        return Command.SINGLE_SUCCESS;
    }

    /**
     * The players online anywhere that {@code sender} may not see, read on the global thread. A nick the database names
     * is answered only for a player outside this set, so the stored nicks never reveal a vanished player.
     */
    private Set<UUID> hiddenFrom(CommandSender sender) {
        Set<UUID> hidden = new HashSet<>();
        Player viewer = sender instanceof Player player ? player : null;
        if (viewer != null) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (!viewer.canSee(online)) {
                    hidden.add(online.getUniqueId());
                }
            }
        }
        for (PlayerRef remote : network.everyoneElsewhere()) {
            hidden.add(remote.uuid());
        }
        for (OnlinePlayer visible : network.elsewhere(sender)) {
            hidden.remove(visible.player().uuid());
        }
        return hidden;
    }

    /**
     * Answer from the nicks the database holds, off the tick: the account that carries {@code query}, if any, and if
     * the sender may know of them.
     */
    private void findStored(CommandSender sender, String query, Set<UUID> hidden) {
        List<PlayerRef> owners;
        try {
            owners = services.nickOwners().of(query).stream()
                    .filter(owner -> !hidden.contains(owner.uuid()))
                    .toList();
        } catch (RuntimeException unreachable) {
            owners = List.of();
        }
        if (owners.isEmpty()) {
            replyOnSenderThread(
                    sender, () -> feedback.send(sender, PresenceMessageKey.REALNAME_NOT_FOUND, Map.of("query", query)));
            return;
        }
        String name = owners.getFirst().name();
        replyOnSenderThread(
                sender,
                () -> feedback.send(
                        sender, PresenceMessageKey.REALNAME_RESULT, Map.of("display", query, "name", name)));
    }
}
