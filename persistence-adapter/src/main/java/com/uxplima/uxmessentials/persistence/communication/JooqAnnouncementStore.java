package com.uxplima.uxmessentials.persistence.communication;

import static com.uxplima.uxmessentials.persistence.jooq.tables.CommunicationAnnouncement.COMMUNICATION_ANNOUNCEMENT;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.communication.application.port.AnnouncementStore;
import com.uxplima.uxmessentials.communication.domain.StoredAnnouncement;
import com.uxplima.uxmessentials.persistence.jooq.tables.records.CommunicationAnnouncementRecord;
import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import org.jooq.DSLContext;
import org.jspecify.annotations.NullMarked;

/**
 * The jOOQ-backed {@link AnnouncementStore} over the generated {@code COMMUNICATION_ANNOUNCEMENT} table. An
 * announcement is keyed by its id, so {@link #find} is a single-row {@code SELECT} on the primary key, {@link #save}
 * upserts on that key (a re-save under an existing id overwrites the same row), and {@link #delete} removes by it.
 * {@link #all} and {@link #enabled} are ordered scans the editor list and the announcer merge read respectively.
 * Every statement is typed jOOQ DSL; no SQL is ever string-concatenated.
 *
 * <p>The store is read on the announcer's async loop (the enabled merge) and the editor's async writes, never on
 * the tick thread; it carries no cache, since the announcer reads it at most once per tick and the editor writes
 * are rare, so a cache would only add an invalidation seam for no measurable saving.
 */
@NullMarked
public final class JooqAnnouncementStore extends JooqRepository implements AnnouncementStore {

    public JooqAnnouncementStore(DSLContext dsl) {
        super(dsl);
    }

    @Override
    public List<StoredAnnouncement> all() {
        return read(dsl -> dsl.selectFrom(COMMUNICATION_ANNOUNCEMENT)
                .orderBy(COMMUNICATION_ANNOUNCEMENT.ID.asc())
                .fetch(AnnouncementRows::toAnnouncement));
    }

    @Override
    public List<StoredAnnouncement> enabled() {
        return read(dsl -> dsl.selectFrom(COMMUNICATION_ANNOUNCEMENT)
                .where(COMMUNICATION_ANNOUNCEMENT.ENABLED.ne(0))
                .orderBy(COMMUNICATION_ANNOUNCEMENT.ID.asc())
                .fetch(AnnouncementRows::toAnnouncement));
    }

    @Override
    public Optional<StoredAnnouncement> find(String id) {
        Objects.requireNonNull(id, "id");
        return read(dsl -> dsl.selectFrom(COMMUNICATION_ANNOUNCEMENT)
                .where(COMMUNICATION_ANNOUNCEMENT.ID.eq(id))
                .fetchOptional(AnnouncementRows::toAnnouncement));
    }

    @Override
    public boolean exists(String id) {
        Objects.requireNonNull(id, "id");
        return read(dsl -> dsl.fetchExists(COMMUNICATION_ANNOUNCEMENT, COMMUNICATION_ANNOUNCEMENT.ID.eq(id)));
    }

    @Override
    public void save(StoredAnnouncement announcement) {
        Objects.requireNonNull(announcement, "announcement");
        long savedAt = System.currentTimeMillis();
        write(dsl -> {
            upsert(dsl, announcement, savedAt);
            return null;
        });
    }

    @Override
    public boolean delete(String id) {
        Objects.requireNonNull(id, "id");
        return write(dsl -> dsl.deleteFrom(COMMUNICATION_ANNOUNCEMENT)
                        .where(COMMUNICATION_ANNOUNCEMENT.ID.eq(id))
                        .execute()
                > 0);
    }

    private static void upsert(DSLContext dsl, StoredAnnouncement announcement, long savedAt) {
        CommunicationAnnouncementRecord record = dsl.newRecord(COMMUNICATION_ANNOUNCEMENT);
        AnnouncementRows.apply(record, announcement, savedAt);
        dsl.insertInto(COMMUNICATION_ANNOUNCEMENT)
                .set(record)
                .onConflict(COMMUNICATION_ANNOUNCEMENT.ID)
                .doUpdate()
                .set(COMMUNICATION_ANNOUNCEMENT.LINES, record.getLines())
                .set(COMMUNICATION_ANNOUNCEMENT.CHANNELS, record.getChannels())
                .set(COMMUNICATION_ANNOUNCEMENT.ENABLED, record.getEnabled())
                .set(COMMUNICATION_ANNOUNCEMENT.DISPLAY_CONDITION, record.getDisplayCondition())
                .set(COMMUNICATION_ANNOUNCEMENT.SOUND, record.getSound())
                .set(COMMUNICATION_ANNOUNCEMENT.INTERVAL_SECONDS, record.getIntervalSeconds())
                .set(COMMUNICATION_ANNOUNCEMENT.UPDATED_AT, record.getUpdatedAt())
                .execute();
    }
}
