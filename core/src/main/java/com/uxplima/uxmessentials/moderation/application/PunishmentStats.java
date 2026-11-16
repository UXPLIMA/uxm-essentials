package com.uxplima.uxmessentials.moderation.application;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.moderation.application.port.SanctionHistory.Tally;
import com.uxplima.uxmessentials.moderation.domain.PunishmentReport;
import com.uxplima.uxmessentials.moderation.domain.SanctionAction;
import com.uxplima.uxmessentials.moderation.domain.StaffPunishmentCount;

/**
 * Pure folding of the store's per-issuer counts ({@link Tally}) into a {@link PunishmentReport}: one line per staff
 * member with their bans, mutes, warnings and kicks, ordered into a most-active-staff leaderboard. It reads no port
 * and touches no clock. What counts as one punishment (a {@code /kickall} as one kick, a STRICT ban as one ban) is
 * decided by the store's count, so no read limit can leave rows out.
 *
 * <p>The lifts ({@link SanctionAction#UNBAN}, {@link SanctionAction#UNMUTE}) are reversals rather than punishments,
 * so they are never counted; a staff member who only lifted sanctions does not appear in the leaderboard. Grouping
 * is by issuer identity, the UUID when present, else a console key derived from the name, and each group's display
 * name is the one on its most recent row, so a since-renamed staff member shows their current name. The leaderboard
 * orders by total punishments descending, breaking ties by name so the ordering is stable.
 */
public final class PunishmentStats {

    /** Fold {@code tallies} into a per-staff leaderboard. */
    public PunishmentReport aggregate(List<Tally> tallies) {
        Objects.requireNonNull(tallies, "tallies");
        Map<String, Accumulator> byStaff = new LinkedHashMap<>();
        for (Tally tally : tallies) {
            byStaff.computeIfAbsent(identity(tally), key -> new Accumulator(tally))
                    .add(tally);
        }
        return new PunishmentReport(byStaff.values().stream()
                .map(Accumulator::toCount)
                .filter(count -> count.total() > 0)
                .sorted(Comparator.comparingInt(StaffPunishmentCount::total)
                        .reversed()
                        .thenComparing(count -> count.staffName().toLowerCase(Locale.ROOT))
                        .thenComparing(StaffPunishmentCount::staffName))
                .toList());
    }

    /** The grouping key: the issuer UUID when present, else a console key so console rows fold together by name. */
    private static String identity(Tally tally) {
        return tally.actor().map(UUID::toString).orElseGet(() -> "console:" + tally.actorName());
    }

    /** A mutable per-staff tally, order-independent for the display name. */
    private static final class Accumulator {

        private final Optional<UUID> uuid;
        private String name;
        private Instant latest = Instant.MIN;
        private int bans;
        private int mutes;
        private int warns;
        private int kicks;

        Accumulator(Tally first) {
            this.uuid = first.actor();
            this.name = first.actorName();
        }

        void add(Tally tally) {
            if (tally.latest().isAfter(latest)) {
                name = tally.actorName();
                latest = tally.latest();
            }
            switch (tally.action()) {
                case BAN -> bans += tally.count();
                case MUTE -> mutes += tally.count();
                case WARN -> warns += tally.count();
                case KICK -> kicks += tally.count();
                case UNBAN, UNMUTE -> {
                    // A lift is not a punishment; the store does not count them, and one handed in is ignored.
                }
            }
        }

        StaffPunishmentCount toCount() {
            return new StaffPunishmentCount(uuid, name, bans, mutes, warns, kicks);
        }
    }
}
