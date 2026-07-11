package com.uxplima.uxmessentials.playerwarps.adapter.inbound.command;

import java.util.Map;
import java.util.Optional;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.playerwarps.adapter.PlayerWarpServices;
import com.uxplima.uxmessentials.playerwarps.application.PlayerwarpsMessageKey;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /pwarp <name> [owner]}: teleport to a player-warp. With no owner the sender warps to their own warp;
 * with an owner they warp to that player's warp, permitted only when it is public.
 *
 * <p>{@code /pwarp del <name>} archives one of the sender's own warps (recoverable — the name is retired from
 * listings but the warp survives), the folded-in counterpart of the visibility/lock/edit subcommands, gated by
 * {@code uxmessentials.pwarp.delete}. The {@link com.uxplima.uxmessentials.playerwarps.application.ArchivePlayerWarp}
 * use case rejects a missing name through the sink.
 */
@NullMarked
public final class PlayerWarpCommand extends PlayerWarpCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.pwarp.use";
    private static final String PUBLIC_PERMISSION = "uxmessentials.pwarp.public";
    private static final String DELETE_PERMISSION = "uxmessentials.pwarp.delete";

    public PlayerWarpCommand(PlayerWarpServices services, Messages messages) {
        super(services, messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("pwarp")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::openGui)
                .then(Commands.literal("visibility")
                        .requires(src -> src.getSender().hasPermission(PUBLIC_PERMISSION))
                        .then(Commands.literal("public")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .suggests(CommandSuggestions.forPlayer(services::ownWarpNames))
                                        .executes(this::makePublic)))
                        .then(Commands.literal("private")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .suggests(CommandSuggestions.forPlayer(services::ownWarpNames))
                                        .executes(this::makePrivate))))
                .then(Commands.literal("del")
                        .requires(src -> src.getSender().hasPermission(DELETE_PERMISSION))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(CommandSuggestions.forPlayer(services::ownWarpNames))
                                .executes(this::runDelete)))
                .then(Commands.literal("edit")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(CommandSuggestions.forPlayer(services::ownWarpNames))
                                .executes(this::openPlayerWarpEditor)))
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests(CommandSuggestions.forPlayer(services::ownWarpNames))
                        .executes(this::run)
                        .then(Commands.argument("arg1", StringArgumentType.word())
                                .suggests(CommandSuggestions.onlinePlayers())
                                .executes(this::runWithOneArg)))
                .build();
    }

    @Override
    public String description() {
        return "Teleport to your own or a player's public warp, edit its settings, or remove it.";
    }

    /**
     * {@code /pwarp} with no arguments: open the management list. A player sees and edits their own warps; a
     * holder of {@code uxmessentials.pwarp.gui} sees and manages every player's (the list re-checks the node per
     * open and scopes the entity set itself). The open is scheduled on the player's entity thread by the view.
     */
    private int openGui(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        services.listView().open(sender, ref(sender));
        return Command.SINGLE_SUCCESS;
    }

    private int openPlayerWarpEditor(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        String name = ctx.getArgument("name", String.class);
        PlayerRef owner = ref(sender);
        // The existence check reads the database; run it off the tick thread, then bridge the open (or the
        // not-found feedback) back to the player's region thread.
        services.scheduler().async(() -> {
            // Names are globally unique now, so resolve by name and confirm the sender owns it before editing.
            boolean exists = services.repository()
                    .findByName(PlayerWarpName.of(name))
                    .filter(warp -> warp.owner().uuid().equals(owner.uuid()))
                    .isPresent();
            onPlayer(owner, () -> {
                if (!exists) {
                    feedback.send(sender, PlayerwarpsMessageKey.PWARP_NOT_FOUND, Map.of("warp", name));
                    return;
                }
                if (services.editorView() != null) {
                    services.editorView().open(sender, owner, name, owner);
                }
            });
        });
        return Command.SINGLE_SUCCESS;
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        PlayerRef who = ref(sender);
        PlayerWarpName warp = PlayerWarpName.of(ctx.getArgument("name", String.class));
        // The use case reads the warp then delegates the hop to the teleport context; run the read off-thread.
        services.scheduler().async(() -> services.usePlayerWarp().useFor(who, warp));
        return Command.SINGLE_SUCCESS;
    }

    private int runDelete(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        PlayerRef who = ref(sender);
        PlayerWarpName name = PlayerWarpName.of(ctx.getArgument("name", String.class));
        // /pwarp del archives the warp by default (recoverable); it reads then writes, so run it off the tick thread.
        services.scheduler().async(() -> services.archivePlayerWarp().archive(who, name));
        return Command.SINGLE_SUCCESS;
    }

    private int runWithOneArg(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        PlayerRef who = ref(sender);
        String warpName = ctx.getArgument("name", String.class);
        String arg1 = ctx.getArgument("arg1", String.class);

        Optional<PlayerRef> owner = services.players().findByName(arg1);
        // Warp names are globally unique, so the owner argument is only a caller hint: with a resolved owner the
        // owner-scoped overload runs; without one the warp is still addressable by its name alone.
        services.scheduler().async(() -> {
            if (owner.isPresent()) {
                services.usePlayerWarp().useFor(who, owner.get(), PlayerWarpName.of(warpName));
            } else {
                services.usePlayerWarp().useFor(who, PlayerWarpName.of(warpName));
            }
        });
        return Command.SINGLE_SUCCESS;
    }

    private int makePublic(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        PlayerRef who = ref(sender);
        PlayerWarpName warp = PlayerWarpName.of(ctx.getArgument("name", String.class));
        // The use case reads the warp then writes its visibility; run the read off the tick thread.
        services.scheduler().async(() -> services.visibility().setPublic(who, warp));
        return Command.SINGLE_SUCCESS;
    }

    private int makePrivate(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        PlayerRef who = ref(sender);
        PlayerWarpName warp = PlayerWarpName.of(ctx.getArgument("name", String.class));
        services.scheduler().async(() -> services.visibility().setPrivate(who, warp));
        return Command.SINGLE_SUCCESS;
    }
}
