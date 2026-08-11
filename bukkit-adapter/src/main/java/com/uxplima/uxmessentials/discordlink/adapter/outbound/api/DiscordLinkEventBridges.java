package com.uxplima.uxmessentials.discordlink.adapter.outbound.api;

import java.util.Objects;

import com.uxplima.uxmessentials.api.bukkit.event.discordlink.UxmAccountLinkEvent;
import com.uxplima.uxmessentials.api.bukkit.event.discordlink.UxmAccountUnlinkEvent;
import com.uxplima.uxmessentials.discordlink.domain.event.AccountLinked;
import com.uxplima.uxmessentials.discordlink.domain.event.AccountUnlinked;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.EventBridgeRegistry;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.Region;
import org.jspecify.annotations.NullMarked;

/**
 * Which Bukkit event each binding fact becomes.
 *
 * <p>Both go global. A code is redeemed on Discord and an unlink can be issued by a plugin, so in neither case is
 * the player reliably on this server, and an entity hop would have nobody to run on.
 */
@NullMarked
public final class DiscordLinkEventBridges {

    private DiscordLinkEventBridges() {}

    public static void register(EventBridgeRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        registry.register(
                AccountLinked.class,
                UxmAccountLinkEvent.getHandlerList(),
                fact -> new UxmAccountLinkEvent(
                        fact.player().uuid(),
                        fact.player().name(),
                        fact.discordId().value(),
                        fact.linkedAt()),
                fact -> Region.global());
        registry.register(
                AccountUnlinked.class,
                UxmAccountUnlinkEvent.getHandlerList(),
                fact -> new UxmAccountUnlinkEvent(
                        fact.player().uuid(),
                        fact.player().name(),
                        fact.discordId().value()),
                fact -> Region.global());
    }
}
