package com.uxplima.uxmessentials.teleport.adapter.inbound.command;

import java.util.Objects;
import java.util.Optional;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.PlayerArgument;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.message.SharedMessageKey;
import com.uxplima.uxmessentials.teleport.adapter.TeleportRefs;
import com.uxplima.uxmessentials.teleport.adapter.TeleportServices;
import com.uxplima.uxmessentials.teleport.domain.RequestDirection;
import com.uxplima.uxmessentials.teleport.domain.TeleportError;
import com.uxplima.uxmessentials.teleport.domain.TeleportRequest;
import com.uxplima.uxmlib.command.Gate;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /tpa <player>} and {@code /tpahere <player>}: open a teleport request in a fixed
 * {@link RequestDirection}. The selector resolves to one online player; the request itself, the
 * toggle/block/self gates, and both notices are the {@link com.uxplima.uxmessentials.teleport.application.RequestTeleport}
 * use case's job: this command only maps the argument and the invoking player.
 */
@NullMarked
public final class TpaRequestCommand extends TeleportCommandSupport implements CommandRegistration {

    private final String literal;
    private final String permission;
    private final String description;
    private final RequestDirection direction;

    public TpaRequestCommand(
            TeleportServices services,
            Messages messages,
            String literal,
            String permission,
            String description,
            RequestDirection direction) {
        super(services, messages);
        this.literal = Objects.requireNonNull(literal, "literal");
        this.permission = Objects.requireNonNull(permission, "permission");
        this.description = Objects.requireNonNull(description, "description");
        this.direction = Objects.requireNonNull(direction, "direction");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal(literal)
                .requires(Gate.permission(permission))
                .then(Commands.argument("player", PlayerArgument.player())
                        .suggests(CommandSuggestions.singlePlayerTarget())
                        .executes(this::run))
                .build();
    }

    @Override
    public String description() {
        return description;
    }

    private int run(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        Optional<Player> resolved;
        if (services.crossServerRequests().enabled()) {
            resolved = resolveHere(ctx, "player");
            Optional<String> elsewhere = resolved.isEmpty() ? typedName(ctx, "player") : Optional.empty();
            if (elsewhere.isPresent()) {
                // Nobody here by that name: the request goes to the backend the player is on, if any.
                services.crossServerRequests().request(ref(sender), elsewhere.get(), direction);
                return Command.SINGLE_SUCCESS;
            }
        }
        resolved = resolveHere(ctx, "player");
        if (resolved.isEmpty()) {
            services.notifier()
                    .send(ref(sender), PlayerArgument.miss(ctx, "player", SharedMessageKey.COMMAND_UNKNOWN_PLAYER));
            return 0;
        }
        Player targetPlayer = resolved.get();
        PlayerRef target = ref(targetPlayer);
        Result<TeleportRequest, TeleportError> opened =
                services.requestTeleport().request(ref(sender), target, direction);
        if (opened.isOk() && services.flags().autoAccepts(target)) {
            autoAccept(target, targetPlayer);
        }
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Auto-accept the request just opened: the target has {@code /tpauto} on, so accept it immediately rather
     * than queuing. The acceptor's anchor is the target's live position, read on the target's region thread; the
     * accept still drives the mover's warmup, so the move-cancels-warmup invariant is unaffected.
     */
    private void autoAccept(PlayerRef target, Player targetPlayer) {
        services.scheduler()
                .onEntity(
                        target, () -> services.acceptTeleport().accept(target, TeleportRefs.positionOf(targetPlayer)));
    }
}
