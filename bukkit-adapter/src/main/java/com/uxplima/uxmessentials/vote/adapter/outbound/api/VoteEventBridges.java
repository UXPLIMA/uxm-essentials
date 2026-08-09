package com.uxplima.uxmessentials.vote.adapter.outbound.api;

import java.util.Objects;

import com.uxplima.uxmessentials.api.bukkit.event.vote.UxmVotePartyEvent;
import com.uxplima.uxmessentials.api.bukkit.event.vote.UxmVoteReceiveEvent;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.EventBridgeRegistry;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.Region;
import com.uxplima.uxmessentials.vote.domain.event.VotePartyTriggered;
import com.uxplima.uxmessentials.vote.domain.event.VoteReceived;
import org.jspecify.annotations.NullMarked;

/**
 * Which Bukkit event each vote fact becomes.
 *
 * <p>A vote follows the voter even when they are offline, since the entity scheduler simply no-ops then. A party is
 * the server's, so it goes global.
 */
@NullMarked
public final class VoteEventBridges {

    private VoteEventBridges() {}

    public static void register(EventBridgeRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        registry.register(
                VoteReceived.class,
                UxmVoteReceiveEvent.getHandlerList(),
                fact -> new UxmVoteReceiveEvent(
                        fact.voter().uuid(), fact.voter().name(), fact.service()),
                fact -> Region.entity(fact.voter()));
        registry.register(
                VotePartyTriggered.class,
                UxmVotePartyEvent.getHandlerList(),
                fact -> new UxmVotePartyEvent(fact.threshold()),
                fact -> Region.global());
    }
}
