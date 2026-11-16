package com.uxplima.uxmessentials.persistence.messaging;

import static com.uxplima.uxmessentials.persistence.jooq.tables.Mail.MAIL;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.function.ToLongFunction;

import com.uxplima.uxmessentials.messaging.application.port.MailRepository;
import com.uxplima.uxmessentials.messaging.domain.MailBox;
import com.uxplima.uxmessentials.messaging.domain.MailId;
import com.uxplima.uxmessentials.messaging.domain.MailItem;
import com.uxplima.uxmessentials.persistence.jooq.tables.records.MailRecord;
import com.uxplima.uxmessentials.persistence.runtime.IntegrityViolation;
import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import com.uxplima.uxmessentials.persistence.runtime.PersistenceException;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;

/**
 * The jOOQ-backed {@link MailRepository} over the generated {@code MAIL} table. A recipient's
 * {@link MailBox} is rebuilt from queryable rows newest-first; the unread count is a {@code COUNT(*)} so the
 * notify never materialises the box. An {@code append} takes {@code max(id)+1} inside the insert transaction
 * (the schema's {@code id} is a portable plain {@code BIGINT}, not a dialect-specific auto-increment) and takes
 * a fresh key when another writer committed that one first. Mark-read and clear are keyed updates/deletes,
 * and the expiry sweep deletes by send time. Every statement is typed jOOQ DSL; no SQL is ever
 * string-concatenated. Mail is text-only: there are no item attachments.
 */
public final class JooqMailRepository extends JooqRepository implements MailRepository {

    /** How often an append whose key another writer took first is tried again with a fresh key. */
    static final int APPEND_ATTEMPTS = 3;

    private final ToLongFunction<DSLContext> firstFreeId;

    public JooqMailRepository(DSLContext dsl) {
        this(dsl, JooqMailRepository::maxIdPlusOne);
    }

    /** With {@code firstFreeId} standing in for the key read, so a test can hand out a key that is already taken. */
    JooqMailRepository(DSLContext dsl, ToLongFunction<DSLContext> firstFreeId) {
        super(dsl);
        this.firstFreeId = Objects.requireNonNull(firstFreeId, "firstFreeId");
    }

    @Override
    public MailBox load(PlayerRef recipient) {
        Objects.requireNonNull(recipient, "recipient");
        List<MailItem> items = read(dsl -> dsl.selectFrom(MAIL)
                .where(MAIL.RECIPIENT.eq(recipient.uuid().toString()))
                .orderBy(MAIL.SENT_AT.desc(), MAIL.ID.desc())
                .fetch()
                .map(row -> MailRows.toItem(row, recipient)));
        return MailBox.of(recipient, items);
    }

    @Override
    public long unreadCount(PlayerRef recipient) {
        Objects.requireNonNull(recipient, "recipient");
        return read(dsl -> dsl.fetchCount(
                MAIL, MAIL.RECIPIENT.eq(recipient.uuid().toString()).and(MAIL.IS_READ.eq((short) 0))));
    }

    @Override
    public MailItem append(MailItem item) {
        Objects.requireNonNull(item, "item");
        // Two writers (another server, the importer's parallel lanes) can read the same maximum; the one that
        // commits second hits the primary key and reads the maximum again, so a sent mail is never dropped.
        for (int attempt = 1; ; attempt++) {
            try {
                long assignedId = write(dsl -> insert(dsl, item));
                return item.withId(MailId.of(assignedId));
            } catch (PersistenceException collision) {
                if (!IntegrityViolation.causedBy(collision) || attempt >= APPEND_ATTEMPTS) {
                    throw collision;
                }
            }
        }
    }

    @Override
    public void markAllRead(PlayerRef recipient) {
        Objects.requireNonNull(recipient, "recipient");
        write(dsl -> dsl.update(MAIL)
                .set(MAIL.IS_READ, (short) 1)
                .where(MAIL.RECIPIENT.eq(recipient.uuid().toString()))
                .execute());
    }

    @Override
    public void clear(PlayerRef recipient) {
        Objects.requireNonNull(recipient, "recipient");
        write(dsl -> dsl.deleteFrom(MAIL)
                .where(MAIL.RECIPIENT.eq(recipient.uuid().toString()))
                .execute());
    }

    @Override
    public int deleteSentBefore(Instant cutoff) {
        Objects.requireNonNull(cutoff, "cutoff");
        return write(dsl -> dsl.deleteFrom(MAIL)
                .where(MAIL.SENT_AT.lt(cutoff.toEpochMilli()))
                .execute());
    }

    private long insert(DSLContext dsl, MailItem item) {
        long id = firstFreeId.applyAsLong(dsl);
        MailRecord record = dsl.newRecord(MAIL);
        MailRows.apply(record, item, id);
        dsl.insertInto(MAIL).set(record).execute();
        return id;
    }

    private static long maxIdPlusOne(DSLContext dsl) {
        Long maxId = dsl.select(DSL.max(MAIL.ID)).from(MAIL).fetchOne(0, Long.class);
        return (maxId == null ? 0L : maxId) + 1L;
    }
}
