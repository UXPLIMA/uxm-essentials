package com.uxplima.uxmessentials.moderation.adapter.inbound.command;

import java.util.Objects;
import java.util.Optional;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
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
 * {@code /checkban <player>}: report whether a player is currently banned, and if so the issuer, reason and
 * expiry of the active ban. Where {@code /banhistory} lists the full record, this answers the single "is this
 * player banned right now?" question staff ask before a fresh sanction. The {@code CheckBan} use case runs the
 * read against the DB-backed sanction store. It shares the {@code uxmessentials.moderation.check} node with
 * {@code /checkmute}. The lookup is hopped off the tick thread through the {@link Scheduler} port; the target
 * resolves online-first, then from the profile cache, so an offline player's ban state is still checkable.
 */
@NullMarked
public final class CheckBanCommand extends ModerationCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.moderation.check";

    private final Scheduler scheduler;

    public CheckBanCommand(ModerationServices services, Messages messages, MessageSink sink, Scheduler scheduler) {
        super(services, messages, sink);
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("checkban")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(CommandSuggestions.playerArgument("player").executes(this::run))
                .build();
    }

    @Override
    public String description() {
        return "Check whether a player is banned.";
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        PlayerRef actor = actor(ctx);
        Optional<PlayerRef> target = targetByName(ctx, ctx.getArgument("player", String.class));
        target.ifPresent(to -> scheduler.async(() -> services.checkBan().show(actor, to)));
        return Command.SINGLE_SUCCESS;
    }
}
