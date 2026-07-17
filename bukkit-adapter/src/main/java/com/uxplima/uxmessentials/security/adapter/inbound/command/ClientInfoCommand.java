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
import com.uxplima.uxmessentials.security.adapter.ClientBrandRegistry;
import com.uxplima.uxmessentials.security.application.SecurityMessageKey;
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
 * {@code /clientinfo <player>}: report the client brand a player reported this session — the staff read behind the
 * ClientID guard. Gated on {@code uxmessentials.security.clientinfo} (staff). The target is resolved online-first
 * and otherwise from the profile cache; the recorded brand is session state held in the in-memory
 * {@link ClientBrandRegistry}, so an offline (or never-seen) player reports no brand. The lookup runs off the tick
 * thread; the registry read is a lock-free map lookup.
 */
@NullMarked
public final class ClientInfoCommand extends SecurityCommandSupport implements CommandRegistration {

    /** The staff permission to inspect a player's reported client brand. */
    public static final String PERMISSION = "uxmessentials.security.clientinfo";

    private final ClientBrandRegistry registry;
    private final PlayerLookup lookup;

    public ClientInfoCommand(
            ClientBrandRegistry registry,
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
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(CommandSuggestions.playerArgument("player").executes(this::run))
                .build();
    }

    @Override
    public String description() {
        return "Show the client brand a player reported.";
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
        Optional<String> brand = registry.brandOf(found.uuid());
        if (brand.isEmpty()) {
            notify(viewer, SecurityMessageKey.SECURITY_CLIENT_UNKNOWN, Map.of("player", found.name()));
            return;
        }
        notify(viewer, SecurityMessageKey.SECURITY_CLIENT_INFO, Map.of("player", found.name(), "brand", brand.get()));
    }

    /** The viewer to deliver output to: the invoking player, or a stable system ref for a console sender. */
    private static PlayerRef viewer(CommandSender sender) {
        return sender instanceof Player player
                ? new PlayerRef(player.getUniqueId(), player.getName())
                : new PlayerRef(new UUID(0L, 0L), sender.getName());
    }
}
