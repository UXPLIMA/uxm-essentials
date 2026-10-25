package com.uxplima.uxmessentials.bossbar.adapter.inbound.command;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.bossbar.adapter.outbound.BossBarAnnouncements;
import com.uxplima.uxmessentials.bossbar.adapter.outbound.BossBarRenderer;
import com.uxplima.uxmessentials.bossbar.application.BossBarMessageKey;
import com.uxplima.uxmessentials.bossbar.application.ToggleBossBars;
import com.uxplima.uxmessentials.bossbar.domain.BossBarConfig;
import com.uxplima.uxmessentials.bossbar.domain.BossBarDefinition;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandFeedback;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.message.SharedMessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.command.Args;
import com.uxplima.uxmlib.command.Sender;
import com.uxplima.uxmlib.common.Durations;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * {@code /bossbars} ({@code uxmessentials.bossbar.use}). With no word, or {@code toggle}, {@code on} or {@code off},
 * it hides or shows the sender's own bars; with a player name after it, another player's
 * ({@code uxmessentials.bossbar.others}, the console too). {@code announce <bar> <time>} puts a bar up for everybody
 * for that long and {@code send <player> <bar> <time>} for one player ({@code uxmessentials.bossbar.announce}). A time
 * is written {@code 30s}, {@code 5m}, {@code 1h30m}.
 *
 * <p>A player is named as a word and looked up among the players online, so a name that is not there reads this
 * plugin's own line in the sender's language rather than the server's selector error.
 */
@NullMarked
public final class BossBarCommand implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.bossbar.use";
    private static final String OTHERS = "uxmessentials.bossbar.others";
    private static final String ANNOUNCE = "uxmessentials.bossbar.announce";
    private static final String PLAYER = "player";
    private static final String BAR = "bar";
    private static final String TIME = "time";

    private final ToggleBossBars toggle;
    private final BossBarRenderer renderer;
    private final BossBarAnnouncements announcements;
    private final Supplier<BossBarConfig> bars;
    private final Runnable redrawEverybody;
    private final BiFunction<UUID, Duration, String> remaining;
    private final Supplier<Instant> clock;
    private final Scheduler scheduler;
    private final CommandFeedback feedback;

    /**
     * @param redrawEverybody draws every player's bars now, after an announcement
     * @param remaining a length in one reader's words
     */
    public BossBarCommand(
            ToggleBossBars toggle,
            BossBarRenderer renderer,
            BossBarAnnouncements announcements,
            Supplier<BossBarConfig> bars,
            Runnable redrawEverybody,
            BiFunction<UUID, Duration, String> remaining,
            Supplier<Instant> clock,
            Scheduler scheduler,
            Messages messages) {
        this.toggle = Objects.requireNonNull(toggle, "toggle");
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        this.announcements = Objects.requireNonNull(announcements, "announcements");
        this.bars = Objects.requireNonNull(bars, "bars");
        this.redrawEverybody = Objects.requireNonNull(redrawEverybody, "redrawEverybody");
        this.remaining = Objects.requireNonNull(remaining, "remaining");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.feedback = new CommandFeedback(Objects.requireNonNull(messages, "messages"));
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("bossbars")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(ctx -> self(ctx, Choice.TOGGLE))
                .then(choice("toggle", Choice.TOGGLE))
                .then(choice("on", Choice.SHOW))
                .then(choice("off", Choice.HIDE))
                .then(Commands.literal("announce")
                        .requires(src -> src.getSender().hasPermission(ANNOUNCE))
                        .then(barArgument().then(timeArgument().executes(this::announce))))
                .then(Commands.literal("send")
                        .requires(src -> src.getSender().hasPermission(ANNOUNCE))
                        .then(playerArgument()
                                .then(barArgument().then(timeArgument().executes(this::send)))))
                .build();
    }

    @Override
    public String description() {
        return "Hide or show your boss bars, or announce one.";
    }

    private com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> choice(
            String literal, Choice choice) {
        return Commands.literal(literal)
                .executes(ctx -> self(ctx, choice))
                .then(playerArgument()
                        .requires(src -> src.getSender().hasPermission(OTHERS))
                        .executes(ctx -> other(ctx, choice)));
    }

    private int self(CommandContext<CommandSourceStack> ctx, Choice choice) {
        CommandSender sender = Sender.audience(ctx.getSource());
        if (!(sender instanceof Player player)) {
            feedback.send(sender, SharedMessageKey.COMMAND_PLAYERS_ONLY);
            return 0;
        }
        apply(player, choice);
        return Command.SINGLE_SUCCESS;
    }

    private int other(CommandContext<CommandSourceStack> ctx, Choice choice) {
        CommandSender sender = Sender.audience(ctx.getSource());
        @Nullable Player target = online(sender, StringArgumentType.getString(ctx, PLAYER));
        if (target == null) {
            return 0;
        }
        boolean hidden = apply(target, choice);
        if (!target.equals(sender)) {
            feedback.send(
                    sender,
                    hidden ? BossBarMessageKey.BOSSBAR_HIDDEN_OTHER : BossBarMessageKey.BOSSBAR_SHOWN_OTHER,
                    Map.of(PLAYER, target.getName()));
        }
        return Command.SINGLE_SUCCESS;
    }

    private boolean apply(Player player, Choice choice) {
        PlayerRef who = BukkitRefs.toRef(player);
        boolean hidden =
                switch (choice) {
                    case TOGGLE -> toggle.toggle(who);
                    case SHOW -> toggle.set(who, false);
                    case HIDE -> toggle.set(who, true);
                };
        scheduler.onEntity(who, () -> renderer.renderFor(player));
        return hidden;
    }

    private int announce(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = Sender.audience(ctx.getSource());
        Optional<BossBarDefinition> bar = bar(sender, StringArgumentType.getString(ctx, BAR));
        Optional<Duration> length = length(sender, StringArgumentType.getString(ctx, TIME));
        if (bar.isEmpty() || length.isEmpty()) {
            return 0;
        }
        announcements.announce(bar.get().id(), length.get(), clock.get());
        redrawEverybody.run();
        feedback.send(
                sender,
                BossBarMessageKey.BOSSBAR_ANNOUNCED,
                Map.of(BAR, bar.get().id(), TIME, words(sender, length.get())));
        return Command.SINGLE_SUCCESS;
    }

    private int send(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = Sender.audience(ctx.getSource());
        @Nullable Player target = online(sender, StringArgumentType.getString(ctx, PLAYER));
        if (target == null) {
            return 0;
        }
        Optional<BossBarDefinition> bar = bar(sender, StringArgumentType.getString(ctx, BAR));
        Optional<Duration> length = length(sender, StringArgumentType.getString(ctx, TIME));
        if (bar.isEmpty() || length.isEmpty()) {
            return 0;
        }
        announcements.send(target.getUniqueId(), bar.get().id(), length.get(), clock.get());
        scheduler.onEntity(BukkitRefs.toRef(target), () -> renderer.renderFor(target));
        feedback.send(
                sender,
                BossBarMessageKey.BOSSBAR_SENT,
                Map.of(BAR, bar.get().id(), PLAYER, target.getName(), TIME, words(sender, length.get())));
        return Command.SINGLE_SUCCESS;
    }

    private @Nullable Player online(CommandSender sender, String name) {
        @Nullable Player player = Bukkit.getPlayerExact(name);
        if (player == null) {
            feedback.send(sender, SharedMessageKey.COMMAND_UNKNOWN_PLAYER, Map.of(PLAYER, name));
        }
        return player;
    }

    private Optional<BossBarDefinition> bar(CommandSender sender, String id) {
        Optional<BossBarDefinition> bar = bars.get().byId(id);
        if (bar.isEmpty()) {
            feedback.send(sender, BossBarMessageKey.BOSSBAR_UNKNOWN_BAR, Map.of(BAR, id));
        }
        return bar;
    }

    private Optional<Duration> length(CommandSender sender, String written) {
        Optional<Duration> length = Durations.tryParse(written).filter(read -> !read.isZero() && !read.isNegative());
        if (length.isEmpty()) {
            feedback.send(sender, BossBarMessageKey.BOSSBAR_BAD_DURATION, Map.of("input", written));
        }
        return length;
    }

    private String words(CommandSender sender, Duration length) {
        return remaining.apply(CommandFeedback.refOf(sender).uuid(), length);
    }

    private RequiredArgumentBuilder<CommandSourceStack, String> playerArgument() {
        return Commands.argument(PLAYER, Args.token()).suggests(CommandSuggestions.onlinePlayers());
    }

    private RequiredArgumentBuilder<CommandSourceStack, String> barArgument() {
        return Commands.argument(BAR, Args.token()).suggests(barIds());
    }

    private RequiredArgumentBuilder<CommandSourceStack, String> timeArgument() {
        return Commands.argument(TIME, Args.token());
    }

    private SuggestionProvider<CommandSourceStack> barIds() {
        return (ctx, builder) -> {
            String typed = builder.getRemainingLowerCase();
            for (BossBarDefinition bar : bars.get().bars()) {
                if (bar.id().toLowerCase(Locale.ROOT).startsWith(typed)) {
                    builder.suggest(bar.id());
                }
            }
            return builder.buildFuture();
        };
    }

    private enum Choice {
        TOGGLE,
        SHOW,
        HIDE
    }
}
