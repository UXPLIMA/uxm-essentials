package com.uxplima.uxmessentials.presence.adapter.inbound.command;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.presence.adapter.PresenceServices;
import com.uxplima.uxmessentials.presence.domain.PresenceMessageKey;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.permission.SocialPermissions;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.NameMatch;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.command.Args;
import com.uxplima.uxmlib.command.Gate;
import com.uxplima.uxmlib.command.Sender;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /nick <name>} sets the invoking player's display name, {@code /nick off} (or {@code /nick clear}) clears
 * it back to the account name, and {@code /nick <player> <name>} sets another player's (gated by
 * {@code uxmessentials.nick.others}). The validation, the stamp, and the confirmation are the
 * {@code SetNick}/{@code ClearNick} use cases' job; this handler only maps the arguments and resolves a named
 * target, online here, on another server or offline, through the player lookup off the tick. The nick is kept in the
 * database and shown on whichever server the player is on, so the command stays a thin argument mapper.
 *
 * <p>The first {@code name} argument is overloaded: alone it is the sender's own nick (or a clear keyword), and
 * in the two-argument form it names the target player. Its completion reflects that. It offers the sender's own
 * account name (so {@code /nick} round-trips to your own name for an edit), the online roster (to pick a target),
 * and the {@code off}/{@code clear} clear keywords. The {@code clear} and {@code off} literal subcommands make the
 * clear path discoverable in tab-completion without losing the {@code /nick off} muscle-memory the keyword keeps.
 */
@NullMarked
public final class NickCommand extends PresenceCommandSupport implements CommandRegistration {

    private static final String PERMISSION = SocialPermissions.NICK_USE;
    private static final String OTHERS_PERMISSION = SocialPermissions.NICK_OTHERS;
    private static final String OFF = "off";
    private static final String CLEAR = "clear";

    private final PlayerLookup players;

    public NickCommand(PresenceServices services, Messages messages, Scheduler scheduler, PlayerLookup players) {
        super(services, messages, scheduler);
        this.players = java.util.Objects.requireNonNull(players, "players");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("nick")
                .requires(Gate.permission(PERMISSION))
                .then(Commands.literal(CLEAR).executes(this::runClear))
                .then(Commands.literal(OFF).executes(this::runClear))
                .then(Commands.argument("name", Args.token())
                        .suggests(nameSuggestions())
                        .executes(this::runSelf)
                        .then(Commands.argument("value", Args.token())
                                .requires(Gate.permission(OTHERS_PERMISSION))
                                .executes(this::runOther)))
                .build();
    }

    @Override
    public List<String> aliases() {
        return List.of();
    }

    @Override
    public String description() {
        return "Set or clear a player's display name.";
    }

    private int runClear(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        services.clearNick().clear(ref(sender));
        return Command.SINGLE_SUCCESS;
    }

    private int runSelf(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        String name = StringArgumentType.getString(ctx, "name");
        PlayerRef self = ref(sender);
        if (name.equalsIgnoreCase(OFF)) {
            services.clearNick().clear(self);
            return Command.SINGLE_SUCCESS;
        }
        services.setNick().set(self, self, name);
        return Command.SINGLE_SUCCESS;
    }

    private int runOther(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        PlayerRef actor = ref(sender);
        String targetName = StringArgumentType.getString(ctx, "name");
        String nick = StringArgumentType.getString(ctx, "value");
        // The target may be offline or on another server: the name is settled off the tick, where the shared name
        // store may be read.
        scheduler.async(() -> {
            NameMatch match = players.matchByName(targetName);
            Optional<PlayerRef> target = match.player();
            if (target.isEmpty()) {
                replyOnSenderThread(
                        sender,
                        () -> feedback.send(
                                sender,
                                match.miss(PresenceMessageKey.NICK_TARGET_UNKNOWN, Map.of("player", targetName))));
                return;
            }
            if (nick.equalsIgnoreCase(OFF)) {
                services.clearNick().clear(actor, target.get());
                return;
            }
            services.setNick().set(actor, target.get(), nick);
        });
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Completes the overloaded first argument: the sender's own account name (so they can round-trip to their
     * real name for an edit), every online player (the targets the two-argument form acts on), and the
     * {@code off}/{@code clear} clear keywords. Brigadier invokes this on the command thread as the player types,
     * so it reads only in-memory state (the sender pulled from the source and the live roster) and never blocks.
     * The candidates collect into a set so a player who shares the sender's name (the sender themselves) is
     * offered once, and a prefix filter narrows them to the token typed so far.
     */
    private static SuggestionProvider<CommandSourceStack> nameSuggestions() {
        return (ctx, builder) -> {
            Set<String> candidates = new LinkedHashSet<>();
            CommandSender sender = Sender.audience(ctx.getSource());
            if (sender instanceof Player self) {
                candidates.add(self.getName());
            }
            for (Player online : Bukkit.getOnlinePlayers()) {
                candidates.add(online.getName());
            }
            candidates.add(OFF);
            candidates.add(CLEAR);
            String prefix = builder.getRemaining().toLowerCase(Locale.ROOT);
            for (String candidate : candidates) {
                if (prefix.isEmpty() || candidate.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    builder.suggest(candidate);
                }
            }
            return builder.buildFuture();
        };
    }
}
