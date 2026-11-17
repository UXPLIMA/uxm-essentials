package com.uxplima.uxmessentials.economy.application;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.economy.domain.EconomyMessageKey;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.domain.NameMatch;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.message.MessageKey;
import org.junit.jupiter.api.Test;

class TargetLookupTest {

    private static final PlayerRef STEVE = new PlayerRef(UUID.randomUUID(), "Steve");

    private final List<String> told = new ArrayList<>();
    private final EconomyNotifier notifier = new EconomyNotifier(
            (PlayerRef viewer, MessageKey key, Map<String, String> placeholders) -> {
                told.add(viewer.name() + " " + key.key() + " " + placeholders);
                return key.key();
            },
            (viewer, text) -> {});

    @Test
    void aNameThatIsFoundIsAnsweredAndNobodyIsTold() {
        TargetLookup lookup = new TargetLookup(new Known(), notifier);

        NameMatch match = lookup.find(ALEX, "Steve", EconomyMessageKey.PAY_TARGET_LOOKUP_FAILED);

        assertThat(match.player()).contains(STEVE);
        assertThat(told).isEmpty();
    }

    @Test
    void aLookupThatFailsTellsThePlayerWhoTypedTheNameAndStillFails() {
        TargetLookup lookup = new TargetLookup(new Unreadable(), notifier);

        assertThatThrownBy(() -> lookup.find(ALEX, "Steve", EconomyMessageKey.PAY_TARGET_LOOKUP_FAILED))
                .isInstanceOf(IllegalStateException.class);
        assertThat(told).containsExactly(ALEX.name() + " pay.target-lookup-failed {player=Steve}");
    }

    /** Knows Steve, online or not. */
    private static final class Known implements PlayerLookup {
        @Override
        public Optional<PlayerRef> findOnlineByName(String name) {
            return Optional.empty();
        }

        @Override
        public Optional<PlayerRef> findByName(String name) {
            return name.equalsIgnoreCase(STEVE.name()) ? Optional.of(STEVE) : Optional.empty();
        }

        @Override
        public Optional<PlayerRef> findByUuid(UUID uuid) {
            return Optional.empty();
        }

        @Override
        public boolean isOnline(UUID uuid) {
            return false;
        }
    }

    /** A lookup whose store cannot be read. */
    private static final class Unreadable implements PlayerLookup {
        @Override
        public Optional<PlayerRef> findOnlineByName(String name) {
            return Optional.empty();
        }

        @Override
        public NameMatch matchByName(String name) {
            throw new IllegalStateException("the player data cannot be read");
        }

        @Override
        public Optional<PlayerRef> findByUuid(UUID uuid) {
            return Optional.empty();
        }

        @Override
        public boolean isOnline(UUID uuid) {
            return false;
        }
    }
}
