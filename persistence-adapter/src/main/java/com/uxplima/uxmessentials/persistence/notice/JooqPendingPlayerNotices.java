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
 * so a value may hold any character. A take deletes each row on its own and returns only the lines whose delete removed
 * a row, so two takes at once hand each line to one of them. A row whose values do not decode is removed and logged
 * rather than shown wrong.
 */
public final class JooqPendingPlayerNotices extends JooqRepository implements PendingPlayerNotices {

    private final Logger log;

    public JooqPendingPlayerNotices(DSLContext dsl, Logger log) {
        super(dsl);
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public void keep(PlayerNotice notice) {
        Objects.requireNonNull(notice, "notice");
        write(dsl -> dsl.insertInto(PLAYER_NOTICES)
                .set(PLAYER_NOTICES.ID, UUID.randomUUID().toString())
                .set(PLAYER_NOTICES.RECIPIENT, notice.recipient().toString())
                .set(PLAYER_NOTICES.MESSAGE_KEY, notice.line().key().key())
                .set(PLAYER_NOTICES.ARGS, encode(notice.line().args()))
                .set(PLAYER_NOTICES.CREATED_AT, notice.at().toEpochMilli())
                .execute());
    }

    @Override
    public List<PlayerNotice> take(UUID recipient) {
        Objects.requireNonNull(recipient, "recipient");
        List<PlayerNoticesRecord> rows = read(dsl -> dsl.selectFrom(PLAYER_NOTICES)
                .where(PLAYER_NOTICES.RECIPIENT.eq(recipient.toString()))
                .orderBy(PLAYER_NOTICES.CREATED_AT, PLAYER_NOTICES.ID)
                .fetch());
        List<PlayerNotice> taken = new ArrayList<>();
        for (PlayerNoticesRecord row : rows) {
            String id = row.getId();
            boolean removed;
            try {
                removed = write(dsl -> dsl.deleteFrom(PLAYER_NOTICES)
                                .where(PLAYER_NOTICES.ID.eq(id))
                                .execute()
                        == 1);
            } catch (RuntimeException failure) {
                // The lines removed so far are gone from the table, so they are handed back to be shown rather than
                // lost with the throw; the rest stay kept for the next take.
                log.error(
                        "kept notice take stopped for player " + recipient + " after " + taken.size() + " of "
                                + rows.size() + " lines",
                        failure);
                break;
            }
            if (!removed) {
                continue;
            }
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
