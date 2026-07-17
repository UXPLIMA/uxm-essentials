package com.uxplima.uxmessentials.security.adapter.inbound.command;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.security.application.FindAlts;
import com.uxplima.uxmessentials.security.application.SecurityMessageKey;
import com.uxplima.uxmessentials.security.domain.AltGroup;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import com.uxplima.uxmessentials.shared.application.message.SharedMessageKey;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /alts <player>}: list the accounts that share an IP with a target — the staff read behind the same-IP alt
 * guard. Gated on {@code uxmessentials.security.alts} (staff). The target is resolved online-first and otherwise
 * from the profile cache, so an offline account is still checkable; the lookup, the DB read, and the grouping all
 * run off the tick thread through the {@link Scheduler}. Each alt's name is resolved from its stored UUID, falling
 * back to the raw UUID for an account the server has never seen.
 */
@NullMarked
public final class AltsCommand extends SecurityCommandSupport implements CommandRegistration {

    /** The staff permission to inspect a player's same-IP alts. */
    public static final String PERMISSION = "uxmessentials.security.alts";

    private final FindAlts findAlts;
    private final PlayerLookup lookup;

    public AltsCommand(
            FindAlts findAlts, PlayerLookup lookup, Scheduler scheduler, Messages messages, MessageSink sink) {
        super(scheduler, messages, sink);
        this.findAlts = Objects.requireNonNull(findAlts, "findAlts");
        this.lookup = Objects.requireNonNull(lookup, "lookup");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("alts")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(CommandSuggestions.playerArgument("player").executes(this::run))
                .build();
    }

    @Override
    public String description() {
        return "List the accounts that share an IP with a player.";
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        PlayerRef viewer = viewer(sender);
        String name = ctx.getArgument("player", String.class);
        scheduler.async(() -> report(viewer, name));
        return Command.SINGLE_SUCCESS;
    }

    private void report(PlayerRef viewer, String name) {
        Optional<PlayerRef> target = lookup.findByName(name);
        if (target.isEmpty()) {
            notify(viewer, SharedMessageKey.COMMAND_UNKNOWN_PLAYER, Map.of("player", name));
            return;
        }
        PlayerRef found = target.get();
        AltGroup group = findAlts.find(found.uuid());
        if (group.isEmpty()) {
            notify(viewer, SecurityMessageKey.SECURITY_ALTS_NONE, Map.of("player", found.name()));
            return;
        }
        notify(
                viewer,
                SecurityMessageKey.SECURITY_ALTS_HEADER,
                Map.of(
                        "player",
                        found.name(),
                        "count",
                        Integer.toString(group.alts().size())));
        for (UUID alt : group.alts()) {
            notify(viewer, SecurityMessageKey.SECURITY_ALTS_ENTRY, Map.of("alt", nameOf(alt)));
        }
    }

    private String nameOf(UUID account) {
        return lookup.findByUuid(account).map(PlayerRef::name).orElse(account.toString());
    }

    /** The viewer to deliver output to: the invoking player, or a stable system ref for a console sender. */
    private static PlayerRef viewer(CommandSender sender) {
        return sender instanceof Player player
                ? new PlayerRef(player.getUniqueId(), player.getName())
                : new PlayerRef(new UUID(0L, 0L), sender.getName());
    }
}
