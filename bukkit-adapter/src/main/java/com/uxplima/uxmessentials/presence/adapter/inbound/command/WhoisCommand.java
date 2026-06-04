package com.uxplima.uxmessentials.presence.adapter.inbound.command;

import java.util.List;
import java.util.Map;

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
import com.uxplima.uxmessentials.presence.application.PresenceMessageKey;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * {@code /whois <player>} ({@code uxmessentials.whois.use}): a staff identity and status summary for one online
 * player — account name, display name, uuid, gamemode, health, ping and world in a single line. The query
 * matches either a player's account name or their rendered display name, case-insensitively, against the online
 * set filtered through the sender's {@code canSee} graph — the same seam {@code /list} and {@code /realname} read
 * — so a vanished player the sender may not see is unresolvable, never leaking details they could not otherwise
 * learn. The console has no {@code canSee} graph and may resolve anyone. A pure read: no use case, no state
 * mutation, just a scan of the visible online set and one resolved reply.
 */
@NullMarked
public final class WhoisCommand extends PresenceCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.whois.use";
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    public WhoisCommand(PresenceServices services, Messages messages) {
        super(services, messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("whois")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
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
        CommandSender sender = ctx.getSource().getSender();
        String query = StringArgumentType.getString(ctx, "player");
        Player match = findVisibleMatch(sender, query);
        if (match == null) {
            feedback.send(sender, PresenceMessageKey.WHOIS_NOT_FOUND, Map.of("query", query));
            return Command.SINGLE_SUCCESS;
        }
        feedback.send(sender, PresenceMessageKey.WHOIS_RESULT, summary(match));
        return Command.SINGLE_SUCCESS;
    }

    /** The placeholder bundle the result line renders: identity plus live status fields. */
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

    /** First online player the sender may see whose account or display name matches {@code query}, else {@code null}. */
    private @Nullable Player findVisibleMatch(CommandSender sender, String query) {
        Player viewer = sender instanceof Player player ? player : null;
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (viewer != null && !viewer.canSee(target)) {
                continue;
            }
            if (target.getName().equalsIgnoreCase(query)
                    || PLAIN.serialize(target.displayName()).equalsIgnoreCase(query)) {
                return target;
            }
        }
        return null;
    }
}
