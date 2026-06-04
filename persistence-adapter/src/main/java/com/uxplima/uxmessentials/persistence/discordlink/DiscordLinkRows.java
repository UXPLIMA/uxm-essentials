package com.uxplima.uxmessentials.persistence.discordlink;

import java.time.Instant;
import java.util.UUID;

import com.uxplima.uxmessentials.discordlink.domain.ConfirmedLink;
import com.uxplima.uxmessentials.discordlink.domain.DiscordId;
import com.uxplima.uxmessentials.discordlink.domain.LinkCode;
import com.uxplima.uxmessentials.discordlink.domain.PendingLink;
import com.uxplima.uxmessentials.persistence.jooq.tables.DiscordLinkPending;
import com.uxplima.uxmessentials.persistence.jooq.tables.DiscordLinks;
import org.jooq.Record;

/**
 * The anti-corruption mapping between the discord-link rows and their domain value objects. The player uuid is
 * stored as its canonical 36-char text, the code and Discord id as their string forms, and both instants as
 * epoch-millis in a BIGINT (the same portable shape every other context uses for a stored instant). This class
 * is the single place that translation lives.
 */
final class DiscordLinkRows {

    private static final DiscordLinkPending PENDING = DiscordLinkPending.DISCORD_LINK_PENDING;
    private static final DiscordLinks LINKS = DiscordLinks.DISCORD_LINKS;

    private DiscordLinkRows() {}

    /** Rebuild a {@link PendingLink} from a queried pending row. */
    static PendingLink toPending(Record row) {
        return new PendingLink(
                UUID.fromString(row.get(PENDING.PLAYER)),
                new LinkCode(row.get(PENDING.CODE)),
                Instant.ofEpochMilli(row.get(PENDING.EXPIRES_AT)));
    }

    /** Rebuild a {@link ConfirmedLink} from a queried link row. */
    static ConfirmedLink toConfirmed(Record row) {
        return new ConfirmedLink(
                UUID.fromString(row.get(LINKS.PLAYER)),
                new DiscordId(row.get(LINKS.DISCORD_ID)),
                Instant.ofEpochMilli(row.get(LINKS.LINKED_AT)));
    }
}
