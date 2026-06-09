package com.uxplima.uxmessentials.warps.adapter.inbound.command;

import java.util.Map;
import java.util.Optional;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.warps.adapter.WarpServices;
import com.uxplima.uxmessentials.warps.application.WarpsMessageKey;
import com.uxplima.uxmessentials.warps.domain.Warp;
import com.uxplima.uxmessentials.warps.domain.WarpName;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /warp <name> [player]}: teleport to a server warp. With no trailing player the sender warps
 * themselves; with one, and the {@code uxmessentials.warp.others} node, staff send that player to the warp
 * instead.
 */
@NullMarked
public final class WarpCommand extends WarpCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.warp.use";
    private static final String OTHERS_PERMISSION = "uxmessentials.warp.others";
    private static final String LOCK_PERMISSION = "uxmessentials.warp.lock";
    private static final String PASSWORD_PERMISSION = "uxmessentials.warp.password";
    private static final String EDIT_PERMISSION = "uxmessentials.warp.edit";

    public WarpCommand(WarpServices services, Messages messages) {
        super(services, messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("warp")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(Commands.literal("lock")
                        .requires(src -> src.getSender().hasPermission(LOCK_PERMISSION))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(CommandSuggestions.forPlayer(this::usableWarpNames))
                                .executes(this::toggleLock)))
                .then(Commands.literal("password")
                        .requires(src -> src.getSender().hasPermission(PASSWORD_PERMISSION))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(CommandSuggestions.forPlayer(this::usableWarpNames))
                                .executes(this::setPasswordClear)
                                .then(Commands.argument("password", StringArgumentType.word())
                                        .executes(this::setPassword))))
                .then(Commands.literal("rate")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(CommandSuggestions.forPlayer(this::usableWarpNames))
                                .then(Commands.argument(
                                                "rating",
                                                com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(1.0, 5.0))
                                        .executes(this::rateWarp))))
                .then(Commands.literal("rating")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(CommandSuggestions.forPlayer(this::usableWarpNames))
                                .executes(this::getWarpRating)))
                .then(Commands.literal("edit")
                        .requires(src -> src.getSender().hasPermission(EDIT_PERMISSION))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(CommandSuggestions.forPlayer(this::usableWarpNames))
                                .executes(this::openWarpEditor)))
                .then(warpNameArgument()
                        .executes(this::run)
                        .then(Commands.argument("arg", StringArgumentType.word())
                                .suggests(CommandSuggestions.onlinePlayers())
                                .executes(this::runWithArg)))
                .build();
    }

    @Override
    public String description() {
        return "Teleport to a server warp, lock/unlock it, edit its settings, or set/clear its password.";
    }

    private int openWarpEditor(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        String name = ctx.getArgument("name", String.class);
        if (!services.repository().exists(WarpName.of(name))) {
            feedback.send(sender, WarpsMessageKey.WARP_NOT_FOUND, Map.of("warp", name));
            return 0;
        }
        if (services.editorView() != null) {
            services.editorView().open(sender, ref(sender), name, null);
        }
        return Command.SINGLE_SUCCESS;
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        services.useWarp().use(ref(sender), WarpName.of(ctx.getArgument("name", String.class)));
        return Command.SINGLE_SUCCESS;
    }

    private int runWithArg(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        String warpName = ctx.getArgument("name", String.class);
        String arg = ctx.getArgument("arg", String.class);

        if (sender.hasPermission(OTHERS_PERMISSION)) {
            Optional<PlayerRef> target = services.players().findOnlineByName(arg);
            if (target.isPresent()) {
                services.useWarp().useFor(ref(sender), target.get(), WarpName.of(warpName));
                return Command.SINGLE_SUCCESS;
            }
        }

        services.useWarp().use(ref(sender), WarpName.of(warpName), arg);
        return Command.SINGLE_SUCCESS;
    }

    private int toggleLock(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        String warpName = ctx.getArgument("name", String.class);
        Optional<Warp> opt = services.repository().find(WarpName.of(warpName));
        if (opt.isEmpty()) {
            services.useWarp().use(ref(sender), WarpName.of(warpName));
            return 0;
        }
        Warp warp = opt.get();
        Warp updated = warp.withLocked(!warp.isLocked());
        services.repository().save(updated);

        feedback.send(
                sender,
                WarpsMessageKey.WARP_LOCK_TOGGLED,
                Map.of("warp", warpName, "state", Boolean.toString(updated.isLocked())));
        return Command.SINGLE_SUCCESS;
    }

    private int setPasswordClear(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        String warpName = ctx.getArgument("name", String.class);
        Optional<Warp> opt = services.repository().find(WarpName.of(warpName));
        if (opt.isEmpty()) {
            services.useWarp().use(ref(sender), WarpName.of(warpName));
            return 0;
        }
        Warp warp = opt.get();
        Warp updated = warp.withPassword(Optional.empty());
        services.repository().save(updated);

        feedback.send(sender, WarpsMessageKey.WARP_PASSWORD_CLEARED, Map.of("warp", warpName));
        return Command.SINGLE_SUCCESS;
    }

    private int setPassword(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        String warpName = ctx.getArgument("name", String.class);
        String password = ctx.getArgument("password", String.class);
        Optional<Warp> opt = services.repository().find(WarpName.of(warpName));
        if (opt.isEmpty()) {
            services.useWarp().use(ref(sender), WarpName.of(warpName));
            return 0;
        }
        Warp warp = opt.get();
        Warp updated = warp.withPassword(Optional.of(password));
        services.repository().save(updated);

        feedback.send(sender, WarpsMessageKey.WARP_PASSWORD_SET, Map.of("warp", warpName, "password", password));
        return Command.SINGLE_SUCCESS;
    }

    private int rateWarp(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        String warpName = ctx.getArgument("name", String.class);
        double rating = ctx.getArgument("rating", Double.class);

        if (!services.repository().exists(WarpName.of(warpName))) {
            feedback.send(sender, WarpsMessageKey.WARP_NOT_FOUND, Map.of("warp", warpName));
            return 0;
        }

        services.repository().rate(WarpName.of(warpName), sender.getUniqueId(), rating);
        feedback.send(sender, WarpsMessageKey.WARP_RATED, Map.of("warp", warpName, "rating", Double.toString(rating)));
        return Command.SINGLE_SUCCESS;
    }

    private int getWarpRating(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        String warpName = ctx.getArgument("name", String.class);

        if (!services.repository().exists(WarpName.of(warpName))) {
            feedback.send(sender, WarpsMessageKey.WARP_NOT_FOUND, Map.of("warp", warpName));
            return 0;
        }

        double avg = services.repository().averageRating(WarpName.of(warpName));
        feedback.send(sender, WarpsMessageKey.WARP_RATING, Map.of("warp", warpName, "rating", oneDecimal(avg)));
        return Command.SINGLE_SUCCESS;
    }

    private static String oneDecimal(double value) {
        return java.math.BigDecimal.valueOf(value)
                .setScale(1, java.math.RoundingMode.HALF_UP)
                .toPlainString();
    }
}
