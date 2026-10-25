package com.uxplima.uxmessentials.vote.adapter.outbound;

import java.util.List;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.adapter.outbound.action.ClickCommandRunner;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.vote.application.port.RewardDispatcher;
import com.uxplima.uxmlib.condition.action.NameFill;
import org.jspecify.annotations.NullMarked;

/**
 * The Bukkit {@link RewardDispatcher}: runs each reward command from the console sender, with the rewarded player's
 * name filled into {@code {player}}, {@code %player%} or {@code <player>}. A vote names its voter in whatever the
 * vote site sent, so a name an account could not have is never filled in and its command is skipped. Console-command execution touches global
 * game state and the command map, so the whole batch is dispatched on the global region thread through the
 * injected {@link Scheduler} port, never the legacy {@code BukkitScheduler}. An empty batch hops nowhere.
 *
 * <p>The reward lines are operator-authored config content dispatched as written, not player-facing
 * messages, so they do not flow through the message catalog; the only transform is the name.
 */
@NullMarked
public final class BukkitRewardDispatcher implements RewardDispatcher {

    private final Scheduler scheduler;
    private final ClickCommandRunner commands;
    private final Logger log;

    public BukkitRewardDispatcher(Scheduler scheduler, ClickCommandRunner commands, Logger log) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.commands = Objects.requireNonNull(commands, "commands");
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public void dispatch(List<String> commands, String playerName) {
        Objects.requireNonNull(commands, "commands");
        Objects.requireNonNull(playerName, "playerName");
        if (commands.isEmpty()) {
            return;
        }
        scheduler.onGlobal(() -> runAll(commands, playerName));
    }

    private void runAll(List<String> lines, String playerName) {
        for (String line : lines) {
            NameFill.into(line, playerName)
                    .ifPresentOrElse(
                            commands::runAsConsole,
                            () -> log.warn(
                                    "event=vote_reward_command_skipped player={} reason=not_an_account_name",
                                    playerName));
        }
    }
}
