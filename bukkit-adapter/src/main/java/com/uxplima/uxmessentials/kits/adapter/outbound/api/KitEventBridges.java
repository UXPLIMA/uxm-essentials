package com.uxplima.uxmessentials.kits.adapter.outbound.api;

import java.util.Objects;

import com.uxplima.uxmessentials.api.bukkit.event.kit.UxmKitClaimEvent;
import com.uxplima.uxmessentials.api.bukkit.event.kit.UxmKitPreClaimEvent;
import com.uxplima.uxmessentials.kits.domain.event.KitClaimed;
import com.uxplima.uxmessentials.kits.domain.event.KitClaiming;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.EventBridgeRegistry;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.Region;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.VetoRegistry;
import org.jspecify.annotations.NullMarked;

/** Which Bukkit event the kit fact becomes. Delivered on the recipient's region, where the items landed. */
@NullMarked
public final class KitEventBridges {

    private KitEventBridges() {}

    public static void register(EventBridgeRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        registry.register(
                KitClaimed.class,
                UxmKitClaimEvent.getHandlerList(),
                fact -> new UxmKitClaimEvent(
                        fact.recipient().uuid(),
                        fact.recipient().name(),
                        fact.kit().value(),
                        fact.actor().uuid(),
                        fact.actor().name(),
                        fact.at()),
                fact -> Region.entity(fact.recipient()));
    }

    /** The one kit action worth refusing: handing the kit out. */
    public static void registerVetoes(VetoRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        registry.register(
                KitClaiming.class,
                UxmKitPreClaimEvent.getHandlerList(),
                proposal -> new UxmKitPreClaimEvent(
                        proposal.recipient().uuid(),
                        proposal.recipient().name(),
                        proposal.kit().value(),
                        proposal.actor().uuid(),
                        proposal.actor().name()));
    }
}
