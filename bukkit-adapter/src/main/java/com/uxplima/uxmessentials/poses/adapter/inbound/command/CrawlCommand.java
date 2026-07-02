package com.uxplima.uxmessentials.poses.adapter.inbound.command;

import java.util.Objects;
import java.util.Optional;

import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.poses.application.PoseSessions;
import com.uxplima.uxmessentials.poses.application.PosesMessageKey;
import com.uxplima.uxmessentials.poses.application.StartCrawl;
import com.uxplima.uxmessentials.poses.application.StartCrawl.CrawlOutcome;
import com.uxplima.uxmessentials.poses.application.StopPose;
import com.uxplima.uxmessentials.poses.domain.PoseSession;
import com.uxplima.uxmessentials.poses.domain.PoseType;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandFeedback;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.SharedMessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /crawl} ({@code uxmessentials.crawl.use}): a toggle for the prone crawl. When the player is already
 * crawling a second {@code /crawl} ends it through {@link StopPose} (which restores the client-only fake block so no
 * phantom is left); otherwise it starts a crawl through {@link StartCrawl}, which gates the feature, the region, and
 * the one-session invariant. A player holding a different pose (a sit or a lie-down) is not crawling, so
 * {@code /crawl} routes to the start path and {@link StartCrawl} turns it away as already-posing rather than the
 * toggle silently ending the unrelated pose.
 */
@NullMarked
public final class CrawlCommand implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.crawl.use";

    private final StartCrawl startCrawl;
    private final StopPose stopPose;
    private final PoseSessions sessions;
    private final CommandFeedback feedback;

    public CrawlCommand(StartCrawl startCrawl, StopPose stopPose, PoseSessions sessions, Messages messages) {
        this.startCrawl = Objects.requireNonNull(startCrawl, "startCrawl");
        this.stopPose = Objects.requireNonNull(stopPose, "stopPose");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.feedback = new CommandFeedback(Objects.requireNonNull(messages, "messages"));
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("crawl")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::run)
                .build();
    }

    @Override
    public String description() {
        return "Crawl through a one-block gap; run again to stand up.";
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            feedback.send(sender, SharedMessageKey.COMMAND_PLAYERS_ONLY);
            return 0;
        }
        PlayerRef who = BukkitRefs.toRef(player);
        if (isCrawling(who)) {
            // Toggle off: end the crawl (StopPose restores the fake block), then confirm they stood up.
            stopPose.stop(who);
            feedback.send(player, PosesMessageKey.POSES_CRAWL_STOPPED);
            return Command.SINGLE_SUCCESS;
        }
        Location location = Objects.requireNonNull(player.getLocation(), "player location");
        Position feet = BukkitRefs.toPosition(location);
        CrawlOutcome outcome = startCrawl.start(who, feet);
        feedback.send(player, feedbackFor(outcome));
        return Command.SINGLE_SUCCESS;
    }

    private boolean isCrawling(PlayerRef who) {
        Optional<PoseSession> session = sessions.current(who);
        return session.isPresent() && session.get().type() == PoseType.CRAWL;
    }

    private static MessageKey feedbackFor(CrawlOutcome outcome) {
        return switch (outcome) {
            case STARTED -> PosesMessageKey.POSES_NOW_CRAWLING;
            case DISABLED -> PosesMessageKey.POSES_CRAWL_DISABLED;
            case DENIED_REGION -> PosesMessageKey.POSES_CANNOT_HERE;
            case ALREADY_POSING -> PosesMessageKey.POSES_ALREADY_POSING;
        };
    }
}
