package com.uxplima.uxmessentials.persistence.communication;

import static com.uxplima.uxmessentials.persistence.jooq.tables.CommunicationAnnouncement.COMMUNICATION_ANNOUNCEMENT;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

import com.uxplima.uxmessentials.communication.domain.StoredAnnouncement;
import com.uxplima.uxmessentials.persistence.jooq.tables.records.CommunicationAnnouncementRecord;
import com.uxplima.uxmessentials.shared.display.BroadcastChannel;
import org.jooq.Record;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The anti-corruption mapping between a {@code communication_announcement} row and the {@link StoredAnnouncement}
 * the editor reads and writes. The lines are newline-joined into one TEXT cell and split back on read; the channels
 * are a comma-separated list of {@link BroadcastChannel} names, parsed leniently so an unknown name is skipped (an
 * empty result falls back to {@code CHAT} in the domain record). The {@code enabled} INTEGER flag round-trips as a
 * boolean, the nullable {@code sound} as an {@link Optional}, and the nullable {@code interval_seconds} as an
 * {@link OptionalLong}. This class is the single place that translation lives.
 *
 * <p>The {@code updated_at} column is a write-only audit stamp set at save time; the read path does not return it.
 */
@NullMarked
final class AnnouncementRows {

    private static final String LINE_SEPARATOR = "\n";
    private static final String CHANNEL_SEPARATOR = ",";

    private AnnouncementRows() {}

    /** Rebuild a {@link StoredAnnouncement} from a queried row. */
    static StoredAnnouncement toAnnouncement(Record row) {
        return new StoredAnnouncement(
                row.get(COMMUNICATION_ANNOUNCEMENT.ID),
                splitLines(row.get(COMMUNICATION_ANNOUNCEMENT.LINES)),
                parseChannels(row.get(COMMUNICATION_ANNOUNCEMENT.CHANNELS)),
                row.get(COMMUNICATION_ANNOUNCEMENT.ENABLED) != 0,
                nullToEmpty(row.get(COMMUNICATION_ANNOUNCEMENT.DISPLAY_CONDITION)),
                Optional.ofNullable(blankToNull(row.get(COMMUNICATION_ANNOUNCEMENT.SOUND))),
                interval(row.get(COMMUNICATION_ANNOUNCEMENT.INTERVAL_SECONDS)));
    }

    /** Populate a record from a stored announcement for an upsert, stamping the save timestamp. */
    static void apply(CommunicationAnnouncementRecord record, StoredAnnouncement announcement, long savedAtMillis) {
        record.setId(announcement.id());
        record.setLines(String.join(LINE_SEPARATOR, announcement.lines()));
        record.setChannels(joinChannels(announcement.channels()));
        record.setEnabled(announcement.enabled() ? 1 : 0);
        record.setDisplayCondition(announcement.condition());
        record.setSound(announcement.sound().orElse(null));
        record.setIntervalSeconds(
                announcement.intervalSeconds().isPresent()
                        ? announcement.intervalSeconds().getAsLong()
                        : null);
        record.setUpdatedAt(savedAtMillis);
    }

    private static List<String> splitLines(String joined) {
        // A persisted announcement always has at least one line, so an empty cell never reaches here; still, a
        // defensive split keeps a stray empty string from violating the non-empty invariant on read.
        List<String> lines = new ArrayList<>();
        for (String line : joined.split(LINE_SEPARATOR, -1)) {
            lines.add(line);
        }
        if (lines.isEmpty()) {
            lines.add(" ");
        }
        return lines;
    }

    private static String joinChannels(Set<BroadcastChannel> channels) {
        List<String> names = new ArrayList<>();
        for (BroadcastChannel channel : channels) {
            names.add(channel.name());
        }
        return String.join(CHANNEL_SEPARATOR, names);
    }

    private static Set<BroadcastChannel> parseChannels(String csv) {
        Set<BroadcastChannel> channels = new LinkedHashSet<>();
        for (String token : csv.split(CHANNEL_SEPARATOR, -1)) {
            String trimmed = token.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                channels.add(BroadcastChannel.valueOf(trimmed.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException unknown) {
                // Skip an unknown channel rather than failing the whole row; CHAT is the domain default.
            }
        }
        return channels;
    }

    private static OptionalLong interval(@Nullable Long seconds) {
        return seconds == null || seconds <= 0 ? OptionalLong.empty() : OptionalLong.of(seconds);
    }

    private static String nullToEmpty(@Nullable String value) {
        return value == null ? "" : value;
    }

    private static @Nullable String blankToNull(@Nullable String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
