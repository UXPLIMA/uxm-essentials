package com.uxplima.uxmessentials.skin.adapter.inbound.command;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandFeedback;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.message.SharedMessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.skin.adapter.outbound.api.SkinSources;
import com.uxplima.uxmessentials.skin.application.ClearSkin;
import com.uxplima.uxmessentials.skin.application.DescribeSkin;
import com.uxplima.uxmessentials.skin.application.DropSkin;
import com.uxplima.uxmessentials.skin.application.PurgeSkinCache;
import com.uxplima.uxmessentials.skin.application.SetSkin;
import com.uxplima.uxmessentials.skin.application.SkinMessageKey;
import com.uxplima.uxmessentials.skin.application.UpdateSkin;
import com.uxplima.uxmessentials.skin.domain.SkinModel;
import com.uxplima.uxmessentials.skin.domain.SkinSource;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * {@code /skin}: everything a player or a staff member does to the face somebody is wearing.
 *
 * <p>A player takes a skin by name ({@code /skin Notch} or {@code /skin set Notch}), from an image on the web
 * ({@code /skin url <link> [slim]}), or from one the operator dropped on the server ({@code /skin file <name>
 * [slim]}); they drop their own choice with {@code /skin clear} and re-pull it with {@code /skin update}. Staff
 * dress and undress others ({@code /skin set <name> <player>}, {@code /skin clear <player>}), delete a stored
 * skin outright ({@code /skin drop <player>}), read one back ({@code /skin info <player>}) and forget a cached
 * texture ({@code /skin purge <name>}).
 *
 * <p>Every branch that touches the network answers immediately with a "working on it" line and does the lookup on
 * the async pool through the {@link Scheduler} port, so no skin lookup ever runs on a tick thread.
 */
@NullMarked
public final class SkinCommand implements CommandRegistration {

    private static final String USE = "uxmessentials.skin.use";
    private static final String URL = "uxmessentials.skin.url";
    private static final String FILE = "uxmessentials.skin.file";
    private static final String UPDATE = "uxmessentials.skin.update";
    private static final String OTHER = "uxmessentials.skin.other";
    private static final String DROP = "uxmessentials.skin.drop";
    private static final String INFO = "uxmessentials.skin.info";
    private static final String PURGE = "uxmessentials.skin.purge";

    /** The word a player appends to say the image was drawn for the three-pixel arm. */
    private static final String SLIM = "slim";

    private static final DateTimeFormatter APPLIED_AT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final SetSkin setSkin;
    private final ClearSkin clearSkin;
    private final UpdateSkin updateSkin;
    private final DropSkin dropSkin;
    private final DescribeSkin describeSkin;
    private final PurgeSkinCache purgeCache;
    private final PlayerLookup names;
    private final Scheduler scheduler;
    private final CommandFeedback feedback;

    public SkinCommand(
            SetSkin setSkin,
            ClearSkin clearSkin,
            UpdateSkin updateSkin,
            DropSkin dropSkin,
            DescribeSkin describeSkin,
            PurgeSkinCache purgeCache,
            PlayerLookup names,
            Scheduler scheduler,
            Messages messages) {
        this.setSkin = Objects.requireNonNull(setSkin, "setSkin");
        this.clearSkin = Objects.requireNonNull(clearSkin, "clearSkin");
        this.updateSkin = Objects.requireNonNull(updateSkin, "updateSkin");
        this.dropSkin = Objects.requireNonNull(dropSkin, "dropSkin");
        this.describeSkin = Objects.requireNonNull(describeSkin, "describeSkin");
        this.purgeCache = Objects.requireNonNull(purgeCache, "purgeCache");
        this.names = Objects.requireNonNull(names, "names");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.feedback = new CommandFeedback(Objects.requireNonNull(messages, "messages"));
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("skin")
                .requires(src -> src.getSender().hasPermission(USE)
                        || src.getSender().hasPermission(OTHER)
                        || src.getSender().hasPermission(INFO))
                .then(Commands.literal("set")
                        .requires(src -> src.getSender().hasPermission(USE))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(ctx -> byName(ctx, null))
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .requires(src -> src.getSender().hasPermission(OTHER))
                                        .executes(ctx -> byName(ctx, StringArgumentType.getString(ctx, "player"))))))
                .then(Commands.literal("url")
                        .requires(src -> src.getSender().hasPermission(URL))
                        .then(Commands.argument("link", StringArgumentType.string())
                                .executes(ctx -> fromUpload(ctx, true, false))
                                .then(Commands.literal(SLIM).executes(ctx -> fromUpload(ctx, true, true)))))
                .then(Commands.literal("file")
                        .requires(src -> src.getSender().hasPermission(FILE))
                        .then(Commands.argument("file", StringArgumentType.word())
                                .executes(ctx -> fromUpload(ctx, false, false))
                                .then(Commands.literal(SLIM).executes(ctx -> fromUpload(ctx, false, true)))))
                .then(Commands.literal("clear")
                        .requires(src -> src.getSender().hasPermission(USE))
                        .executes(ctx -> clear(ctx, null))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .requires(src -> src.getSender().hasPermission(OTHER))
                                .executes(ctx -> clear(ctx, StringArgumentType.getString(ctx, "player")))))
                .then(Commands.literal("update")
                        .requires(src -> src.getSender().hasPermission(UPDATE))
                        .executes(this::update))
                .then(Commands.literal("drop")
                        .requires(src -> src.getSender().hasPermission(DROP))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .executes(this::drop)))
                .then(Commands.literal("info")
                        .requires(src -> src.getSender().hasPermission(INFO))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .executes(this::info)))
                .then(Commands.literal("purge")
                        .requires(src -> src.getSender().hasPermission(PURGE))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(this::purge)))
                .then(Commands.argument("name", StringArgumentType.word())
                        .requires(src -> src.getSender().hasPermission(USE))
                        .executes(ctx -> byName(ctx, null)))
                .executes(this::usage)
                .build();
    }

    @Override
    public String description() {
        return "Wear the skin of another account, an image on the web, or one of the server's own.";
    }

    @Override
    public List<String> aliases() {
        return List.of("skins");
    }

    /** {@code /skin <name>}, {@code /skin set <name>} and the staff {@code /skin set <name> <player>}. */
    private int byName(CommandContext<CommandSourceStack> ctx, @Nullable String targetName) {
        CommandSender sender = ctx.getSource().getSender();
        PlayerRef actor = actorOf(sender);
        if (actor == null) {
            return 0;
        }
        PlayerRef target = targetName == null ? actor : named(sender, targetName);
        if (target == null) {
            return 0;
        }
        String name = StringArgumentType.getString(ctx, "name");
        run(
                sender,
                () -> report(
                        sender,
                        actor,
                        target,
                        setSkin.set(actor, target, new SkinSource.ByName(name), SkinModel.CLASSIC)));
        return Command.SINGLE_SUCCESS;
    }

    /** {@code /skin url <link> [slim]} and {@code /skin file <name> [slim]}, which share everything but the source. */
    private int fromUpload(CommandContext<CommandSourceStack> ctx, boolean fromWeb, boolean slim) {
        CommandSender sender = ctx.getSource().getSender();
        PlayerRef actor = actorOf(sender);
        if (actor == null) {
            return 0;
        }
        SkinSource source = fromWeb
                ? new SkinSource.ByUrl(StringArgumentType.getString(ctx, "link"))
                : new SkinSource.ByFile(StringArgumentType.getString(ctx, "file"));
        SkinModel model = SkinModel.of(slim);
        run(sender, () -> report(sender, actor, actor, setSkin.set(actor, actor, source, model)));
        return Command.SINGLE_SUCCESS;
    }

    /** {@code /skin clear} and the staff {@code /skin clear <player>}. */
    private int clear(CommandContext<CommandSourceStack> ctx, @Nullable String targetName) {
        CommandSender sender = ctx.getSource().getSender();
        PlayerRef actor = actorOf(sender);
        if (actor == null) {
            return 0;
        }
        PlayerRef target = targetName == null ? actor : named(sender, targetName);
        if (target == null) {
            return 0;
        }
        run(sender, () -> {
            ClearSkin.Outcome outcome = clearSkin.clear(target);
            if (outcome == ClearSkin.Outcome.NOTHING_TO_CLEAR) {
                feedback.send(sender, SkinMessageKey.SKIN_NOTHING_TO_CLEAR);
            } else if (actor.equals(target)) {
                feedback.send(sender, SkinMessageKey.SKIN_CLEARED);
            } else {
                feedback.send(sender, SkinMessageKey.SKIN_CLEARED_FOR_OTHER, Map.of("player", target.name()));
            }
        });
        return Command.SINGLE_SUCCESS;
    }

    /** {@code /skin update}: re-pull the skin the player already chose. */
    private int update(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        PlayerRef actor = actorOf(sender);
        if (actor == null) {
            return 0;
        }
        run(
                sender,
                () -> feedback.send(
                        sender,
                        switch (updateSkin.update(actor)) {
                            case UPDATED -> SkinMessageKey.SKIN_UPDATED;
                            case NOTHING_STORED -> SkinMessageKey.SKIN_NOTHING_STORED;
                            case LOOKUP_FAILED -> SkinMessageKey.SKIN_LOOKUP_FAILED;
                        }));
        return Command.SINGLE_SUCCESS;
    }

    /** {@code /skin drop <player>}: delete a stored skin, whether or not its owner is online. */
    private int drop(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        PlayerRef target = named(sender, StringArgumentType.getString(ctx, "player"));
        if (target == null) {
            return 0;
        }
        run(
                sender,
                () -> feedback.send(
                        sender,
                        dropSkin.drop(target.uuid()) == DropSkin.Outcome.DROPPED
                                ? SkinMessageKey.SKIN_DROPPED
                                : SkinMessageKey.SKIN_NOTHING_STORED,
                        Map.of("player", target.name())));
        return Command.SINGLE_SUCCESS;
    }

    /** {@code /skin info <player>}: which skin, from where, on which model, and when. */
    private int info(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        PlayerRef target = named(sender, StringArgumentType.getString(ctx, "player"));
        if (target == null) {
            return 0;
        }
        run(sender, () -> {
            Optional<DescribeSkin.Description> described = describeSkin.describe(target.uuid());
            if (described.isEmpty()) {
                feedback.send(sender, SkinMessageKey.SKIN_INFO_NONE, Map.of("player", target.name()));
                return;
            }
            DescribeSkin.Description description = described.get();
            feedback.send(sender, SkinMessageKey.SKIN_INFO_HEADER, Map.of("player", target.name()));
            feedback.send(
                    sender,
                    SkinMessageKey.SKIN_INFO_SOURCE,
                    Map.of(
                            "source", SkinSources.typeOf(description.source()),
                            "value", description.source().value()));
            feedback.send(
                    sender,
                    SkinMessageKey.SKIN_INFO_MODEL,
                    Map.of("model", description.model().name()));
            feedback.send(sender, SkinMessageKey.SKIN_INFO_APPLIED, Map.of("when", when(description.appliedAt())));
        });
        return Command.SINGLE_SUCCESS;
    }

    /** {@code /skin purge <name>}: forget a cached texture so the next lookup is fresh. */
    private int purge(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        String name = StringArgumentType.getString(ctx, "name");
        run(sender, () -> {
            purgeCache.purge(name);
            feedback.send(sender, SkinMessageKey.SKIN_PURGED, Map.of("name", name));
        });
        return Command.SINGLE_SUCCESS;
    }

    private int usage(CommandContext<CommandSourceStack> ctx) {
        feedback.send(ctx.getSource().getSender(), SkinMessageKey.SKIN_USAGE);
        return Command.SINGLE_SUCCESS;
    }

    /** Tell the sender the work has started, then do it off the tick thread. */
    private void run(CommandSender sender, Runnable work) {
        feedback.send(sender, SkinMessageKey.SKIN_WORKING);
        scheduler.async(work);
    }

    /** Say how a set ended, in the wording that fits who it was for. */
    private void report(CommandSender sender, PlayerRef actor, PlayerRef target, SetSkin.Outcome outcome) {
        if (outcome != SetSkin.Outcome.APPLIED) {
            feedback.send(
                    sender,
                    switch (outcome) {
                        case DISABLED_SOURCE -> SkinMessageKey.SKIN_SOURCE_DISABLED;
                        case BLOCKED -> SkinMessageKey.SKIN_BLOCKED;
                        case URL_NOT_ALLOWED -> SkinMessageKey.SKIN_URL_NOT_ALLOWED;
                        case NO_PERMISSION -> SkinMessageKey.SKIN_NO_PERMISSION;
                        case ON_COOLDOWN -> SkinMessageKey.SKIN_ON_COOLDOWN;
                        case NOT_FOUND -> SkinMessageKey.SKIN_NOT_FOUND;
                        default -> SkinMessageKey.SKIN_LOOKUP_FAILED;
                    });
            return;
        }
        if (actor.equals(target)) {
            feedback.send(sender, SkinMessageKey.SKIN_APPLIED);
        } else {
            feedback.send(sender, SkinMessageKey.SKIN_SET_FOR_OTHER, Map.of("player", target.name()));
        }
    }

    /** The sender as a player ref, or null after telling a non-player sender this is not for them. */
    private @Nullable PlayerRef actorOf(CommandSender sender) {
        if (sender instanceof Player player) {
            return BukkitRefs.toRef(player);
        }
        feedback.send(sender, SharedMessageKey.COMMAND_PLAYERS_ONLY);
        return null;
    }

    /** The account known by {@code name}, or null after saying nobody here goes by it. */
    private @Nullable PlayerRef named(CommandSender sender, String name) {
        Optional<PlayerRef> found = names.findByName(name);
        if (found.isEmpty()) {
            feedback.send(sender, SkinMessageKey.SKIN_UNKNOWN_PLAYER, Map.of("player", name));
            return null;
        }
        return found.get();
    }

    private static String when(Instant applied) {
        return APPLIED_AT.format(applied);
    }
}
