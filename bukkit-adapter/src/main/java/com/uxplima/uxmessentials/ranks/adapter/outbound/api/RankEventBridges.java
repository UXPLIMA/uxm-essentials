package com.uxplima.uxmessentials.ranks.adapter.outbound.api;

import java.util.Objects;

import com.uxplima.uxmessentials.api.bukkit.event.rank.UxmPrestigeEvent;
import com.uxplima.uxmessentials.api.bukkit.event.rank.UxmRankSetEvent;
import com.uxplima.uxmessentials.api.bukkit.event.rank.UxmRankUpEvent;
import com.uxplima.uxmessentials.ranks.domain.RankId;
import com.uxplima.uxmessentials.ranks.domain.event.PlayerPrestiged;
import com.uxplima.uxmessentials.ranks.domain.event.PlayerRankSet;
import com.uxplima.uxmessentials.ranks.domain.event.PlayerRankedUp;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.EventBridgeRegistry;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.Region;
import org.jspecify.annotations.NullMarked;

/**
 * Which Bukkit event each rank fact becomes.
 *
 * <p>A rankup and a prestige belong to the player who made the move, so they follow that player's thread. A
 * direct set is an administrator's write against an account that may well be offline, so it goes global: the
 * entity scheduler would have nobody to run it on.
 */
@NullMarked
public final class RankEventBridges {

    private RankEventBridges() {}

    public static void register(EventBridgeRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        registry.register(
                PlayerRankedUp.class,
                UxmRankUpEvent.getHandlerList(),
                fact -> new UxmRankUpEvent(
                        fact.who().uuid(),
                        fact.who().name(),
                        fact.from().value(),
                        fact.to().value()),
                fact -> Region.entity(fact.who()));
        registry.register(
                PlayerRankSet.class,
                UxmRankSetEvent.getHandlerList(),
                fact -> new UxmRankSetEvent(
                        fact.playerId(),
                        fact.playerId().toString(),
                        fact.previous().map(RankId::value).orElse(null),
                        fact.rank().value()),
                fact -> Region.global());
        registry.register(
                PlayerPrestiged.class,
                UxmPrestigeEvent.getHandlerList(),
                fact -> new UxmPrestigeEvent(
                        fact.who().uuid(), fact.who().name(), fact.level(), fact.rewardMultiplier()),
                fact -> Region.entity(fact.who()));
    }
}
