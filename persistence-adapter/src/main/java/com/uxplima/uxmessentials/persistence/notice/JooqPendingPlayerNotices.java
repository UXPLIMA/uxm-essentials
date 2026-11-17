package com.uxplima.uxmessentials.persistence.notice;

import static com.uxplima.uxmessentials.persistence.jooq.tables.PlayerNotices.PLAYER_NOTICES;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

import com.uxplima.uxmessentials.persistence.jooq.tables.records.PlayerNoticesRecord;
import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.PendingPlayerNotices;
import com.uxplima.uxmessentials.shared.domain.PlayerNotice;
import com.uxplima.uxmessentials.shared.domain.message.MessageKey;
import com.uxplima.uxmessentials.shared.domain.message.MessageLine;
import org.jooq.DSLContext;

/**
 * jOOQ-backed {@link PendingPlayerNotices} over the {@code player_notices} table, one row per kept line. The values a
 * line is filled with are stored as {@code name=value} pairs joined by {@code &}, each side percent-encoded as UTF-8,
 * so a value may hold any character. A row whose values do not decode is removed and logged rather than shown wrong.
 *
 * <p>A keep drops the recipient's oldest rows past {@code keptPerPlayer} in the same transaction, so one player never
 * holds more. A take reads the recipient's rows and removes them with one delete, in one transaction; a delete that
 * removed fewer rows than were read means another server took some of them first, so that attempt is rolled back,
 * having removed nothing, and the take reads what is left again, up to {@link #TAKE_ATTEMPTS} times. A line is therefore handed to one taker only,
 * and a take the database fails leaves every row where it was.
 */
public final class JooqPendingPlayerNotices extends JooqRepository implements PendingPlayerNotices {

    /** How many times a take that lost a race to another server reads again. */
    static final int TAKE_ATTEMPTS = 3;

    private final Logger log;
    private final int keptPerPlayer;

    /** @param keptPerPlayer how many lines are kept for one player at most, at least one */
    public JooqPendingPlayerNotices(DSLContext dsl, Logger log, int keptPerPlayer) {
        super(dsl);
        this.log = Objects.requireNonNull(log, "log");
        if (keptPerPlayer < 1) {
            throw new IllegalArgumentException("keptPerPlayer must be at least 1: " + keptPerPlayer);
        }
        this.keptPerPlayer = keptPerPlayer;
    }

    @Override
    public void keep(PlayerNotice notice) {
        Objects.requireNonNull(notice, "notice");
        String recipient = notice.recipient().toString();
        write(dsl -> {
            dsl.insertInto(PLAYER_NOTICES)
                    .set(PLAYER_NOTICES.ID, UUID.randomUUID().toString())
                    .set(PLAYER_NOTICES.RECIPIENT, recipient)
                    .set(PLAYER_NOTICES.MESSAGE_KEY, notice.line().key().key())
                    .set(PLAYER_NOTICES.ARGS, encode(notice.line().args()))
                    .set(PLAYER_NOTICES.CREATED_AT, notice.at().toEpochMilli())
                    .execute();
            // The ids are read and then deleted by name: MySQL refuses a delete that reads its own table.
            List<String> newestFirst = dsl.select(PLAYER_NOTICES.ID)
                    .from(PLAYER_NOTICES)
                    .where(PLAYER_NOTICES.RECIPIENT.eq(recipient))
                    .orderBy(PLAYER_NOTICES.CREATED_AT.desc(), PLAYER_NOTICES.ID.desc())
                    .fetch(PLAYER_NOTICES.ID);
            if (newestFirst.size() <= keptPerPlayer) {
                return 0;
            }
            return dsl.deleteFrom(PLAYER_NOTICES)
                    .where(PLAYER_NOTICES.ID.in(newestFirst.subList(keptPerPlayer, newestFirst.size())))
                    .execute();
        });
    }

    @Override
    public List<PlayerNotice> take(UUID recipient) {
        Objects.requireNonNull(recipient, "recipient");
        // A take that lost a race reads again what the other server left; a few tries settle it.
        for (int attempt = 1; ; attempt++) {
            try {
                return decoded(recipient, takeRows(recipient));
            } catch (LostRace race) {
                if (attempt >= TAKE_ATTEMPTS) {
                    // The rows left wait for the next take, at the player's next join or the next kept line.
                    return List.of();
                }
            }
        }
    }

    /** Remove and return {@code recipient}'s rows, or throw {@link LostRace} having removed none. */
    private List<PlayerNoticesRecord> takeRows(UUID recipient) {
        return write(dsl -> {
            List<PlayerNoticesRecord> read = dsl.selectFrom(PLAYER_NOTICES)
                    .where(PLAYER_NOTICES.RECIPIENT.eq(recipient.toString()))
                    .orderBy(PLAYER_NOTICES.CREATED_AT, PLAYER_NOTICES.ID)
                    .fetch();
            if (read.isEmpty()) {
                return read;
            }
            List<String> ids = read.stream().map(PlayerNoticesRecord::getId).toList();
            int removed = dsl.deleteFrom(PLAYER_NOTICES)
                    .where(PLAYER_NOTICES.ID.in(ids))
                    .execute();
            if (removed != ids.size()) {
                throw new LostRace();
            }
            return read;
        });
    }

    /** The lines {@code rows} hold; a row whose values do not read is logged and left out. */
    private List<PlayerNotice> decoded(UUID recipient, List<PlayerNoticesRecord> rows) {
        List<PlayerNotice> taken = new ArrayList<>(rows.size());
        for (PlayerNoticesRecord row : rows) {
            Map<String, String> args;
            try {
                args = decode(row.getArgs());
            } catch (IllegalArgumentException malformed) {
                log.warn("event=player_notice_unreadable recipient={} key={}", recipient, row.getMessageKey());
                continue;
            }
            taken.add(new PlayerNotice(
                    recipient,
                    new MessageLine(new StoredKey(row.getMessageKey()), args),
                    Instant.ofEpochMilli(row.getCreatedAt())));
        }
        return taken;
    }

    @Override
    public int purgeOlderThan(Instant cutoff) {
        Objects.requireNonNull(cutoff, "cutoff");
        return write(dsl -> dsl.deleteFrom(PLAYER_NOTICES)
                .where(PLAYER_NOTICES.CREATED_AT.lt(cutoff.toEpochMilli()))
                .execute());
    }

    /** Rolls back a take that found another server had taken some of its lines first. */
    private static final class LostRace extends RuntimeException {

        private static final long serialVersionUID = 1L;

        LostRace() {
            super(null, null, false, false);
        }
    }

    /** The values in name order, so one line always stores the same text. */
    private static String encode(Map<String, String> args) {
        return new TreeMap<>(args)
                .entrySet().stream()
                        .map(arg -> escape(arg.getKey()) + "=" + escape(arg.getValue()))
                        .collect(Collectors.joining("&"));
    }

    private static Map<String, String> decode(String args) {
        Map<String, String> decoded = new LinkedHashMap<>();
        if (args.isEmpty()) {
            return decoded;
        }
        for (String pair : args.split("&", -1)) {
            int split = pair.indexOf('=');
            if (split < 0) {
                throw new IllegalArgumentException("a value without a name: " + pair);
            }
            decoded.put(
                    URLDecoder.decode(pair.substring(0, split), StandardCharsets.UTF_8),
                    URLDecoder.decode(pair.substring(split + 1), StandardCharsets.UTF_8));
        }
        return decoded;
    }

    private static String escape(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8);
    }

    /** A catalogue key read back from a row. */
    private record StoredKey(String key) implements MessageKey {}
}
