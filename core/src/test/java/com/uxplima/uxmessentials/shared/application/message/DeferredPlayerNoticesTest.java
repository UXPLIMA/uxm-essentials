package com.uxplima.uxmessentials.shared.application.message;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.port.PendingPlayerNotices;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.domain.PlayerNotice;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.message.MessageKey;
import com.uxplima.uxmessentials.shared.domain.message.MessageLine;
import org.junit.jupiter.api.Test;

/**
 * A line for a player online here is shown at once; one for a player who is away waits and is shown once, when they
 * are next online. A player who left before it was shown keeps it.
 */
class DeferredPlayerNoticesTest {

    private static final PlayerRef SAM = new PlayerRef(UUID.fromString("00000000-0000-0000-0000-000000000005"), "Sam");
    private static final MessageKey PAID = () -> "economy.pay-received";
    private static final MessageLine LINE = new MessageLine(PAID, Map.of("player", "Alex", "amount", "$40.00"));
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2030-05-01T12:00:00Z"), ZoneOffset.UTC);

    private final Online online = new Online();
    private final Kept kept = new Kept();
    private final List<String> shown = new ArrayList<>();
    private final DeferredPlayerNotices notices = new DeferredPlayerNotices(
            kept,
            online,
            new Notifier(
                    (viewer, key, args) -> key.key() + " " + args.get("player") + " " + args.get("amount"),
                    (viewer, text) -> shown.add(viewer.name() + ": " + text)),
            CLOCK);

    @Test
    void aPlayerOnlineHereIsToldAtOnce() {
        online.here.add(SAM.uuid());

        notices.tell(SAM, LINE);

        assertThat(shown).containsExactly("Sam: economy.pay-received Alex $40.00");
        assertThat(kept.rows).isEmpty();
    }

    @Test
    void aPlayerWhoIsAwayIsToldOnceWhenTheyAreNextOnline() {
        notices.tell(SAM, LINE);
        assertThat(shown).isEmpty();

        online.here.add(SAM.uuid());
        assertThat(notices.deliver(SAM)).isEqualTo(1);
        assertThat(notices.deliver(SAM)).isZero();

        assertThat(shown).containsExactly("Sam: economy.pay-received Alex $40.00");
    }

    @Test
    void aPlayerWhoLeftBeforeTheLineWasShownKeepsIt() {
        notices.tell(SAM, LINE);

        notices.deliver(SAM);

        assertThat(shown).isEmpty();
        assertThat(kept.rows.get(SAM.uuid())).singleElement().satisfies(notice -> {
            assertThat(notice.line()).isEqualTo(LINE);
            assertThat(notice.at()).isEqualTo(CLOCK.instant());
        });
    }

    /** Who is online on this server. */
    private static final class Online implements PlayerLookup {

        private final Set<UUID> here = new HashSet<>();

        @Override
        public Optional<PlayerRef> findOnlineByName(String name) {
            return Optional.empty();
        }

        @Override
        public Optional<PlayerRef> findByUuid(UUID uuid) {
            return Optional.empty();
        }

        @Override
        public boolean isOnline(UUID uuid) {
            return here.contains(uuid);
        }
    }

    /** The kept lines, as the database holds them. */
    private static final class Kept implements PendingPlayerNotices {

        private final Map<UUID, List<PlayerNotice>> rows = new HashMap<>();

        @Override
        public void keep(PlayerNotice notice) {
            rows.computeIfAbsent(notice.recipient(), id -> new ArrayList<>()).add(notice);
        }

        @Override
        public List<PlayerNotice> take(UUID recipient) {
            List<PlayerNotice> taken = rows.remove(recipient);
            return taken == null ? List.of() : List.copyOf(taken);
        }
    }
}
