package com.uxplima.uxmessentials.moderation.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.moderation.domain.Issuer;
import com.uxplima.uxmessentials.moderation.domain.MuteState;
import com.uxplima.uxmessentials.moderation.domain.TempbanState;
import com.uxplima.uxmessentials.moderation.fakes.FakeModerationRepository;
import com.uxplima.uxmessentials.moderation.fakes.ModerationFakes;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@code /banlist} and {@code /mutelist}: read-only reviews of the sanctions still in effect. A populated list
 * renders a header with the count then one entry per sanction (the target name resolved from the projection's
 * UUID); an empty list renders the empty notice. Expired tempbans and lifted mutes never appear because the
 * repository filters them at the query.
 */
class SanctionListsTest {

    private FakeModerationRepository repo;
    private CapturingNotifier notifier;
    private ModerationFakes.FixedPlayers players;
    private Clock clock;
    private PlayerRef staff;
    private PlayerRef target;

    @BeforeEach
    void setUp() {
        repo = new FakeModerationRepository();
        notifier = new CapturingNotifier();
        clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC);
        staff = new PlayerRef(UUID.randomUUID(), "Staff");
        target = new PlayerRef(UUID.randomUUID(), "Griefer");
        Map<UUID, PlayerRef> byUuid = new HashMap<>();
        byUuid.put(staff.uuid(), staff);
        byUuid.put(target.uuid(), target);
        players = new ModerationFakes.FixedPlayers(byUuid, Set.of(staff.uuid()));
    }

    @Test
    void banListWithNoBansRendersEmpty() {
        new ListBans(repo, players, notifier.notifier(), clock).list(staff);

        assertThat(notifier.keys).containsExactly("moderation.banlist-empty");
    }

    @Test
    void banListRendersHeaderAndEntryPerBan() {
        repo.saveTempban(
                target,
                TempbanState.active(
                        Instant.EPOCH.plusSeconds(3600), Issuer.console("Staff"), Optional.of("x"), Instant.EPOCH));

        new ListBans(repo, players, notifier.notifier(), clock).list(staff);

        assertThat(notifier.keys).containsExactly("moderation.banlist-header", "moderation.banlist-entry");
    }

    @Test
    void muteListWithNoMutesRendersEmpty() {
        new ListMutes(repo, players, notifier.notifier(), clock).list(staff);

        assertThat(notifier.keys).containsExactly("moderation.mutelist-empty");
    }

    @Test
    void muteListRendersHeaderAndEntryPerMute() {
        repo.saveMute(target, MuteState.permanent(Issuer.console("Staff"), Optional.of("spam"), Instant.EPOCH));

        new ListMutes(repo, players, notifier.notifier(), clock).list(staff);

        assertThat(notifier.keys).containsExactly("moderation.mutelist-header", "moderation.mutelist-entry");
    }

    private static final class CapturingNotifier {
        private final List<String> keys = new ArrayList<>();

        ModerationNotifier notifier() {
            return new ModerationNotifier(new RecordingMessages(keys), new NoopSink());
        }
    }

    private static final class RecordingMessages implements Messages {
        private final List<String> keys;

        RecordingMessages(List<String> keys) {
            this.keys = keys;
        }

        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            keys.add(key.key());
            return key.key();
        }
    }

    private static final class NoopSink implements MessageSink {
        @Override
        public void deliver(PlayerRef viewer, String renderedText) {}
    }
}
