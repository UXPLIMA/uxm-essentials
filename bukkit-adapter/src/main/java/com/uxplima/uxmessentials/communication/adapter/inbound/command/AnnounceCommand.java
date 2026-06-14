package com.uxplima.uxmessentials.communication.adapter.inbound.command;

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
import com.uxplima.uxmessentials.communication.adapter.CommunicationSettings;
import com.uxplima.uxmessentials.communication.adapter.outbound.AnnouncerTask;
import com.uxplima.uxmessentials.communication.adapter.outbound.BukkitAnnouncerBroadcaster;
import com.uxplima.uxmessentials.communication.application.BroadcastOptOut;
import com.uxplima.uxmessentials.communication.application.CommunicationMessageKey;
import com.uxplima.uxmessentials.communication.domain.Announcement;
import com.uxplima.uxmessentials.communication.domain.AnnouncerConfig;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.display.BroadcastChannel;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /announce} ({@code uxmessentials.announce.admin}, default op): the operator surface over the rotating
 * announcer. Three admin subcommands plus a per-player toggle alias:
 *
 * <ul>
 *   <li>{@code reload} — re-read {@code announcer.conf} and swap the live config in, then re-arm the
 *       per-announcement override loops so a newly-added override fires (it is excluded from the shared rotation).
 *       The re-read is HOCON file I/O, so it runs off-tick on the {@code Scheduler} and the confirmation —
 *       the count of announcements through {@link CommunicationMessageKey#ANNOUNCER_RELOADED} — bridges back to the
 *       global region for delivery, mirroring {@code /uxmess}'s off-tick reload commands.
 *   <li>{@code list} — list the configured announcement ids and the channels each pushes to.
 *   <li>{@code preview <id>} — show that announcement to the invoking player alone, bypassing the opt-out and
 *       condition gates; an unknown id answers with {@link CommunicationMessageKey#ANNOUNCE_PREVIEW_UNKNOWN}.
 *   <li>{@code toggle} — flip the invoking player's broadcast opt-out, an alias for {@code /broadcasttoggle} so the
 *       opt-out lives under one verb too; it reuses the same {@link BroadcastOptOut} use case.
 * </ul>
 *
 * <p>The admin subcommands accept the console; {@code preview} and {@code toggle} act on the invoking player and
 * reject a console source. The announcement ids, channels, and lines are operator content; only the framing
 * (reload confirmation, list header/entry/empty, unknown-id error) is a parity-checked {@code MessageKey}.
 */
@NullMarked
public final class AnnounceCommand extends CommunicationCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.announce.admin";

    private final CommunicationSettings settings;
    private final BukkitAnnouncerBroadcaster broadcaster;
    private final BroadcastOptOut optOut;
    private final AnnouncerTask announcer;
    private final Scheduler scheduler;

    public AnnounceCommand(
            CommunicationSettings settings,
            BukkitAnnouncerBroadcaster broadcaster,
            BroadcastOptOut optOut,
            AnnouncerTask announcer,
            Scheduler scheduler,
            Messages messages) {
        super(messages);
        this.settings = Objects.requireNonNull(settings, "settings");
        this.broadcaster = Objects.requireNonNull(broadcaster, "broadcaster");
        this.optOut = Objects.requireNonNull(optOut, "optOut");
        this.announcer = Objects.requireNonNull(announcer, "announcer");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("announce")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(Commands.literal("reload").executes(this::reload))
                .then(Commands.literal("list").executes(this::list))
                .then(Commands.literal("preview")
                        .then(Commands.argument("id", StringArgumentType.word()).executes(this::preview)))
                .then(Commands.literal("toggle").executes(this::toggle))
                .build();
    }

    @Override
    public String description() {
        return "Manage the rotating server announcer.";
    }

    private int reload(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        // Re-reading the three HOCON files is blocking I/O, so it runs off the tick thread; re-arming the override
        // loops then picks up any announcement newly given an interval-seconds override (otherwise it would be
        // excluded from the rotation with no loop of its own and silently never broadcast). The confirmation hops
        // back to the global region for delivery, like /uxmess's off-tick reload commands.
        scheduler.async(() -> {
            settings.reload();
            announcer.rearmOverrides();
            int count = settings.announcerConfig().announcementCount();
            scheduler.onGlobal(() -> feedback.send(
                    sender, CommunicationMessageKey.ANNOUNCER_RELOADED, Map.of("count", Integer.toString(count))));
        });
        return Command.SINGLE_SUCCESS;
    }

    private int list(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        AnnouncerConfig config = settings.announcerConfig();
        if (!config.hasAnnouncements()) {
            feedback.send(sender, CommunicationMessageKey.ANNOUNCE_LIST_EMPTY, Map.of());
            return Command.SINGLE_SUCCESS;
        }
        feedback.send(
                sender,
                CommunicationMessageKey.ANNOUNCE_LIST_HEADER,
                Map.of("count", Integer.toString(config.announcementCount())));
        for (Announcement announcement : config.announcements()) {
            feedback.send(
                    sender,
                    CommunicationMessageKey.ANNOUNCE_LIST_ENTRY,
                    Map.of("id", announcement.id(), "channels", channels(announcement)));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int preview(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        String id = ctx.getArgument("id", String.class);
        Optional<Announcement> found = settings.announcerConfig().announcements().stream()
                .filter(announcement -> announcement.id().equalsIgnoreCase(id))
                .findFirst();
        if (found.isEmpty()) {
            feedback.send(sender, CommunicationMessageKey.ANNOUNCE_PREVIEW_UNKNOWN, Map.of("id", id));
            return Command.SINGLE_SUCCESS;
        }
        broadcaster.preview(found.get(), sender);
        return Command.SINGLE_SUCCESS;
    }

    private int toggle(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        optOut.toggle(ref(sender));
        return Command.SINGLE_SUCCESS;
    }

    private static String channels(Announcement announcement) {
        return announcement.channels().stream()
                .map(BroadcastChannel::name)
                .sorted()
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
    }
}
