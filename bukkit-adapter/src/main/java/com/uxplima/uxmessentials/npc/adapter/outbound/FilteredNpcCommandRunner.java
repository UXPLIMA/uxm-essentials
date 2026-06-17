package com.uxplima.uxmessentials.npc.adapter.outbound;

import java.util.Objects;

import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.npc.adapter.inbound.listener.NpcCommandRunner;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.NullMarked;

/**
 * An {@link NpcCommandRunner} decorator that drops any command on the configured {@link BlockedCommands} list
 * before it reaches the delegate, logging the block. Both an NPC's single click command and its click-action
 * command effects (console/player/op) run through the one {@code NpcCommandRunner} the wiring shares between the
 * interaction listener and the action runner, so wrapping that single instance filters every NPC-driven dispatch
 * from one place. With an empty blocklist this is a transparent pass-through, so a default server pays nothing.
 */
@NullMarked
public final class FilteredNpcCommandRunner implements NpcCommandRunner {

    private final NpcCommandRunner delegate;
    private final BlockedCommands blocked;
    private final Logger log;

    public FilteredNpcCommandRunner(NpcCommandRunner delegate, BlockedCommands blocked, Logger log) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.blocked = Objects.requireNonNull(blocked, "blocked");
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public void runAsConsole(String command) {
        if (allowed(command)) {
            delegate.runAsConsole(command);
        }
    }

    @Override
    public void runAsPlayer(Player player, String command) {
        if (allowed(command)) {
            delegate.runAsPlayer(player, command);
        }
    }

    @Override
    public void runAsPlayerOp(Player player, String command) {
        if (allowed(command)) {
            delegate.runAsPlayerOp(player, command);
        }
    }

    /** True when {@code command} may run; logs and returns false when it is on the blocklist. */
    private boolean allowed(String command) {
        Objects.requireNonNull(command, "command");
        if (blocked.isBlocked(command)) {
            log.warn("event=npc_command_blocked command={}", command);
            return false;
        }
        return true;
    }
}
