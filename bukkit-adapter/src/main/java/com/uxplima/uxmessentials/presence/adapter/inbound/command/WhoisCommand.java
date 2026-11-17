package com.uxplima.uxmessentials.presence.adapter.inbound.command;

import java.util.List;
import java.util.Map;
import java.util.Objects;

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
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
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
 * {@code /whois <player>} ({@code uxmessentials.whois.use}): a staff identity and status summary for one online
 * player, account name, display name, uuid, gamemode, health, ping and world in a single line. The query
 * matches either a player's account name or their rendered display name, case-insensitively, against the online
 * set filtered through the sender's {@code canSee} graph, the same seam {@code /list} and {@code /realname} read,
 * so a vanished player the sender may not see is unresolvable, never leaking details they could not otherwise
 * learn. The console has no {@code canSee} graph and may resolve anyone. A pure read: no use case, no state
 * mutation, just a scan of the visible online set and one resolved reply.
 *
 * <p>A player the sender may see on another backend of the network, found by account name, is answered with what this
 * backend knows of them: their name, uuid and the server they are on.
 */
@NullMarked
public final class WhoisCommand extends PresenceCommandSupport implements CommandRegistration {

    private static final String PERMISSION = SocialPermissions.WHOIS_USE;
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private final VisibleNetwork network;

    public WhoisCommand(PresenceServices services, Messages messages, Scheduler scheduler, VisibleNetwork network) {
        super(services, messages, scheduler);
        this.network = Objects.requireNonNull(network, "network");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("whois")
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
        return "Show information about an online player.";
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = Sender.audience(ctx.getSource());
        String query = StringArgumentType.getString(ctx, "player");
        // The roster (names + display names + per-viewer canSee) is read on the global region thread (Folia forbids
        // iterating Bukkit.getOnlinePlayers() off it); the matched player's live status fields are then read on that
        // player's own entity thread, and the one reply lands on the sender's own thread.
        scheduler.onGlobal(() -> {
            VisibleNameMatch.Found found = VisibleNameMatch.find(sender, query, network);
            if (found instanceof VisibleNameMatch.Elsewhere(OnlinePlayer elsewhere)) {
                replyOnSenderThread(
                        sender,
                        () -> feedback.send(
                                sender,
                                PresenceMessageKey.WHOIS_RESULT_ELSEWHERE,
                                Map.of(
                                        "name", elsewhere.player().name(),
                                        "uuid", elsewhere.player().uuid().toString(),
                                        "server", elsewhere.server())));
                return;
            }
            if (!(found instanceof VisibleNameMatch.Here(Player match))) {
                replyOnSenderThread(
                        sender,
                        () -> feedback.send(sender, PresenceMessageKey.WHOIS_NOT_FOUND, Map.of("query", query)));
                return;
            }
            PlayerRef matchRef = BukkitRefs.toRef(match);
            scheduler.onEntity(matchRef, () -> {
                Player live = Bukkit.getPlayer(matchRef.uuid());
                if (live == null || !live.isOnline()) {
                    replyOnSenderThread(
                            sender,
                            () -> feedback.send(sender, PresenceMessageKey.WHOIS_NOT_FOUND, Map.of("query", query)));
                    return;
                }
                Map<String, String> summary = summary(live);
                replyOnSenderThread(sender, () -> feedback.send(sender, PresenceMessageKey.WHOIS_RESULT, summary));
            });
        });
        return Command.SINGLE_SUCCESS;
    }

    /** The placeholder bundle the result line renders: identity plus live status fields, read on the target's thread. */
    private static Map<String, String> summary(Player match) {
        return Map.of(
                "name", match.getName(),
                "display", PLAIN.serialize(match.displayName()),
                "uuid", match.getUniqueId().toString(),
                "gamemode", match.getGameMode().name(),
                "health", String.valueOf((int) Math.round(match.getHealth())),
                "ping", String.valueOf(match.getPing()),
                "world", match.getWorld().getName());
    }
}
