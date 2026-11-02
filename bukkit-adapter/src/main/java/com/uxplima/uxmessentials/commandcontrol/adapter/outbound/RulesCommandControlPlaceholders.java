package com.uxplima.uxmessentials.commandcontrol.adapter.outbound;

import java.util.Objects;

import org.bukkit.Server;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.commandcontrol.adapter.CommandControlState;
import com.uxplima.uxmessentials.commandcontrol.domain.RuleSet;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.CommandControlPlaceholders;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The {@link CommandControlPlaceholders} seam over the same {@link CommandControlState} the gate listener consults, run in
 * the gate's own order: the root as asked about first, then the bare form behind a {@code namespace:} prefix when
 * the module is set to close that bypass. An answer here therefore matches what happens when the player types the
 * command, which is the whole point of publishing it.
 *
 * <p>The resolution is a pure walk over the world's rule set plus the player's permission and group facts, so it is
 * cheap enough for a menu requirement or a scoreboard line. An offline player has no world to select a rule set
 * with, so they are reported as allowed: nothing is being blocked for somebody who is not there.
 */
@NullMarked
public final class RulesCommandControlPlaceholders implements CommandControlPlaceholders {

    private final Server server;
    private final CommandControlState state;
    private final PlayerGroupSource groups;

    public RulesCommandControlPlaceholders(Server server, CommandControlState state, PlayerGroupSource groups) {
        this.server = Objects.requireNonNull(server, "server");
        this.state = Objects.requireNonNull(state, "state");
        this.groups = Objects.requireNonNull(groups, "groups");
    }

    @Override
    public boolean allowed(PlayerRef who, String command) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(command, "command");
        Player player = server.getPlayer(who.uuid());
        if (player == null) {
            return true;
        }
        CommandControlState.Snapshot rulesInForce = state.current();
        RuleSet rules = rulesInForce.worldRules().forWorld(worldName(player));
        return rules.explain(command, new BukkitPlayerFacts(player, groups), rulesInForce.blockNamespaceBypass())
                .allowed();
    }

    private static @Nullable String worldName(Player player) {
        return player.getWorld().getName();
    }
}
