package com.uxplima.uxmessentials.persistence.messaging;

import static com.uxplima.uxmessentials.persistence.jooq.tables.MessagingReplyPartners.MESSAGING_REPLY_PARTNERS;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.messaging.application.port.ReplyPartnerStore;
import com.uxplima.uxmessentials.messaging.domain.LastConversation;
import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jooq.DSLContext;

/**
 * The jOOQ-backed {@link ReplyPartnerStore} over {@code messaging_reply_partners}: one row per player, holding their
 * last partner and when that conversation was last touched, in epoch milliseconds.
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
        String partner = conversation.partner().uuid().toString();
        String name = conversation.partner().name();
        long touched = conversation.lastTouched().toEpochMilli();
        write(dsl -> dsl.insertInto(MESSAGING_REPLY_PARTNERS)
                .set(MESSAGING_REPLY_PARTNERS.OWNER_UUID, owner.toString())
                .set(MESSAGING_REPLY_PARTNERS.PARTNER_UUID, partner)
                .set(MESSAGING_REPLY_PARTNERS.PARTNER_NAME, name)
                .set(MESSAGING_REPLY_PARTNERS.TOUCHED_AT, touched)
                .onConflict(MESSAGING_REPLY_PARTNERS.OWNER_UUID)
                .doUpdate()
                .set(MESSAGING_REPLY_PARTNERS.PARTNER_UUID, partner)
                .set(MESSAGING_REPLY_PARTNERS.PARTNER_NAME, name)
                .set(MESSAGING_REPLY_PARTNERS.TOUCHED_AT, touched)
                .execute());
    }
}
