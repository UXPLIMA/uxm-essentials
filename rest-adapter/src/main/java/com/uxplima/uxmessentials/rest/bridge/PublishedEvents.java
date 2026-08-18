package com.uxplima.uxmessentials.rest.bridge;

import java.util.List;

import com.uxplima.uxmessentials.api.bukkit.event.UxmEvent;
import com.uxplima.uxmessentials.api.bukkit.event.communication.UxmAnnouncerReloadEvent;
import com.uxplima.uxmessentials.api.bukkit.event.communication.UxmBroadcastOptOutEvent;
import com.uxplima.uxmessentials.api.bukkit.event.discordlink.UxmAccountLinkEvent;
import com.uxplima.uxmessentials.api.bukkit.event.discordlink.UxmAccountUnlinkEvent;
import com.uxplima.uxmessentials.api.bukkit.event.economy.UxmBankDepositEvent;
import com.uxplima.uxmessentials.api.bukkit.event.economy.UxmBankWithdrawEvent;
import com.uxplima.uxmessentials.api.bukkit.event.economy.UxmLoanDisburseEvent;
import com.uxplima.uxmessentials.api.bukkit.event.economy.UxmLoanRepayEvent;
import com.uxplima.uxmessentials.api.bukkit.event.economy.UxmWalletCreditEvent;
import com.uxplima.uxmessentials.api.bukkit.event.economy.UxmWalletDebitEvent;
import com.uxplima.uxmessentials.api.bukkit.event.economy.UxmWalletRejectEvent;
import com.uxplima.uxmessentials.api.bukkit.event.hologram.UxmHologramCreateEvent;
import com.uxplima.uxmessentials.api.bukkit.event.hologram.UxmHologramDeleteEvent;
import com.uxplima.uxmessentials.api.bukkit.event.home.UxmHomeCreateEvent;
import com.uxplima.uxmessentials.api.bukkit.event.home.UxmHomeDeleteEvent;
import com.uxplima.uxmessentials.api.bukkit.event.home.UxmHomeIconChangeEvent;
import com.uxplima.uxmessentials.api.bukkit.event.home.UxmHomeLimitReachedEvent;
import com.uxplima.uxmessentials.api.bukkit.event.home.UxmHomeRelocateEvent;
import com.uxplima.uxmessentials.api.bukkit.event.home.UxmHomeRenameEvent;
import com.uxplima.uxmessentials.api.bukkit.event.home.UxmHomeVisibilityChangeEvent;
import com.uxplima.uxmessentials.api.bukkit.event.invrollback.UxmInventoryRestoreEvent;
import com.uxplima.uxmessentials.api.bukkit.event.itemworld.UxmEntityPurgeEvent;
import com.uxplima.uxmessentials.api.bukkit.event.itemworld.UxmMobSpawnEvent;
import com.uxplima.uxmessentials.api.bukkit.event.kit.UxmKitClaimEvent;
import com.uxplima.uxmessentials.api.bukkit.event.messaging.UxmHelpOpEvent;
import com.uxplima.uxmessentials.api.bukkit.event.messaging.UxmMailDeliverEvent;
import com.uxplima.uxmessentials.api.bukkit.event.messaging.UxmPrivateMessageEvent;
import com.uxplima.uxmessentials.api.bukkit.event.moderation.UxmAltDetectedEvent;
import com.uxplima.uxmessentials.api.bukkit.event.moderation.UxmIpBanEvent;
import com.uxplima.uxmessentials.api.bukkit.event.moderation.UxmJailLocationDefineEvent;
import com.uxplima.uxmessentials.api.bukkit.event.moderation.UxmJailLocationRemoveEvent;
import com.uxplima.uxmessentials.api.bukkit.event.moderation.UxmPlayerJailEvent;
import com.uxplima.uxmessentials.api.bukkit.event.moderation.UxmPlayerMuteEvent;
import com.uxplima.uxmessentials.api.bukkit.event.moderation.UxmPlayerTempbanEvent;
import com.uxplima.uxmessentials.api.bukkit.event.moderation.UxmPlayerUnjailEvent;
import com.uxplima.uxmessentials.api.bukkit.event.moderation.UxmPlayerUnmuteEvent;
import com.uxplima.uxmessentials.api.bukkit.event.moderation.UxmPlayerWarnEvent;
import com.uxplima.uxmessentials.api.bukkit.event.npc.UxmNpcCreateEvent;
import com.uxplima.uxmessentials.api.bukkit.event.npc.UxmNpcDeleteEvent;
import com.uxplima.uxmessentials.api.bukkit.event.npc.UxmNpcMoveEvent;
import com.uxplima.uxmessentials.api.bukkit.event.playerstate.UxmPlayerFeedEvent;
import com.uxplima.uxmessentials.api.bukkit.event.playerstate.UxmPlayerFlyToggleEvent;
import com.uxplima.uxmessentials.api.bukkit.event.playerstate.UxmPlayerGameModeChangeEvent;
import com.uxplima.uxmessentials.api.bukkit.event.playerstate.UxmPlayerGodToggleEvent;
import com.uxplima.uxmessentials.api.bukkit.event.playerstate.UxmPlayerHealEvent;
import com.uxplima.uxmessentials.api.bukkit.event.playerstate.UxmPlayerSpeedChangeEvent;
import com.uxplima.uxmessentials.api.bukkit.event.playerwarp.UxmPlayerWarpCreateEvent;
import com.uxplima.uxmessentials.api.bukkit.event.playerwarp.UxmPlayerWarpDeleteEvent;
import com.uxplima.uxmessentials.api.bukkit.event.pose.UxmPoseEvent;
import com.uxplima.uxmessentials.api.bukkit.event.presence.UxmAfkEvent;
import com.uxplima.uxmessentials.api.bukkit.event.rank.UxmPrestigeEvent;
import com.uxplima.uxmessentials.api.bukkit.event.rank.UxmRankSetEvent;
import com.uxplima.uxmessentials.api.bukkit.event.rank.UxmRankUpEvent;
import com.uxplima.uxmessentials.api.bukkit.event.scoreboard.UxmScoreboardVisibilityEvent;
import com.uxplima.uxmessentials.api.bukkit.event.security.UxmSecurityLockoutEvent;
import com.uxplima.uxmessentials.api.bukkit.event.security.UxmVerificationFailEvent;
import com.uxplima.uxmessentials.api.bukkit.event.security.UxmVerificationPassEvent;
import com.uxplima.uxmessentials.api.bukkit.event.skin.UxmSkinChangeEvent;
import com.uxplima.uxmessentials.api.bukkit.event.staff.UxmStaffChatEvent;
import com.uxplima.uxmessentials.api.bukkit.event.staff.UxmStaffModeEvent;
import com.uxplima.uxmessentials.api.bukkit.event.teleport.UxmBackLocationCaptureEvent;
import com.uxplima.uxmessentials.api.bukkit.event.teleport.UxmPlayerTeleportEvent;
import com.uxplima.uxmessentials.api.bukkit.event.teleport.UxmTeleportRequestAcceptEvent;
import com.uxplima.uxmessentials.api.bukkit.event.teleport.UxmTeleportRequestCancelEvent;
import com.uxplima.uxmessentials.api.bukkit.event.teleport.UxmTeleportRequestDenyEvent;
import com.uxplima.uxmessentials.api.bukkit.event.teleport.UxmTeleportRequestExpireEvent;
import com.uxplima.uxmessentials.api.bukkit.event.teleport.UxmTeleportRequestSendEvent;
import com.uxplima.uxmessentials.api.bukkit.event.teleport.UxmWarmupCancelEvent;
import com.uxplima.uxmessentials.api.bukkit.event.teleport.UxmWarmupStartEvent;
import com.uxplima.uxmessentials.api.bukkit.event.trade.UxmTradeCancelEvent;
import com.uxplima.uxmessentials.api.bukkit.event.trade.UxmTradeCompleteEvent;
import com.uxplima.uxmessentials.api.bukkit.event.vanish.UxmVanishToggleEvent;
import com.uxplima.uxmessentials.api.bukkit.event.vault.UxmVaultContentsChangeEvent;
import com.uxplima.uxmessentials.api.bukkit.event.vault.UxmVaultOpenEvent;
import com.uxplima.uxmessentials.api.bukkit.event.vote.UxmVotePartyEvent;
import com.uxplima.uxmessentials.api.bukkit.event.vote.UxmVoteReceiveEvent;
import com.uxplima.uxmessentials.api.bukkit.event.warp.UxmWarpCreateEvent;
import com.uxplima.uxmessentials.api.bukkit.event.warp.UxmWarpDeleteEvent;
import com.uxplima.uxmessentials.api.bukkit.event.world.UxmWorldAdoptEvent;
import com.uxplima.uxmessentials.api.bukkit.event.world.UxmWorldCreateEvent;
import com.uxplima.uxmessentials.api.bukkit.event.world.UxmWorldDeleteEvent;
import com.uxplima.uxmessentials.api.bukkit.event.world.UxmWorldEntryDeniedEvent;
import com.uxplima.uxmessentials.api.bukkit.event.world.UxmWorldImportEvent;
import com.uxplima.uxmessentials.api.bukkit.event.world.UxmWorldLoadEvent;
import com.uxplima.uxmessentials.api.bukkit.event.world.UxmWorldSettingChangeEvent;
import com.uxplima.uxmessentials.api.bukkit.event.world.UxmWorldUnloadEvent;
import com.uxplima.uxmessentials.api.bukkit.event.world.UxmWorldUnregisterEvent;

/**
 * Every event the stream carries.
 *
 * <p>Written out rather than discovered, because a jar cannot scan a package it did not build and a stream whose
 * contents depend on what happened to be on the classpath is not something anybody can document. A drift guard
 * compares this list against every published event class, so an event added upstream and forgotten here fails a
 * build rather than quietly never arriving.
 *
 * <p>The cancellable pre-events are deliberately not here. Their whole point is the chance to veto, and a
 * subscriber on the far end of a socket cannot answer inside the tick that asked; sending them would be a veto
 * point this add-on cannot honour, and would report every action twice besides.
 */
public final class PublishedEvents {

    /** Every streamed event class, grouped by the context it belongs to. */
    public static final List<Class<? extends UxmEvent>> ALL = List.of(
            // communication
            UxmAnnouncerReloadEvent.class,
            UxmBroadcastOptOutEvent.class,

            // discordlink
            UxmAccountLinkEvent.class,
            UxmAccountUnlinkEvent.class,

            // economy
            UxmBankDepositEvent.class,
            UxmBankWithdrawEvent.class,
            UxmLoanDisburseEvent.class,
            UxmLoanRepayEvent.class,
            UxmWalletCreditEvent.class,
            UxmWalletDebitEvent.class,
            UxmWalletRejectEvent.class,

            // hologram
            UxmHologramCreateEvent.class,
            UxmHologramDeleteEvent.class,

            // home
            UxmHomeCreateEvent.class,
            UxmHomeDeleteEvent.class,
            UxmHomeIconChangeEvent.class,
            UxmHomeLimitReachedEvent.class,
            UxmHomeRelocateEvent.class,
            UxmHomeRenameEvent.class,
            UxmHomeVisibilityChangeEvent.class,

            // itemworld
            UxmEntityPurgeEvent.class,
            UxmMobSpawnEvent.class,

            // invrollback
            UxmInventoryRestoreEvent.class,

            // kit
            UxmKitClaimEvent.class,

            // messaging
            UxmHelpOpEvent.class,
            UxmMailDeliverEvent.class,
            UxmPrivateMessageEvent.class,

            // moderation
            UxmAltDetectedEvent.class,
            UxmIpBanEvent.class,
            UxmJailLocationDefineEvent.class,
            UxmJailLocationRemoveEvent.class,
            UxmPlayerJailEvent.class,
            UxmPlayerMuteEvent.class,
            UxmPlayerTempbanEvent.class,
            UxmPlayerUnjailEvent.class,
            UxmPlayerUnmuteEvent.class,
            UxmPlayerWarnEvent.class,

            // npc
            UxmNpcCreateEvent.class,
            UxmNpcDeleteEvent.class,
            UxmNpcMoveEvent.class,

            // playerstate
            UxmPlayerFeedEvent.class,
            UxmPlayerFlyToggleEvent.class,
            UxmPlayerGameModeChangeEvent.class,
            UxmPlayerGodToggleEvent.class,
            UxmPlayerHealEvent.class,
            UxmPlayerSpeedChangeEvent.class,

            // playerwarp
            UxmPlayerWarpCreateEvent.class,
            UxmPlayerWarpDeleteEvent.class,

            // pose
            UxmPoseEvent.class,

            // presence
            UxmAfkEvent.class,

            // rank
            UxmPrestigeEvent.class,
            UxmRankSetEvent.class,
            UxmRankUpEvent.class,

            // scoreboard
            UxmScoreboardVisibilityEvent.class,

            // staff
            UxmStaffChatEvent.class,
            UxmStaffModeEvent.class,

            // security
            UxmSecurityLockoutEvent.class,
            UxmVerificationFailEvent.class,
            UxmVerificationPassEvent.class,

            // teleport
            UxmBackLocationCaptureEvent.class,
            UxmPlayerTeleportEvent.class,
            UxmTeleportRequestAcceptEvent.class,
            UxmTeleportRequestCancelEvent.class,
            UxmTeleportRequestDenyEvent.class,
            UxmTeleportRequestExpireEvent.class,
            UxmTeleportRequestSendEvent.class,
            UxmWarmupCancelEvent.class,
            UxmWarmupStartEvent.class,

            // trade
            UxmTradeCancelEvent.class,
            UxmTradeCompleteEvent.class,

            // vanish
            UxmSkinChangeEvent.class,
            UxmVanishToggleEvent.class,

            // vault
            UxmVaultContentsChangeEvent.class,
            UxmVaultOpenEvent.class,

            // vote
            UxmVotePartyEvent.class,
            UxmVoteReceiveEvent.class,

            // warp
            UxmWarpCreateEvent.class,
            UxmWarpDeleteEvent.class,

            // world
            UxmWorldAdoptEvent.class,
            UxmWorldCreateEvent.class,
            UxmWorldDeleteEvent.class,
            UxmWorldEntryDeniedEvent.class,
            UxmWorldImportEvent.class,
            UxmWorldLoadEvent.class,
            UxmWorldSettingChangeEvent.class,
            UxmWorldUnloadEvent.class,
            UxmWorldUnregisterEvent.class);

    private PublishedEvents() {}
}
