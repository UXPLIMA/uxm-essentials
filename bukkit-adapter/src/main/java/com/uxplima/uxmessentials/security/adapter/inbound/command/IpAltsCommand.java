package com.uxplima.uxmessentials.security.adapter.inbound.command;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.bukkit.command.CommandSender;

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
 * {@code /ipalts <player>}: list the accounts that share an IP with a target — the staff read behind the same-IP alt
 * guard. It lives on {@code /ipalts} rather than {@code /alts} because the moderation context already owns the
 * top-level {@code /alts} (last-IP history over its own raw store); this one reads the security context's hashed
 * {@code security_ip} store instead, so the two never share a command literal. Gated on
 * {@code uxmessentials.security.alts} (staff). The target is resolved online-first and otherwise from the profile
 * cache, so an offline account is still checkable; the lookup, the DB read, and the grouping all run off the tick
 * thread through the {@link Scheduler}. Each alt's name is resolved from its stored UUID, falling back to the raw
 * UUID for an account the server has never seen.
 */
@NullMarked
public final class IpAltsCommand extends SecurityCommandSupport implements CommandRegistration {

    /** The staff permission to inspect a player's same-IP alts. */
    public static final String PERMISSION = "uxmessentials.security.alts";

    private final FindAlts findAlts;
    private final PlayerLookup lookup;

    public IpAltsCommand(
            FindAlts findAlts, PlayerLookup lookup, Scheduler scheduler, Messages messages, MessageSink sink) {
        super(scheduler, messages, sink);
        this.findAlts = Objects.requireNonNull(findAlts, "findAlts");
        this.lookup = Objects.requireNonNull(lookup, "lookup");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("ipalts")
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
        String name = ctx.getArgument("player", String.class);
        scheduler.async(() -> report(sender, name));
        return Command.SINGLE_SUCCESS;
    }

    private void report(CommandSender sender, String name) {
        Optional<PlayerRef> target = lookup.findByName(name);
        if (target.isEmpty()) {
            notifySender(sender, SharedMessageKey.COMMAND_UNKNOWN_PLAYER, Map.of("player", name));
            return;
        }
        PlayerRef found = target.get();
        AltGroup group = findAlts.find(found.uuid());
        // The core group excludes only the queried UUID, but in offline mode one human account can own two UUIDs
        // (an old premium id and the current offline id) recorded under the same IP, both resolving to the queried
        // name. Resolve names here (core does not), drop any that resolve to the queried account's own name, and
        // dedupe by resolved name so two UUIDs for one person show once; genuinely distinct alts still each show.
        List<String> altNames = distinctAltNames(group, found.name());
        if (altNames.isEmpty()) {
            notifySender(sender, SecurityMessageKey.SECURITY_ALTS_NONE, Map.of("player", found.name()));
            return;
        }
        notifySender(
                sender,
                SecurityMessageKey.SECURITY_ALTS_HEADER,
                Map.of("player", found.name(), "count", Integer.toString(altNames.size())));
        for (String altName : altNames) {
            notifySender(sender, SecurityMessageKey.SECURITY_ALTS_ENTRY, Map.of("alt", altName));
        }
    }

    /** Resolve each alt UUID to a name, drop any equal (case-insensitively) to {@code queriedName}, and dedupe. */
    private List<String> distinctAltNames(AltGroup group, String queriedName) {
        Set<String> seen = new LinkedHashSet<>();
        List<String> names = new ArrayList<>();
        for (UUID alt : group.alts()) {
            String altName = nameOf(alt);
            if (altName.equalsIgnoreCase(queriedName)) {
                continue;
            }
            if (seen.add(altName.toLowerCase(Locale.ROOT))) {
                names.add(altName);
            }
        }
        return names;
    }

    private String nameOf(UUID account) {
        return lookup.findByUuid(account).map(PlayerRef::name).orElse(account.toString());
    }
}
