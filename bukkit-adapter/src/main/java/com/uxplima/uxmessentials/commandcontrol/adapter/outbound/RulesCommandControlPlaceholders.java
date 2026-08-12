package com.uxplima.uxmessentials.commandcontrol.adapter.outbound;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.Server;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.commandcontrol.domain.NamespaceBypassRule;
import com.uxplima.uxmessentials.commandcontrol.domain.PlayerFacts;
import com.uxplima.uxmessentials.commandcontrol.domain.RuleSet;
import com.uxplima.uxmessentials.commandcontrol.domain.RuleVerdict;
import com.uxplima.uxmessentials.commandcontrol.domain.WorldRuleSets;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.CommandControlPlaceholders;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The {@link CommandControlPlaceholders} seam over the same {@link WorldRuleSets} the gate listener consults, run in
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
    private final WorldRuleSets worldRules;
    private final boolean blockNamespaceBypass;
    private final PlayerGroupSource groups;

    public RulesCommandControlPlaceholders(
            Server server, WorldRuleSets worldRules, boolean blockNamespaceBypass, PlayerGroupSource groups) {
        this.server = Objects.requireNonNull(server, "server");
        this.worldRules = Objects.requireNonNull(worldRules, "worldRules");
        this.blockNamespaceBypass = blockNamespaceBypass;
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
        RuleSet rules = worldRules.forWorld(worldName(player));
        return decide(rules, root(command), new BukkitPlayerFacts(player, groups))
                .allowed();
    }

    private RuleVerdict decide(RuleSet rules, String root, PlayerFacts facts) {
        RuleVerdict asked = rules.explain(root, facts);
        if (asked.allowed() && blockNamespaceBypass) {
            Optional<RuleVerdict> bare = NamespaceBypassRule.bareRoot(root).map(label -> rules.explain(label, facts));
            if (bare.isPresent() && !bare.get().allowed()) {
                return bare.get();
            }
        }
        return asked;
    }

    /** The command label as the gate reads it: slash stripped, arguments dropped, lowercased. */
    private static String root(String command) {
        String body = command.startsWith("/") ? command.substring(1) : command;
        int space = body.indexOf(' ');
        String label = space < 0 ? body : body.substring(0, space);
        return label.trim().toLowerCase(Locale.ROOT);
    }

    private static @Nullable String worldName(Player player) {
        return player.getWorld().getName();
    }
}
