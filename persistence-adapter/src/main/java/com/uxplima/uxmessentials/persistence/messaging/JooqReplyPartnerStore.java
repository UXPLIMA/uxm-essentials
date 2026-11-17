package com.uxplima.uxmessentials.persistence.messaging;

import static com.uxplima.uxmessentials.persistence.jooq.tables.MessagingReplyPartners.MESSAGING_REPLY_PARTNERS;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.messaging.application.port.ReplyPartnerStore;
import com.uxplima.uxmessentials.messaging.domain.LastConversation;
import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import com.uxplima.uxmessentials.persistence.runtime.PersistenceException;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jooq.DSLContext;

/**
 * The jOOQ-backed {@link ReplyPartnerStore} over {@code messaging_reply_partners}: one row per player, holding their
 * last partner and when that conversation was last touched, in epoch milliseconds. A conversation is written only
 * over one touched at the same time or earlier, so two backends writing at once keep the newer one: an update guarded
 * by {@code touched_at}, then an insert when no row exists, and the guarded update again when another backend
 * inserted first.
 */
public final class JooqReplyPartnerStore extends JooqRepository implements ReplyPartnerStore {

    public JooqReplyPartnerStore(DSLContext dsl) {
        super(dsl);
    }

    @Override
    public Optional<LastConversation> load(UUID owner) {
        Objects.requireNonNull(owner, "owner");
        return read(dsl -> dsl.selectFrom(MESSAGING_REPLY_PARTNERS)
                        .where(MESSAGING_REPLY_PARTNERS.OWNER_UUID.eq(owner.toString()))
                        .fetchOptional())
                .map(row -> LastConversation.with(
                        new PlayerRef(UUID.fromString(row.getPartnerUuid()), row.getPartnerName()),
                        Instant.ofEpochMilli(row.getTouchedAt())));
    }

    @Override
    public void save(UUID owner, LastConversation conversation) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(conversation, "conversation");
        String key = owner.toString();
        if (write(dsl -> updateIfNotNewer(dsl, key, conversation)) > 0) {
            return;
        }
        if (read(dsl -> dsl.fetchExists(MESSAGING_REPLY_PARTNERS, MESSAGING_REPLY_PARTNERS.OWNER_UUID.eq(key)))) {
            return; // the stored conversation was touched later than this one
        }
        try {
            write(dsl -> dsl.insertInto(MESSAGING_REPLY_PARTNERS)
                    .set(MESSAGING_REPLY_PARTNERS.OWNER_UUID, key)
                    .set(
                            MESSAGING_REPLY_PARTNERS.PARTNER_UUID,
                            conversation.partner().uuid().toString())
                    .set(
                            MESSAGING_REPLY_PARTNERS.PARTNER_NAME,
                            conversation.partner().name())
                    .set(
                            MESSAGING_REPLY_PARTNERS.TOUCHED_AT,
                            conversation.lastTouched().toEpochMilli())
                    .execute());
        } catch (PersistenceException raced) {
            // Another backend inserted the row between the existence check and this insert.
            write(dsl -> updateIfNotNewer(dsl, key, conversation));
        }
    }

    private static int updateIfNotNewer(DSLContext dsl, String owner, LastConversation conversation) {
        long touched = conversation.lastTouched().toEpochMilli();
        return dsl.update(MESSAGING_REPLY_PARTNERS)
                .set(
                        MESSAGING_REPLY_PARTNERS.PARTNER_UUID,
                        conversation.partner().uuid().toString())
                .set(
                        MESSAGING_REPLY_PARTNERS.PARTNER_NAME,
                        conversation.partner().name())
                .set(MESSAGING_REPLY_PARTNERS.TOUCHED_AT, touched)
                .where(MESSAGING_REPLY_PARTNERS.OWNER_UUID.eq(owner))
                .and(MESSAGING_REPLY_PARTNERS.TOUCHED_AT.le(touched))
                .execute();
    }
}
