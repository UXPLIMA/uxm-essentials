package com.uxplima.uxmessentials.shared.adapter.outbound.api;

import java.util.Objects;

import com.uxplima.uxmessentials.communication.adapter.outbound.api.CommunicationEventBridges;
import com.uxplima.uxmessentials.discordlink.adapter.outbound.api.DiscordLinkEventBridges;
import com.uxplima.uxmessentials.economy.adapter.outbound.api.EconomyEventBridges;
import com.uxplima.uxmessentials.holograms.adapter.outbound.api.HologramEventBridges;
import com.uxplima.uxmessentials.homes.adapter.outbound.api.HomeEventBridges;
import com.uxplima.uxmessentials.homes.adapter.outbound.api.HomeVetoBridges;
import com.uxplima.uxmessentials.invrollback.adapter.outbound.api.InvRollbackEventBridges;
import com.uxplima.uxmessentials.itemworld.adapter.outbound.api.ItemWorldEventBridges;
import com.uxplima.uxmessentials.kits.adapter.outbound.api.KitEventBridges;
import com.uxplima.uxmessentials.messaging.adapter.outbound.api.MessagingEventBridges;
import com.uxplima.uxmessentials.moderation.adapter.outbound.api.ModerationEventBridges;
import com.uxplima.uxmessentials.npc.adapter.outbound.api.NpcEventBridges;
import com.uxplima.uxmessentials.playerstate.adapter.outbound.api.PlayerStateEventBridges;
import com.uxplima.uxmessentials.playerwarps.adapter.outbound.api.PlayerWarpEventBridges;
import com.uxplima.uxmessentials.poses.adapter.outbound.api.PoseEventBridges;
import com.uxplima.uxmessentials.presence.adapter.outbound.api.PresenceEventBridges;
import com.uxplima.uxmessentials.ranks.adapter.outbound.api.RankEventBridges;
import com.uxplima.uxmessentials.scoreboard.adapter.outbound.api.ScoreboardEventBridges;
import com.uxplima.uxmessentials.security.adapter.outbound.api.SecurityEventBridges;
import com.uxplima.uxmessentials.staff.adapter.outbound.api.StaffEventBridges;
import com.uxplima.uxmessentials.teleport.adapter.outbound.api.TeleportEventBridges;
import com.uxplima.uxmessentials.trade.adapter.outbound.api.TradeEventBridges;
import com.uxplima.uxmessentials.vanish.adapter.outbound.api.VanishEventBridges;
import com.uxplima.uxmessentials.vaults.adapter.outbound.api.VaultEventBridges;
import com.uxplima.uxmessentials.vote.adapter.outbound.api.VoteEventBridges;
import com.uxplima.uxmessentials.warps.adapter.outbound.api.WarpEventBridges;
import com.uxplima.uxmessentials.worlds.adapter.outbound.api.WorldEventBridges;
import org.jspecify.annotations.NullMarked;

/**
 * The one place every context's event bridges are installed.
 *
 * <p>Each context owns its own mapping class, next to the facts it maps, but they are all installed from here rather
 * than from each module's wiring. Two reasons. The bridge is cross-cutting infrastructure, not a module feature: it
 * has no state, holds nothing, and registering it costs one map entry per event. And a disabled module publishes no
 * facts at all, since its use cases never run, so gating registration on the module would add a moving part that
 * changes nothing observable.
 */
@NullMarked
public final class EventBridges {

    private EventBridges() {}

    /** Install every context's bridges into {@code registry}, in context order. */
    public static void installAll(EventBridgeRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        HomeEventBridges.register(registry);
        WarpEventBridges.register(registry);
        PlayerWarpEventBridges.register(registry);
        TeleportEventBridges.register(registry);
        EconomyEventBridges.register(registry);
        ModerationEventBridges.register(registry);
        PlayerStateEventBridges.register(registry);
        VaultEventBridges.register(registry);
        KitEventBridges.register(registry);
        MessagingEventBridges.register(registry);
        PresenceEventBridges.register(registry);
        CommunicationEventBridges.register(registry);
        StaffEventBridges.register(registry);
        ScoreboardEventBridges.register(registry);
        PoseEventBridges.register(registry);
        VoteEventBridges.register(registry);
        ItemWorldEventBridges.register(registry);
        HologramEventBridges.register(registry);
        NpcEventBridges.register(registry);
        WorldEventBridges.register(registry);
        RankEventBridges.register(registry);
        TradeEventBridges.register(registry);
        DiscordLinkEventBridges.register(registry);
        InvRollbackEventBridges.register(registry);
        SecurityEventBridges.register(registry);
        VanishEventBridges.register(registry);
    }

    /** Install every context's veto mappings into {@code registry}, in context order. */
    public static void installAllVetoes(VetoRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        HomeVetoBridges.register(registry);
        TeleportEventBridges.registerVetoes(registry);
        WarpEventBridges.registerVetoes(registry);
        PlayerWarpEventBridges.registerVetoes(registry);
        KitEventBridges.registerVetoes(registry);
    }
}
