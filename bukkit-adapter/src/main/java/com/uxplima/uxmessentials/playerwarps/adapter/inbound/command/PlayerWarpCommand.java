package com.uxplima.uxmessentials.playerwarps.adapter.inbound.command;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.playerwarps.adapter.PlayerWarpServices;
import com.uxplima.uxmessentials.playerwarps.application.PlayerwarpsMessageKey;
import com.uxplima.uxmessentials.playerwarps.domain.DisplayName;
import com.uxplima.uxmessentials.playerwarps.domain.IconSpec;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.playerwarps.domain.WarpAccess;
import com.uxplima.uxmessentials.playerwarps.domain.WarpDescription;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.warps.domain.WarpCost;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /pwarp}: the single entry point for everything a player does with their own player-warps, structured as a
 * Brigadier subcommand tree (the same idiom {@code /home} uses). With no arguments it opens the management GUI; a
 * bare {@code <name>} teleports; the folded-in verbs cover the whole self-service surface — create/move, archive,
 * rename, the metadata/access/price edits, the social verbs (rate, favourite), transfer, withdraw, info, and list.
 *
 * <p>Every subcommand is gated by its own {@code uxmessentials.pwarp.<verb>} node through Brigadier
 * {@code .requires(...)}, so only {@code pwarp} is a command-catalog id; the verbs are literals inside the tree
 * (management/admin verbs land in the next task). Each handler resolves any tick-thread state — the actor's
 * {@link PlayerRef}, and their {@link Position} for {@code set}/{@code move} — then hands the repository I/O to the
 * injected scheduler and returns immediately, so the command thread never blocks. A malformed name is turned into a
 * friendly notice, never a stack trace, and the teleport password is threaded straight to the access gate: it is
 * never echoed, logged, or tab-completed.
 */
@NullMarked
public final class PlayerWarpCommand extends PlayerWarpCommandSupport implements CommandRegistration {

    private static final String USE_PERMISSION = "uxmessentials.pwarp.use";
    private static final String PUBLIC_PERMISSION = "uxmessentials.pwarp.public";
    private static final String DELETE_PERMISSION = "uxmessentials.pwarp.delete";
    private static final String SET_PERMISSION = "uxmessentials.pwarp.set";
    private static final String MOVE_PERMISSION = "uxmessentials.pwarp.move";
    private static final String RENAME_PERMISSION = "uxmessentials.pwarp.rename";
    private static final String DISPLAYNAME_PERMISSION = "uxmessentials.pwarp.displayname";
    private static final String DESCRIPTION_PERMISSION = "uxmessentials.pwarp.description";
    private static final String ICON_PERMISSION = "uxmessentials.pwarp.icon";
    private static final String CATEGORY_PERMISSION = "uxmessentials.pwarp.category";
    private static final String ACCESS_PERMISSION = "uxmessentials.pwarp.access";
    private static final String PASSWORD_PERMISSION = "uxmessentials.pwarp.password";
    private static final String PRICE_PERMISSION = "uxmessentials.pwarp.price";
    private static final String INFO_PERMISSION = "uxmessentials.pwarp.info";
    private static final String LIST_PERMISSION = "uxmessentials.pwarp.list";
    private static final String FAVOURITE_PERMISSION = "uxmessentials.pwarp.favourite";
    private static final String RATE_PERMISSION = "uxmessentials.pwarp.rate";
    private static final String TRANSFER_PERMISSION = "uxmessentials.pwarp.transfer";
    private static final String WITHDRAW_PERMISSION = "uxmessentials.pwarp.withdraw";

    public PlayerWarpCommand(PlayerWarpServices services, Messages messages) {
        super(services, messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("pwarp")
                .requires(src -> src.getSender().hasPermission(USE_PERMISSION))
                .executes(this::openGui)
                .then(visibilitySubtree())
                .then(Commands.literal("edit").then(nameArg().executes(this::openPlayerWarpEditor)))
                .then(verb("del", DELETE_PERMISSION).then(nameArg().executes(this::runDelete)))
                .then(verb("set", SET_PERMISSION).then(nameArg().executes(this::runSet)))
                .then(verb("move", MOVE_PERMISSION).then(nameArg().executes(this::runMove)))
                .then(verb("rename", RENAME_PERMISSION)
                        .then(nameArg()
                                .then(Commands.argument("newName", StringArgumentType.word())
                                        .executes(this::runRename))))
                .then(textVerb("displayname", DISPLAYNAME_PERMISSION, "text", this::runDisplayName))
                .then(textVerb("description", DESCRIPTION_PERMISSION, "text", this::runDescription))
                .then(textVerb("icon", ICON_PERMISSION, "icon", this::runIcon))
                .then(textVerb("category", CATEGORY_PERMISSION, "categoryId", this::runCategory))
                .then(accessSubtree())
                .then(passwordSubtree())
                .then(priceSubtree())
                .then(verb("rate", RATE_PERMISSION)
                        .then(nameArg()
                                .then(Commands.argument("stars", IntegerArgumentType.integer(1, 5))
                                        .executes(this::runRate))))
                .then(verb("favourite", FAVOURITE_PERMISSION)
                        .then(nameArg().executes(ctx -> dispatch(ctx, services.favouritePlayerWarp()::favourite))))
                .then(verb("unfavourite", FAVOURITE_PERMISSION)
                        .then(nameArg().executes(ctx -> dispatch(ctx, services.favouritePlayerWarp()::unfavourite))))
                .then(verb("withdraw", WITHDRAW_PERMISSION)
                        .then(nameArg().executes(ctx -> dispatch(ctx, services.withdrawEarnings()::withdraw))))
                .then(verb("info", INFO_PERMISSION)
                        .then(nameArg().executes(ctx -> dispatch(ctx, services.listPlayerWarps()::info))))
                .then(verb("transfer", TRANSFER_PERMISSION)
                        .then(nameArg()
                                .then(CommandSuggestions.playerArgument("player")
                                        .executes(this::runTransfer))))
                .then(listSubtree())
                .then(nameArg()
                        .executes(this::run)
                        .then(Commands.argument("password", StringArgumentType.greedyString())
                                .executes(this::runWithPassword)))
                .build();
    }

    @Override
    public String description() {
        return "Teleport to a player warp, edit your own warps, or manage their access and price.";
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
        Target target = target(ctx);
        if (target == null) {
            return Command.SINGLE_SUCCESS;
        }
        String name = target.name().value();
        PlayerRef owner = target.who();
        // The existence check reads the database; run it off the tick thread, then bridge the open (or the
        // not-found feedback) back to the player's region thread. Names are globally unique now, so resolve by
        // name and confirm the sender owns it before editing.
        services.scheduler().async(() -> {
            boolean exists = services.repository()
                    .findByName(target.name())
                    .filter(warp -> warp.owner().uuid().equals(owner.uuid()))
                    .isPresent();
            onPlayer(owner, () -> {
                if (!exists) {
                    feedback.send(target.sender(), PlayerwarpsMessageKey.PWARP_NOT_FOUND, Map.of("warp", name));
                    return;
                }
                if (services.editorView() != null) {
                    services.editorView().open(target.sender(), owner, name, owner);
                }
            });
        });
        return Command.SINGLE_SUCCESS;
    }

    /** {@code /pwarp <name>}: teleport with no password (an empty password on a PASSWORD warp is a wrong attempt). */
    private int run(CommandContext<CommandSourceStack> ctx) {
        Target target = target(ctx);
        if (target == null) {
            return Command.SINGLE_SUCCESS;
        }
        // The use case reads the warp then delegates the hop to the teleport context; run the read off-thread.
        services.scheduler()
                .async(() -> services.usePlayerWarp().useFor(target.who(), target.name(), Optional.empty()));
        return Command.SINGLE_SUCCESS;
    }

    /** {@code /pwarp <name> <password>}: thread the entered password to the access gate for a PASSWORD warp. */
    private int runWithPassword(CommandContext<CommandSourceStack> ctx) {
        Target target = target(ctx);
        if (target == null) {
            return Command.SINGLE_SUCCESS;
        }
        // The password is handed straight to the gate's Optional overload and never echoed, logged, or completed.
        String password = ctx.getArgument("password", String.class);
        services.scheduler()
                .async(() -> services.usePlayerWarp().useFor(target.who(), target.name(), Optional.of(password)));
        return Command.SINGLE_SUCCESS;
    }

    /** {@code /pwarp del <name>}: archive the warp by default (recoverable); the read/write runs off-thread. */
    private int runDelete(CommandContext<CommandSourceStack> ctx) {
        return dispatch(ctx, services.archivePlayerWarp()::archive);
    }

    private int runSet(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        var name = warpName(sender, ctx.getArgument("name", String.class));
        if (name == null) {
            return Command.SINGLE_SUCCESS;
        }
        // Capture the position on the entity thread before hopping off; the find + count + save touch the database.
        PlayerRef who = ref(sender);
        String ownerName = sender.getName();
        Position at = position(sender);
        services.scheduler().async(() -> services.setPlayerWarp().set(who, ownerName, name, at));
        return Command.SINGLE_SUCCESS;
    }

    private int runMove(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        var name = warpName(sender, ctx.getArgument("name", String.class));
        if (name == null) {
            return Command.SINGLE_SUCCESS;
        }
        PlayerRef who = ref(sender);
        Position at = position(sender);
        services.scheduler().async(() -> services.editPlayerWarp().moveHere(who, name, at));
        return Command.SINGLE_SUCCESS;
    }

    private int runRename(CommandContext<CommandSourceStack> ctx) {
        Target target = target(ctx);
        if (target == null) {
            return Command.SINGLE_SUCCESS;
        }
        var newName = warpName(target.sender(), ctx.getArgument("newName", String.class));
        if (newName == null) {
            return Command.SINGLE_SUCCESS;
        }
        services.scheduler().async(() -> services.editPlayerWarp().rename(target.who(), target.name(), newName));
        return Command.SINGLE_SUCCESS;
    }

    private int runDisplayName(CommandContext<CommandSourceStack> ctx) {
        return editOptional(ctx, "text", DisplayName::of, services.editPlayerWarp()::setDisplayName);
    }

    private int runDescription(CommandContext<CommandSourceStack> ctx) {
        return editOptional(ctx, "text", WarpDescription::of, services.editPlayerWarp()::setDescription);
    }

    private int runIcon(CommandContext<CommandSourceStack> ctx) {
        return editOptional(ctx, "icon", IconSpec::of, services.editPlayerWarp()::setIcon);
    }

    private int runCategory(CommandContext<CommandSourceStack> ctx) {
        Target target = target(ctx);
        if (target == null) {
            return Command.SINGLE_SUCCESS;
        }
        Optional<String> value = optionalText(argOr(ctx, "categoryId"));
        services.scheduler().async(() -> services.editPlayerWarp().setCategory(target.who(), target.name(), value));
        return Command.SINGLE_SUCCESS;
    }

    private int runAccess(CommandContext<CommandSourceStack> ctx, WarpAccess access) {
        Target target = target(ctx);
        if (target == null) {
            return Command.SINGLE_SUCCESS;
        }
        services.scheduler().async(() -> services.editPlayerWarp().setAccess(target.who(), target.name(), access));
        return Command.SINGLE_SUCCESS;
    }

    private int runSetPassword(CommandContext<CommandSourceStack> ctx) {
        Target target = target(ctx);
        if (target == null) {
            return Command.SINGLE_SUCCESS;
        }
        // The password is handed straight to the edit use case and never echoed, logged, or tab-completed.
        String password = ctx.getArgument("password", String.class);
        services.scheduler().async(() -> services.editPlayerWarp().setPassword(target.who(), target.name(), password));
        return Command.SINGLE_SUCCESS;
    }

    private int runClearPassword(CommandContext<CommandSourceStack> ctx) {
        return dispatch(ctx, services.editPlayerWarp()::clearPassword);
    }

    private int runPrice(CommandContext<CommandSourceStack> ctx, Optional<String> currency) {
        Target target = target(ctx);
        if (target == null) {
            return Command.SINGLE_SUCCESS;
        }
        // The amount arg is bounded non-negative at parse (doubleArg(0.0)); WarpCost still guards the invariant.
        BigDecimal amount = BigDecimal.valueOf(ctx.getArgument("amount", Double.class));
        WarpCost price = currency.map(id -> WarpCost.of(amount, id)).orElseGet(() -> WarpCost.of(amount));
        services.scheduler().async(() -> services.editPlayerWarp().setPrice(target.who(), target.name(), price));
        return Command.SINGLE_SUCCESS;
    }

    private int runRate(CommandContext<CommandSourceStack> ctx) {
        Target target = target(ctx);
        if (target == null) {
            return Command.SINGLE_SUCCESS;
        }
        int stars = ctx.getArgument("stars", Integer.class);
        services.scheduler().async(() -> services.ratePlayerWarp().rate(target.who(), target.name(), stars));
        return Command.SINGLE_SUCCESS;
    }

    private int runTransfer(CommandContext<CommandSourceStack> ctx) {
        Target target = target(ctx);
        if (target == null) {
            return Command.SINGLE_SUCCESS;
        }
        String targetName = ctx.getArgument("player", String.class);
        // Resolve the (possibly offline) target profile off the tick thread; an unknown target bridges the
        // not-found notice back to the actor's region thread rather than transferring to nobody.
        services.scheduler().async(() -> {
            Optional<PlayerRef> newOwner = services.players().findByName(targetName);
            if (newOwner.isEmpty()) {
                onPlayer(target.who(), () -> unknownPlayer(target.sender(), targetName));
                return;
            }
            services.transferPlayerWarp().transfer(target.who(), target.name(), newOwner.get());
        });
        return Command.SINGLE_SUCCESS;
    }

    private int runListOwn(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        PlayerRef viewer = ref(sender);
        // The owned-warp read hits the database; run it off the tick thread (the sink delivers on the region thread).
        services.scheduler().async(() -> services.listPlayerWarps().own(viewer));
        return Command.SINGLE_SUCCESS;
    }

    private int runListOther(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        PlayerRef viewer = ref(sender);
        String ownerName = ctx.getArgument("player", String.class);
        services.scheduler().async(() -> {
            Optional<PlayerRef> owner = services.players().findByName(ownerName);
            if (owner.isEmpty()) {
                onPlayer(viewer, () -> unknownPlayer(sender, ownerName));
                return;
            }
            services.listPlayerWarps().publicOf(viewer, owner.get(), owner.get().name());
        });
        return Command.SINGLE_SUCCESS;
    }

    private LiteralArgumentBuilder<CommandSourceStack> visibilitySubtree() {
        return verb("visibility", PUBLIC_PERMISSION)
                .then(Commands.literal("public").then(nameArg().executes(ctx -> dispatch(ctx, this::makePublic))))
                .then(Commands.literal("private").then(nameArg().executes(ctx -> dispatch(ctx, this::makePrivate))));
    }

    private void makePublic(PlayerRef who, PlayerWarpName name) {
        services.visibility().setPublic(who, name);
    }

    private void makePrivate(PlayerRef who, PlayerWarpName name) {
        services.visibility().setPrivate(who, name);
    }

    private LiteralArgumentBuilder<CommandSourceStack> accessSubtree() {
        RequiredArgumentBuilder<CommandSourceStack, String> name = nameArg();
        for (WarpAccess access : WarpAccess.values()) {
            name.then(Commands.literal(access.name()).executes(ctx -> runAccess(ctx, access)));
        }
        return verb("access", ACCESS_PERMISSION).then(name);
    }

    private LiteralArgumentBuilder<CommandSourceStack> passwordSubtree() {
        return verb("password", PASSWORD_PERMISSION)
                .then(nameArg()
                        .then(Commands.literal("clear").executes(this::runClearPassword))
                        .then(Commands.argument("password", StringArgumentType.greedyString())
                                .executes(this::runSetPassword)));
    }

    private LiteralArgumentBuilder<CommandSourceStack> priceSubtree() {
        return verb("price", PRICE_PERMISSION)
                .then(nameArg()
                        .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.0))
                                .executes(ctx -> runPrice(ctx, Optional.empty()))
                                .then(Commands.argument("currency", StringArgumentType.word())
                                        .executes(ctx -> runPrice(
                                                ctx, Optional.of(ctx.getArgument("currency", String.class)))))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> listSubtree() {
        return verb("list", LIST_PERMISSION)
                .executes(this::runListOwn)
                .then(CommandSuggestions.playerArgument("player").executes(this::runListOther));
    }

    /**
     * A {@code <literal> <name> [text...]} metadata subcommand: the name node clears the value (no text) and the
     * greedy text node sets it, both routed to the same handler which reads the optional text with {@link #argOr}.
     */
    private LiteralArgumentBuilder<CommandSourceStack> textVerb(
            String literal, String permission, String argName, Command<CommandSourceStack> handler) {
        return verb(literal, permission)
                .then(nameArg()
                        .executes(handler)
                        .then(Commands.argument(argName, StringArgumentType.greedyString())
                                .executes(handler)));
    }

    /**
     * Apply a metadata edit that takes an {@code Optional<T>}: an absent, blank, or {@code "-"} text clears (empty);
     * any other text is built through {@code factory} and, when it validates, dispatched. A value the value object
     * rejects sends the invalid-value notice and runs nothing, so a bad argument is never a stack trace.
     */
    private <T> int editOptional(
            CommandContext<CommandSourceStack> ctx, String argName, Function<String, T> factory, MetadataEdit<T> edit) {
        Target target = target(ctx);
        if (target == null) {
            return Command.SINGLE_SUCCESS;
        }
        Optional<String> raw = optionalText(argOr(ctx, argName));
        if (raw.isEmpty()) {
            services.scheduler().async(() -> edit.apply(target.who(), target.name(), Optional.empty()));
            return Command.SINGLE_SUCCESS;
        }
        T value;
        try {
            value = factory.apply(raw.get());
        } catch (IllegalArgumentException invalid) {
            feedback.send(target.sender(), PlayerwarpsMessageKey.PWARP_INVALID_NAME, Map.of("value", raw.get()));
            return Command.SINGLE_SUCCESS;
        }
        services.scheduler().async(() -> edit.apply(target.who(), target.name(), Optional.of(value)));
        return Command.SINGLE_SUCCESS;
    }

    /** One metadata edit verb: set (or clear) a value object on a warp. The result is delivered by the use case. */
    @FunctionalInterface
    private interface MetadataEdit<T> {
        void apply(PlayerRef who, PlayerWarpName name, Optional<T> value);
    }
}
