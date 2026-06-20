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
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /pwarp <name> [owner]}: teleport to a player-warp. With no owner the sender warps to their own warp;
 * with an owner they warp to that player's warp, permitted only when it is public.
 */
@NullMarked
public final class PlayerWarpCommand extends PlayerWarpCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.pwarp.use";
    private static final String PUBLIC_PERMISSION = "uxmessentials.pwarp.public";

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
                .then(Commands.literal("lock")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(CommandSuggestions.forPlayer(services::ownWarpNames))
                                .executes(this::toggleLock)))
                .then(Commands.literal("password")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(CommandSuggestions.forPlayer(services::ownWarpNames))
                                .executes(this::setPasswordClear)
                                .then(Commands.argument("password", StringArgumentType.word())
                                        .executes(this::setPassword))))
                .then(Commands.literal("edit")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(CommandSuggestions.forPlayer(services::ownWarpNames))
                                .executes(this::openPlayerWarpEditor)))
                .then(Commands.literal("rate")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.argument("owner", StringArgumentType.word())
                                        .suggests(CommandSuggestions.onlinePlayers())
                                        .then(Commands.argument(
                                                        "rating",
                                                        com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(
                                                                1.0, 5.0))
                                                .executes(this::ratePlayerWarp)))))
                .then(Commands.literal("rating")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.argument("owner", StringArgumentType.word())
                                        .suggests(CommandSuggestions.onlinePlayers())
                                        .executes(this::getPlayerWarpRating))))
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests(CommandSuggestions.forPlayer(services::ownWarpNames))
                        .executes(this::run)
                        .then(Commands.argument("arg1", StringArgumentType.word())
                                .suggests(CommandSuggestions.onlinePlayers())
                                .executes(this::runWithOneArg)
                                .then(Commands.argument("arg2", StringArgumentType.word())
                                        .executes(this::runWithTwoArgs))))
                .build();
    }

    @Override
    public String description() {
        return "Teleport to your own or a player's public warp, lock it, edit its settings, or set a password.";
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
            boolean exists = services.repository().exists(owner, PlayerWarpName.of(name));
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
        services.scheduler().async(() -> services.usePlayerWarp().use(who, warp));
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
        services.scheduler().async(() -> {
            if (owner.isPresent()) {
                services.usePlayerWarp().useFor(who, owner.get(), PlayerWarpName.of(warpName));
            } else {
                services.usePlayerWarp().use(who, PlayerWarpName.of(warpName), arg1);
            }
        });
        return Command.SINGLE_SUCCESS;
    }

    private int runWithTwoArgs(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        PlayerRef who = ref(sender);
        String warpName = ctx.getArgument("name", String.class);
        String ownerName = ctx.getArgument("arg1", String.class);
        String password = ctx.getArgument("arg2", String.class);

        Optional<PlayerRef> owner = services.players().findByName(ownerName);
        if (owner.isEmpty()) {
            unknownPlayer(ctx.getSource().getSender(), ownerName);
            return 0;
        }
        services.scheduler()
                .async(() -> services.usePlayerWarp()
                        .useFor(who, owner.get(), PlayerWarpName.of(warpName), Optional.of(password)));
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

    private int toggleLock(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        PlayerRef who = ref(sender);
        String warpName = ctx.getArgument("name", String.class);
        // The find + save both touch the database; run them off the tick thread, then bridge the confirmation.
        services.scheduler().async(() -> {
            Optional<PlayerWarp> opt = services.repository().find(who, PlayerWarpName.of(warpName));
            if (opt.isEmpty()) {
                services.usePlayerWarp().use(who, PlayerWarpName.of(warpName));
                return;
            }
            PlayerWarp updated = opt.get().withLocked(!opt.get().isLocked());
            services.repository().save(updated);
            onPlayer(
                    who,
                    () -> feedback.send(
                            sender,
                            PlayerwarpsMessageKey.PWARP_LOCK_TOGGLED,
                            Map.of("warp", warpName, "state", Boolean.toString(updated.isLocked()))));
        });
        return Command.SINGLE_SUCCESS;
    }

    private int setPasswordClear(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        PlayerRef who = ref(sender);
        String warpName = ctx.getArgument("name", String.class);
        services.scheduler().async(() -> {
            Optional<PlayerWarp> opt = services.repository().find(who, PlayerWarpName.of(warpName));
            if (opt.isEmpty()) {
                services.usePlayerWarp().use(who, PlayerWarpName.of(warpName));
                return;
            }
            services.repository().save(opt.get().withPassword(Optional.empty()));
            onPlayer(
                    who,
                    () -> feedback.send(
                            sender, PlayerwarpsMessageKey.PWARP_PASSWORD_CLEARED, Map.of("warp", warpName)));
        });
        return Command.SINGLE_SUCCESS;
    }

    private int setPassword(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        PlayerRef who = ref(sender);
        String warpName = ctx.getArgument("name", String.class);
        String password = ctx.getArgument("password", String.class);
        services.scheduler().async(() -> {
            Optional<PlayerWarp> opt = services.repository().find(who, PlayerWarpName.of(warpName));
            if (opt.isEmpty()) {
                services.usePlayerWarp().use(who, PlayerWarpName.of(warpName));
                return;
            }
            services.repository().save(opt.get().withPassword(Optional.of(password)));
            onPlayer(
                    who,
                    () -> feedback.send(
                            sender,
                            PlayerwarpsMessageKey.PWARP_PASSWORD_SET,
                            Map.of("warp", warpName, "password", password)));
        });
        return Command.SINGLE_SUCCESS;
    }

    private int ratePlayerWarp(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        PlayerRef rater = ref(sender);
        String warpName = ctx.getArgument("name", String.class);
        String ownerName = ctx.getArgument("owner", String.class);
        double rating = ctx.getArgument("rating", Double.class);

        Optional<PlayerRef> ownerOpt = services.players().findByName(ownerName);
        if (ownerOpt.isEmpty()) {
            feedback.send(sender, PlayerwarpsMessageKey.PWARP_NOT_FOUND, Map.of("warp", warpName));
            return 0;
        }
        PlayerRef owner = ownerOpt.get();
        // The existence + visibility check and the rating write all touch the database; run them off-thread.
        services.scheduler().async(() -> {
            Optional<PlayerWarp> warpOpt = services.repository().find(owner, PlayerWarpName.of(warpName));
            if (warpOpt.isEmpty()) {
                onPlayer(
                        rater,
                        () -> feedback.send(sender, PlayerwarpsMessageKey.PWARP_NOT_FOUND, Map.of("warp", warpName)));
                return;
            }
            if (!warpOpt.get().isPublic() && !owner.uuid().equals(rater.uuid())) {
                onPlayer(
                        rater,
                        () -> feedback.send(sender, PlayerwarpsMessageKey.PWARP_NOT_PUBLIC, Map.of("warp", warpName)));
                return;
            }
            services.repository().rate(owner, PlayerWarpName.of(warpName), rater.uuid(), rating);
            onPlayer(
                    rater,
                    () -> feedback.send(
                            sender,
                            PlayerwarpsMessageKey.PWARP_RATED,
                            Map.of("warp", warpName, "rating", Double.toString(rating))));
        });
        return Command.SINGLE_SUCCESS;
    }

    private int getPlayerWarpRating(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        PlayerRef viewer = ref(sender);
        String warpName = ctx.getArgument("name", String.class);
        String ownerName = ctx.getArgument("owner", String.class);

        Optional<PlayerRef> ownerOpt = services.players().findByName(ownerName);
        if (ownerOpt.isEmpty()) {
            feedback.send(sender, PlayerwarpsMessageKey.PWARP_NOT_FOUND, Map.of("warp", warpName));
            return 0;
        }
        PlayerRef owner = ownerOpt.get();
        // The existence check and the rating read both touch the database; run them off the tick thread.
        services.scheduler().async(() -> {
            if (!services.repository().exists(owner, PlayerWarpName.of(warpName))) {
                onPlayer(
                        viewer,
                        () -> feedback.send(sender, PlayerwarpsMessageKey.PWARP_NOT_FOUND, Map.of("warp", warpName)));
                return;
            }
            double avg = services.repository().averageRating(owner, PlayerWarpName.of(warpName));
            onPlayer(
                    viewer,
                    () -> feedback.send(
                            sender,
                            PlayerwarpsMessageKey.PWARP_RATING,
                            Map.of("warp", warpName, "rating", oneDecimal(avg))));
        });
        return Command.SINGLE_SUCCESS;
    }

    private static String oneDecimal(double value) {
        return java.math.BigDecimal.valueOf(value)
                .setScale(1, java.math.RoundingMode.HALF_UP)
                .toPlainString();
    }
}
