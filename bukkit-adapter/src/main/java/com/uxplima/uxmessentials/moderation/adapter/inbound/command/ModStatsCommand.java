package com.uxplima.uxmessentials.moderation.adapter.inbound.command;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.moderation.adapter.ModerationServices;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /modstats [staff] [days]}: staff punishment analytics. With no target it renders the server-wide
 * most-active-staff leaderboard; with a {@code staff} name it renders that staff member's own punishment
 * breakdown. Either form accepts an optional {@code days} window (last N days), and a bare numeric first
 * argument is read as a server-wide window ({@code /modstats 7}). The {@code ReviewPunishmentStats} use case
 * scans the append-only history (bounded) and folds it through the pure {@code PunishmentStats} aggregation;
 * the whole read is hopped off the tick thread through the {@link Scheduler} port. Gated by its own
 * {@code uxmessentials.moderation.stats} node.
 *
 * <p>The numeric {@code days} branch is declared before the {@code staff} branch so Brigadier routes an integer
 * first argument to the server-wide window and any other name to the per-staff view; the window is clamped to
 * {@link #MAX_DAYS} so the bounded scan stays within budget.
 */
@NullMarked
public final class ModStatsCommand extends ModerationCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.moderation.stats";
    private static final int MAX_DAYS = 3650;

    private final Scheduler scheduler;

    public ModStatsCommand(ModerationServices services, Messages messages, MessageSink sink, Scheduler scheduler) {
        super(services, messages, sink);
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("modstats")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(ctx -> serverWide(ctx, OptionalInt.empty()))
                .then(Commands.argument("days", IntegerArgumentType.integer(1))
                        .executes(ctx -> serverWide(ctx, days(ctx))))
                .then(CommandSuggestions.playerArgument("staff")
                        .executes(ctx -> perStaff(ctx, OptionalInt.empty()))
                        .then(Commands.argument("days", IntegerArgumentType.integer(1))
                                .executes(ctx -> perStaff(ctx, days(ctx)))))
                .build();
    }

    @Override
    public String description() {
        return "Show staff punishment analytics.";
    }

    private int serverWide(CommandContext<CommandSourceStack> ctx, OptionalInt days) {
        PlayerRef viewer = actor(ctx);
        scheduler.async(() -> services.reviewPunishmentStats().showServerWide(viewer, days));
        return Command.SINGLE_SUCCESS;
    }

    private int perStaff(CommandContext<CommandSourceStack> ctx, OptionalInt days) {
        PlayerRef viewer = actor(ctx);
        Optional<PlayerRef> staff = targetByName(ctx, ctx.getArgument("staff", String.class));
        staff.ifPresent(
                member -> scheduler.async(() -> services.reviewPunishmentStats().showForStaff(viewer, member, days)));
        return Command.SINGLE_SUCCESS;
    }

    private static OptionalInt days(CommandContext<CommandSourceStack> ctx) {
        return OptionalInt.of(Math.min(IntegerArgumentType.getInteger(ctx, "days"), MAX_DAYS));
    }
}
