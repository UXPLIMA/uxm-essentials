package com.uxplima.uxmessentials.persistence.moderation;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.moderation.domain.Issuer;
import com.uxplima.uxmessentials.moderation.domain.SanctionAction;
import com.uxplima.uxmessentials.moderation.domain.SanctionHistoryEntry;
import com.uxplima.uxmessentials.persistence.jooq.tables.records.ModerationSanctionHistoryRecord;
import org.jspecify.annotations.Nullable;

/**
 * The anti-corruption mapping between a {@code moderation_sanction_history} row and the domain
 * {@link SanctionHistoryEntry}. The {@link SanctionAction} is stored as its enum name; UUIDs are the canonical
 * 36-character text and instants epoch milliseconds, identical to every other moderation table. A null
 * {@code ip} rebuilds to a UUID-scoped row, a null {@code expires_at} to a permanent sanction or a lift, and a
 * null {@code actor} to a console issuer (the name is always present).
 */
final class SanctionHistoryRows {

    private SanctionHistoryRows() {}

    static SanctionHistoryEntry toEntry(ModerationSanctionHistoryRecord row) {
        return new SanctionHistoryEntry(
                SanctionAction.valueOf(row.getAction()),
                UUID.fromString(row.getTarget()),
                issuer(row.getActor(), row.getActorName()),
                Optional.ofNullable(row.getReason()),
                Instant.ofEpochMilli(row.getTs()),
                Optional.ofNullable(row.getExpiresAt()).map(Instant::ofEpochMilli),
                Optional.ofNullable(row.getIp()));
    }

    private static Issuer issuer(@Nullable String uuid, String name) {
        return Issuer.stored(Optional.ofNullable(uuid).map(UUID::fromString), name);
    }
}
