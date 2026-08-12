package com.uxplima.uxmessentials.shared.application.placeholder;

import java.util.List;
import java.util.stream.Stream;

import com.uxplima.uxmessentials.shared.application.module.ModuleId;

/** What a player is to other players: their presence, their messages, the chat, staff, Discord and sanctions. */
final class SocialPlaceholderKeys {

    private static final ModuleId PRESENCE = ModuleId.of("presence");
    private static final ModuleId MESSAGING = ModuleId.of("messaging");
    private static final ModuleId COMMUNICATION = ModuleId.of("communication");
    private static final ModuleId STAFF = ModuleId.of("staff");
    private static final ModuleId DISCORDLINK = ModuleId.of("discordlink");
    private static final ModuleId MODERATION = ModuleId.of("moderation");

    private SocialPlaceholderKeys() {}

    static List<PlaceholderSpec> all() {
        return Stream.of(presence(), messaging(), communication(), staff(), discordlink(), moderation())
                .flatMap(List::stream)
                .toList();
    }

    private static List<PlaceholderSpec> presence() {
        return List.of(
                PlaceholderSpec.of("afk", "Whether the player is away (yes/no).", PlaceholderScope.SESSION, PRESENCE),
                PlaceholderSpec.of(
                        "afk_duration",
                        "How long the player has been away, in the compact 1h30m form.",
                        PlaceholderScope.SESSION,
                        PRESENCE),
                PlaceholderSpec.of(
                        "vanished", "Whether the player is vanished (yes/no).", PlaceholderScope.SESSION, PRESENCE),
                PlaceholderSpec.of(
                        "presence_afk", "Whether the player is away (yes/no).", PlaceholderScope.SESSION, PRESENCE),
                PlaceholderSpec.of(
                        "presence_afk_duration",
                        "How long the player has been away, in the compact 1h30m form.",
                        PlaceholderScope.SESSION,
                        PRESENCE),
                PlaceholderSpec.of(
                        "presence_afk_since",
                        "The same away duration, under the spelling a config may prefer.",
                        PlaceholderScope.SESSION,
                        PRESENCE),
                PlaceholderSpec.of(
                        "presence_afk_reason",
                        "The reason the player gave when they went away.",
                        PlaceholderScope.SESSION,
                        PRESENCE),
                PlaceholderSpec.of(
                        "presence_vanished",
                        "Whether the player is vanished (yes/no).",
                        PlaceholderScope.SESSION,
                        PRESENCE),
                PlaceholderSpec.of(
                        "presence_nickname",
                        "The player's nickname, or their name when they have not set one.",
                        PlaceholderScope.SESSION,
                        PRESENCE),
                PlaceholderSpec.of(
                        "presence_realname",
                        "The player's account name, whatever nickname they wear.",
                        PlaceholderScope.SESSION,
                        PRESENCE));
    }

    private static List<PlaceholderSpec> messaging() {
        return List.of(
                PlaceholderSpec.of(
                        "messaging_mail_unread",
                        "How much mail the player has not read.",
                        PlaceholderScope.PLAYER,
                        MESSAGING),
                PlaceholderSpec.of(
                        "messaging_mail_total", "How much mail the player holds.", PlaceholderScope.PLAYER, MESSAGING),
                PlaceholderSpec.of(
                        "messaging_ignoring_count",
                        "How many players this player is ignoring.",
                        PlaceholderScope.PLAYER,
                        MESSAGING),
                PlaceholderSpec.of(
                        "messaging_reply_target",
                        "Who /r would answer: the last player this one talked to.",
                        PlaceholderScope.SESSION,
                        MESSAGING),
                PlaceholderSpec.of(
                        "messaging_msgtoggle",
                        "Whether the player accepts private messages (yes/no).",
                        PlaceholderScope.SESSION,
                        MESSAGING),
                PlaceholderSpec.of(
                        "messaging_socialspy",
                        "Whether the player is watching other players' messages (yes/no).",
                        PlaceholderScope.SESSION,
                        MESSAGING));
    }

    private static List<PlaceholderSpec> communication() {
        return List.of(
                PlaceholderSpec.of(
                        "communication_chat_enabled",
                        "Whether public chat is open rather than locked by /togglechat (yes/no).",
                        PlaceholderScope.GLOBAL,
                        COMMUNICATION),
                PlaceholderSpec.of(
                        "communication_broadcasts",
                        "Whether the player receives the rotating announcements (yes/no).",
                        PlaceholderScope.SESSION,
                        COMMUNICATION));
    }

    private static List<PlaceholderSpec> staff() {
        return List.of(
                PlaceholderSpec.of(
                        "staff_mode", "Whether the player is in staff mode (yes/no).", PlaceholderScope.SESSION, STAFF),
                PlaceholderSpec.of(
                        "staff_online", "How many staff members are connected.", PlaceholderScope.GLOBAL, STAFF),
                PlaceholderSpec.of(
                        "staff_count",
                        "The same connected-staff count, under the spelling a config may prefer.",
                        PlaceholderScope.GLOBAL,
                        STAFF));
    }

    private static List<PlaceholderSpec> discordlink() {
        return List.of(
                PlaceholderSpec.of(
                        "discordlink_linked",
                        "Whether the account is bound to a Discord user (yes/no).",
                        PlaceholderScope.PLAYER,
                        DISCORDLINK),
                PlaceholderSpec.of(
                        "discordlink_id", "The bound Discord user id.", PlaceholderScope.PLAYER, DISCORDLINK));
    }

    private static List<PlaceholderSpec> moderation() {
        return List.of(
                PlaceholderSpec.of(
                        "muted", "Whether the player is muted (yes/no).", PlaceholderScope.PLAYER, MODERATION),
                PlaceholderSpec.of(
                        "jailed", "Whether the player is jailed (yes/no).", PlaceholderScope.PLAYER, MODERATION),
                PlaceholderSpec.of(
                        "moderation_banned",
                        "Whether the player is banned (yes/no).",
                        PlaceholderScope.PLAYER,
                        MODERATION),
                PlaceholderSpec.of(
                        "moderation_muted",
                        "Whether the player is muted (yes/no).",
                        PlaceholderScope.PLAYER,
                        MODERATION),
                PlaceholderSpec.of(
                        "moderation_jailed",
                        "Whether the player is jailed (yes/no).",
                        PlaceholderScope.PLAYER,
                        MODERATION),
                PlaceholderSpec.of(
                        "moderation_frozen",
                        "Whether the player is frozen in place by staff (yes/no).",
                        PlaceholderScope.SESSION,
                        MODERATION),
                PlaceholderSpec.of(
                        "moderation_warns",
                        "How many warnings the player carries.",
                        PlaceholderScope.PLAYER,
                        MODERATION),
                PlaceholderSpec.of(
                        "moderation_ban_reason", "Why the player was banned.", PlaceholderScope.PLAYER, MODERATION),
                PlaceholderSpec.of(
                        "moderation_ban_issuer", "Who banned the player.", PlaceholderScope.PLAYER, MODERATION),
                PlaceholderSpec.of(
                        "moderation_ban_remaining",
                        "How long is left on the ban, in the compact 1d2h form.",
                        PlaceholderScope.PLAYER,
                        MODERATION),
                PlaceholderSpec.of(
                        "moderation_ban_remaining_formatted",
                        "The same remaining ban, under the spelling a config may prefer.",
                        PlaceholderScope.PLAYER,
                        MODERATION),
                PlaceholderSpec.of(
                        "moderation_mute_reason", "Why the player was muted.", PlaceholderScope.PLAYER, MODERATION),
                PlaceholderSpec.of(
                        "moderation_mute_issuer", "Who muted the player.", PlaceholderScope.PLAYER, MODERATION),
                PlaceholderSpec.of(
                        "moderation_mute_remaining",
                        "How long is left on the mute, in the compact 1d2h form.",
                        PlaceholderScope.PLAYER,
                        MODERATION),
                PlaceholderSpec.of(
                        "moderation_mute_remaining_formatted",
                        "The same remaining mute, under the spelling a config may prefer.",
                        PlaceholderScope.PLAYER,
                        MODERATION));
    }
}
