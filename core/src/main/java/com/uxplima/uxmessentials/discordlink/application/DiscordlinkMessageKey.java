package com.uxplima.uxmessentials.discordlink.application;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;

/**
 * The discord-link context's user-visible message keys. Each constant maps 1:1 to a kebab-case catalog key in
 * {@code messages_<lang>.conf} ({@code DISCORD_LINK_CODE} ↔ {@code discordlink.code}); the constant is the
 * compile-time handle, the catalog holds the text. There are no inline player-facing literals in the context —
 * every message resolves through one of these.
 *
 * <p>These render only the in-game side ({@code /discordlink}, {@code /discordunlink}). The text the bridge
 * replies with inside Discord is not a player-locale catalog key — it is sent on JDA's own thread to a Discord
 * user with no Minecraft locale — so the slash handler carries its own short literals, not these keys.
 *
 * <p>Per the i18n contract a disabled module still ships its keys so the catalog stays whole and the
 * locale-parity guard sees the full {@code en} key set.
 */
public enum DiscordlinkMessageKey implements MessageKey {

    // /discordlink — issuing and reporting a code
    DISCORD_LINK_CODE("discordlink.code"),
    DISCORD_LINK_HOWTO("discordlink.howto"),
    DISCORD_LINK_ALREADY("discordlink.already"),

    // /discordlink status
    DISCORD_LINK_STATUS_LINKED("discordlink.status.linked"),
    DISCORD_LINK_STATUS_UNLINKED("discordlink.status.unlinked"),

    // /discordunlink
    DISCORD_LINK_UNLINKED("discordlink.unlinked"),
    DISCORD_LINK_NOT_LINKED("discordlink.not-linked"),

    // console rejection
    DISCORD_LINK_PLAYERS_ONLY("discordlink.players-only");

    private final String key;

    DiscordlinkMessageKey(String key) {
        this.key = key;
    }

    @Override
    public String key() {
        return key;
    }
}
