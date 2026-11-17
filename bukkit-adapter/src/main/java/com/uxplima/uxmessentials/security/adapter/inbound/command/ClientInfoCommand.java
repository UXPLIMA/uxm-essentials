package com.uxplima.uxmessentials.security.adapter.inbound.command;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.command.CommandSender;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.security.adapter.NetworkClientBrands;
import com.uxplima.uxmessentials.security.domain.SecurityMessageKey;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import com.uxplima.uxmessentials.shared.application.permission.EnforcementPermissions;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.NameMatch;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.message.MessageLine;
import com.uxplima.uxmessentials.shared.domain.message.SharedMessageKey;
import com.uxplima.uxmlib.command.Gate;
import com.uxplima.uxmlib.command.Sender;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /clientinfo <player>}: report the client brand a player reported this session, the staff read behind the
 * ClientID guard. Gated on {@code uxmessentials.security.clientinfo} (staff). The target is resolved online-first
 * and otherwise from the profile cache; the recorded brand is session state held in the in-memory
 * {@link ClientBrandRegistry}, so an offline (or never-seen) player reports no brand. The lookup runs off the tick
 * thread; the registry read is a lock-free map lookup.
 */
@NullMarked
public final class ClientInfoCommand extends SecurityCommandSupport implements CommandRegistration {

    /** The staff permission to inspect a player's reported client brand. */
    public static final String PERMISSION = EnforcementPermissions.SECURITY_CLIENTINFO;

    private final NetworkClientBrands registry;
    private final PlayerLookup lookup;

    public ClientInfoCommand(
            NetworkClientBrands registry,
            PlayerLookup lookup,
            Scheduler scheduler,
            Messages messages,
            MessageSink sink) {
        super(scheduler, messages, sink);
        this.registry = Objects.requireNonNull(registry, "registry");
        this.lookup = Objects.requireNonNull(lookup, "lookup");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("clientinfo")
                .requires(Gate.permission(PERMISSION))
                .then(CommandSuggestions.playerArgument("player").executes(this::run))
                .build();
    }

    @Override
    public String description() {
        return "Show the client brand a player reported.";
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = Sender.audience(ctx.getSource());
        String name = ctx.getArgument("player", String.class);
        scheduler.async(() -> report(sender, name));
        return Command.SINGLE_SUCCESS;
    }

    private void report(CommandSender sender, String name) {
        NameMatch match = lookup.matchByName(name);
        Optional<PlayerRef> target = match.player();
        if (target.isEmpty()) {
            MessageLine miss = match.miss(SharedMessageKey.COMMAND_UNKNOWN_PLAYER, Map.of("player", name));
            notifySender(sender, miss.key(), miss.args());
            return;
        }
        PlayerRef found = target.get();
        // A player on another backend is asked about there; the answer arrives off the tick like this read.
        var unused = registry.brandOf(found).thenAccept(brand -> tell(sender, found, brand));
    }

    private void tell(CommandSender sender, PlayerRef found, Optional<String> brand) {
        if (brand.isEmpty()) {
            notifySender(sender, SecurityMessageKey.SECURITY_CLIENT_UNKNOWN, Map.of("player", found.name()));
            return;
        }
        notifySender(
                sender, SecurityMessageKey.SECURITY_CLIENT_INFO, Map.of("player", found.name(), "brand", brand.get()));
    }
}
