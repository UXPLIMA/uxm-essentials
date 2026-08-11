package com.uxplima.uxmessentials.commandcontrol.adapter.outbound.api;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.api.query.UxmCommandControlQuery;
import com.uxplima.uxmessentials.api.view.UxmCommandCheck;
import com.uxplima.uxmessentials.api.view.UxmCommandRule;
import com.uxplima.uxmessentials.commandcontrol.adapter.outbound.BukkitPlayerFacts;
import com.uxplima.uxmessentials.commandcontrol.adapter.outbound.PlayerGroupSource;
import com.uxplima.uxmessentials.commandcontrol.domain.NamespaceBypassRule;
import com.uxplima.uxmessentials.commandcontrol.domain.PlayerFacts;
import com.uxplima.uxmessentials.commandcontrol.domain.RuleMode;
import com.uxplima.uxmessentials.commandcontrol.domain.RuleSet;
import com.uxplima.uxmessentials.commandcontrol.domain.RuleVerdict;
import com.uxplima.uxmessentials.commandcontrol.domain.WorldRuleSets;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.ApiValues;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.AsyncQueries;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The published command check, run against the same rules and the same facts the gate uses.
 *
 * <p>It goes through {@link RuleSet#explain} rather than restating the resolution, and it repeats the gate's
 * namespace step in the same order: the root as asked about first, then the bare form behind a {@code namespace:}
 * prefix when the module is set to close that bypass. An answer here therefore matches what happens when the player
 * actually types the command.
 *
 * <p>The read hops to the thread that owns the player, because both halves of the input come from the live player:
 * the world they are standing in selects the rule set, and their permissions decide the bypass and the group.
 */
@NullMarked
public final class CommandControlQueries implements UxmCommandControlQuery {

    private final WorldRuleSets worldRules;
    private final boolean blockNamespaceBypass;
    private final PlayerGroupSource groups;
    private final PlayerLookup players;
    private final Scheduler scheduler;

    public CommandControlQueries(
            WorldRuleSets worldRules,
            boolean blockNamespaceBypass,
            PlayerGroupSource groups,
            PlayerLookup players,
            Scheduler scheduler) {
        this.worldRules = Objects.requireNonNull(worldRules, "worldRules");
        this.blockNamespaceBypass = blockNamespaceBypass;
        this.groups = Objects.requireNonNull(groups, "groups");
        this.players = Objects.requireNonNull(players, "players");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public CompletableFuture<Optional<UxmCommandCheck>> check(UUID playerId, String command) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(command, "command");
        PlayerRef who = ApiValues.subject(players, playerId);
        return AsyncQueries.onPlayer(scheduler, who, () -> checkNow(playerId, command), Optional.empty());
    }

    @Override
    public CompletableFuture<Boolean> isBlocked(UUID playerId, String command) {
        return check(playerId, command)
                .thenApply(answer -> answer.map(UxmCommandCheck::blocked).orElse(false));
    }

    /** The answer for a player who is here, on their own thread. */
    private Optional<UxmCommandCheck> checkNow(UUID playerId, String command) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            return Optional.empty();
        }
        String world = worldName(player);
        RuleSet rules = worldRules.forWorld(world);
        PlayerFacts facts = new BukkitPlayerFacts(player, groups);
        RuleVerdict verdict = decide(rules, root(command), facts);
        Optional<String> deciding = worldRules.hasOverrideFor(world) ? Optional.ofNullable(world) : Optional.empty();
        return Optional.of(new UxmCommandCheck(
                verdict.commandRoot(), verdict.allowed(), rule(verdict), verdict.group(), deciding));
    }

    /**
     * The gate's own order: the root as asked about, then the bare form behind a namespace prefix when that bypass
     * is closed. The bare form only replaces the answer when it is the one that blocks, which is exactly when the
     * gate would stop the command.
     */
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

    /** The published rule, which folds the mode and whether the root was listed into one answer. */
    private static UxmCommandRule rule(RuleVerdict verdict) {
        return switch (verdict.reason()) {
            case BYPASS -> UxmCommandRule.BYPASS;
            case LISTED ->
                verdict.mode() == RuleMode.WHITELIST ? UxmCommandRule.WHITELISTED : UxmCommandRule.BLACKLISTED;
            case UNLISTED ->
                verdict.mode() == RuleMode.WHITELIST ? UxmCommandRule.NOT_WHITELISTED : UxmCommandRule.NOT_BLACKLISTED;
        };
    }

    /** The command label, read exactly as the gate reads it: slash stripped, arguments dropped, lowercased. */
    private static String root(String command) {
        String body = command.startsWith("/") ? command.substring(1) : command;
        int space = body.indexOf(' ');
        String label = space < 0 ? body : body.substring(0, space);
        return label.trim().toLowerCase(Locale.ROOT);
    }

    /** The player's current world name, or {@code null} when it cannot be read, as the gate falls back too. */
    private static @Nullable String worldName(Player player) {
        World world = player.getWorld();
        return world == null ? null : world.getName();
    }
}
