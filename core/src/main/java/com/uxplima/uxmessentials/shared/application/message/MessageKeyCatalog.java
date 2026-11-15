package com.uxplima.uxmessentials.shared.application.message;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.uxplima.uxmessentials.bossbar.domain.BossBarMessageKey;
import com.uxplima.uxmessentials.commandcontrol.domain.CommandControlMessageKey;
import com.uxplima.uxmessentials.communication.domain.CommunicationMessageKey;
import com.uxplima.uxmessentials.customcommands.domain.CustomCommandsMessageKey;
import com.uxplima.uxmessentials.custommenus.domain.CustomMenusMessageKey;
import com.uxplima.uxmessentials.economy.domain.EconomyMessageKey;
import com.uxplima.uxmessentials.guard.domain.GuardMessageKey;
import com.uxplima.uxmessentials.holograms.domain.HologramsMessageKey;
import com.uxplima.uxmessentials.homes.domain.HomesMessageKey;
import com.uxplima.uxmessentials.invrollback.domain.InvrollbackMessageKey;
import com.uxplima.uxmessentials.itemworld.domain.ItemworldMessageKey;
import com.uxplima.uxmessentials.kits.domain.KitsMessageKey;
import com.uxplima.uxmessentials.messaging.domain.MessagingMessageKey;
import com.uxplima.uxmessentials.moderation.domain.ModerationMessageKey;
import com.uxplima.uxmessentials.npc.domain.NpcMessageKey;
import com.uxplima.uxmessentials.playerstate.domain.PlayerstateMessageKey;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerwarpsMessageKey;
import com.uxplima.uxmessentials.poses.domain.PosesMessageKey;
import com.uxplima.uxmessentials.presence.domain.PresenceMessageKey;
import com.uxplima.uxmessentials.ranks.domain.RanksMessageKey;
import com.uxplima.uxmessentials.scoreboard.domain.ScoreboardMessageKey;
import com.uxplima.uxmessentials.security.domain.SecurityMessageKey;
import com.uxplima.uxmessentials.shared.domain.message.GuiMessageKey;
import com.uxplima.uxmessentials.shared.domain.message.MessageKey;
import com.uxplima.uxmessentials.shared.domain.message.SharedMessageKey;
import com.uxplima.uxmessentials.skin.domain.SkinMessageKey;
import com.uxplima.uxmessentials.staff.domain.StaffMessageKey;
import com.uxplima.uxmessentials.survival.domain.SurvivalMessageKey;
import com.uxplima.uxmessentials.teleport.domain.TeleportMessageKey;
import com.uxplima.uxmessentials.trade.domain.TradeMessageKey;
import com.uxplima.uxmessentials.vanish.domain.VanishMessageKey;
import com.uxplima.uxmessentials.vaults.domain.VaultsMessageKey;
import com.uxplima.uxmessentials.villagers.domain.VillagersMessageKey;
import com.uxplima.uxmessentials.vote.domain.VoteMessageKey;
import com.uxplima.uxmessentials.warps.domain.WarpsMessageKey;
import com.uxplima.uxmessentials.worlds.domain.WorldEditorMessageKey;
import com.uxplima.uxmessentials.worlds.domain.WorldsMessageKey;

/**
 * The single registry of every shipped {@link MessageKey} constant, across the {@code shared} common
 * block and every feature context (the twelve landed contexts plus the round-3 {@code communication} module).
 *
 * <p>This is the one place that enumerates the per-module key enums, so the catalog is whole regardless
 * of which modules are enabled at runtime (a disabled module still ships its keys).
 * Two collaborators read it: the {@code GlobalTranslator} wiring (which registers every key for the
 * {@code translatable} path) and the locale-parity guard (which asserts this constant set equals
 * {@code messages_en.conf}'s key set, bidirectionally). Adding a context's enum here is the one edit a
 * new module makes to join the parity matrix.
 */
public final class MessageKeyCatalog {

    /** The per-module key enums, in the registry's dependency-first order; {@code shared} first. */
    private static final List<MessageKey[]> ENUMS = List.of(
            SharedMessageKey.values(),
            GuiMessageKey.values(),
            TeleportMessageKey.values(),
            WorldsMessageKey.values(),
            WorldEditorMessageKey.values(),
            HomesMessageKey.values(),
            EconomyMessageKey.values(),
            WarpsMessageKey.values(),
            KitsMessageKey.values(),
            PlayerstateMessageKey.values(),
            VanishMessageKey.values(),
            MessagingMessageKey.values(),
            PresenceMessageKey.values(),
            ModerationMessageKey.values(),
            ItemworldMessageKey.values(),
            VaultsMessageKey.values(),
            CommunicationMessageKey.values(),
            HologramsMessageKey.values(),
            PlayerwarpsMessageKey.values(),
            ScoreboardMessageKey.values(),
            BossBarMessageKey.values(),
            VoteMessageKey.values(),
            StaffMessageKey.values(),
            NpcMessageKey.values(),
            CustomMenusMessageKey.values(),
            CustomCommandsMessageKey.values(),
            PosesMessageKey.values(),
            SurvivalMessageKey.values(),
            RanksMessageKey.values(),
            SecurityMessageKey.values(),
            CommandControlMessageKey.values(),
            TradeMessageKey.values(),
            VillagersMessageKey.values(),
            InvrollbackMessageKey.values(),
            GuardMessageKey.values(),
            SkinMessageKey.values());

    private MessageKeyCatalog() {}

    /** Every shipped {@link MessageKey} constant, deduplicated by identity, in declaration order. */
    public static Set<MessageKey> all() {
        Set<MessageKey> keys = new LinkedHashSet<>();
        for (MessageKey[] block : ENUMS) {
            for (MessageKey key : block) {
                keys.add(key);
            }
        }
        return Set.copyOf(keys);
    }

    /** Every shipped catalog lookup string ({@link MessageKey#key()}), for the parity matrix. */
    public static Set<String> allKeys() {
        Set<String> keys = new LinkedHashSet<>();
        for (MessageKey key : all()) {
            keys.add(key.key());
        }
        return Set.copyOf(keys);
    }
}
