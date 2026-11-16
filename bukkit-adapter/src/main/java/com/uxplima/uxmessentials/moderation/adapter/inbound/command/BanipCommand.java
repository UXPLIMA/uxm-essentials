package com.uxplima.uxmessentials.moderation.adapter.inbound.command;

import java.util.Map;
import java.util.Optional;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.moderation.adapter.ModerationServices;
import com.uxplima.uxmessentials.moderation.adapter.inbound.gui.PunishmentAction;
import com.uxplima.uxmessentials.moderation.adapter.inbound.gui.PunishmentGuiFlow;
import com.uxplima.uxmessentials.moderation.application.BanIp;
import com.uxplima.uxmessentials.moderation.domain.ModerationMessageKey;
import com.uxplima.uxmessentials.moderation.domain.SeenRecord;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.permission.EnforcementPermissions;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.command.Gate;
import com.uxplima.uxmlib.command.Sender;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * {@code /banip <player|ip> [reason]}: ban by stored IP. When the argument is an IP literal it is banned
 * directly with no resolved UUID; when it is a player name, the player's last-seen IP is resolved from the
 * DB-backed seen record (a real indexed lookup) and the UUID recorded for a replayable audit line. The
 * alt-detection and audit are the {@code BanIp} use case's job. A player with no recorded IP yields a
 * not-found notice.
 *
 * <p>Bare {@code /banip} (no arguments) opens the player-picker → confirm GUI flow when the command's catalog
 * {@code gui} flag is on: {@link #guiRoot()} returns the opener and the shared {@code GuiRootBinding} installs it
 * as the root executor. The picker bans the chosen player's last-known address. The raw subcommand form (which
 * also accepts a literal IP) is unchanged, and the same {@code .requires} permission gate covers the opener.
 */
@NullMarked
public final class BanipCommand extends ModerationCommandSupport implements CommandRegistration {

    private static final String PERMISSION = EnforcementPermissions.MODERATION_BANIP;

    private final @Nullable PunishmentGuiFlow guiFlow;

    public BanipCommand(
            ModerationServices services, Messages messages, MessageSink sink, @Nullable PunishmentGuiFlow guiFlow) {
        super(services, messages, sink);
        this.guiFlow = guiFlow;
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("banip")
                .requires(Gate.permission(PERMISSION))
                .then(CommandSuggestions.playerArgument("target")
                        .executes(ctx -> run(ctx, Optional.empty()))
                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                                .executes(ctx -> run(ctx, optionalReason(ctx)))))
                .build();
    }

    @Override
    public String description() {
        return "Ban a player or IP by address.";
    }

    @Override
    public Optional<Command<CommandSourceStack>> guiRoot() {
        if (guiFlow == null) {
            return Optional.empty();
        }
        return Optional.of(ctx -> {
            if (Sender.audience(ctx.getSource()) instanceof Player sender) {
                guiFlow.open(sender, BukkitRefs.toRef(sender), PunishmentAction.BANIP);
            }
            return Command.SINGLE_SUCCESS;
        });
    }

    private int run(CommandContext<CommandSourceStack> ctx, Optional<String> reason) {
        PlayerRef actor = actor(ctx);
        String argument = ctx.getArgument("target", String.class);
        resolveTarget(ctx, argument, actor)
                .ifPresent(resolving -> offTick(
                        actor,
                        () -> resolving
                                .resolve()
                                .ifPresent(target -> services.banIp().banIp(actor, target, "", reason))));
        return Command.SINGLE_SUCCESS;
    }

    /**
     * How the command finds the address to ban, decided on the command thread: an address typed is used as it is, a
     * player name is looked up here and their last address read from their seen record on the async lane. Empty, after
     * a line to the sender, when the argument is neither.
     */
    private Optional<Resolving> resolveTarget(
            CommandContext<CommandSourceStack> ctx, String argument, PlayerRef actor) {
        if (IpLiteral.looksLikeAddress(argument)) {
            Optional<String> ip = IpLiteral.canonical(argument);
            if (ip.isEmpty()) {
                notify(ctx, ModerationMessageKey.BANIP_INVALID_IP, Map.of());
            }
            return ip.map(address -> () -> Optional.of(new BanIp.Target(address, Optional.empty())));
        }
        return targetByName(ctx, argument).map(player -> () -> byPlayer(actor, player));
    }

    /** The address a player was last seen on, read from storage; empty, after a line to the actor, when none. */
    private Optional<BanIp.Target> byPlayer(PlayerRef actor, PlayerRef player) {
        Optional<String> ip = services.repository().seen(player).flatMap(SeenRecord::lastIp);
        if (ip.isEmpty()) {
            tell(actor, ModerationMessageKey.SEENIP_NO_IP, Map.of("player", player.name()));
            return Optional.empty();
        }
        return Optional.of(new BanIp.Target(ip.get(), Optional.of(player.uuid())));
    }

    /** The address to ban, found on the async lane. */
    @FunctionalInterface
    private interface Resolving {
        Optional<BanIp.Target> resolve();
    }
}
