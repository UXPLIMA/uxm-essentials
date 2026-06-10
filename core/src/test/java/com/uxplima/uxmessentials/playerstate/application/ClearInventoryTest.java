package com.uxplima.uxmessentials.playerstate.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.uxplima.uxmessentials.playerstate.application.port.PlayerEffects;
import com.uxplima.uxmessentials.playerstate.domain.AirAmount;
import com.uxplima.uxmessentials.playerstate.domain.BurnDuration;
import com.uxplima.uxmessentials.playerstate.domain.ExperienceChange;
import com.uxplima.uxmessentials.playerstate.domain.FoodLevel;
import com.uxplima.uxmessentials.playerstate.domain.FreezeDuration;
import com.uxplima.uxmessentials.playerstate.domain.HealthLevel;
import com.uxplima.uxmessentials.playerstate.domain.PersonalTime;
import com.uxplima.uxmessentials.playerstate.domain.PersonalWeather;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@code /clearinventory} ({@code /ci}, {@code /clear}): a live-only verb that empties a player's inventory
 * through the {@link PlayerEffects} port without touching the persisted snapshot or publishing a domain event.
 * Pins the two confirmation shapes — a self clear that notifies only the actor, and a staff clear that confirms
 * to the actor with the subject's name and to the subject themselves.
 */
class ClearInventoryTest {

    private RecordingEffects effects;
    private CapturingSink sink;
    private PlayerStateNotifier notifier;
    private PlayerRef alice;
    private PlayerRef bob;

    @BeforeEach
    void setUp() {
        effects = new RecordingEffects();
        sink = new CapturingSink();
        notifier = new PlayerStateNotifier(new KeyMessages(), sink);
        alice = new PlayerRef(UUID.randomUUID(), "Alice");
        bob = new PlayerRef(UUID.randomUUID(), "Bob");
    }

    @Test
    void selfClearEmptiesTheInventoryAndConfirmsToTheActor() {
        ClearInventory clear = new ClearInventory(effects, notifier);

        clear.clear(alice);

        assertThat(effects.cleared).containsExactly(alice.uuid());
        assertThat(sink.sent).containsExactly(new Sent(alice.uuid(), PlayerstateMessageKey.INVENTORY_CLEARED.key()));
    }

    @Test
    void staffClearEmptiesTheSubjectAndConfirmsToBothParties() {
        ClearInventory clear = new ClearInventory(effects, notifier);

        clear.clearFor(alice, bob);

        assertThat(effects.cleared).containsExactly(bob.uuid());
        assertThat(sink.sent)
                .containsExactly(
                        new Sent(alice.uuid(), PlayerstateMessageKey.INVENTORY_CLEARED_OTHER.key()),
                        new Sent(bob.uuid(), PlayerstateMessageKey.INVENTORY_CLEARED.key()));
        assertThat(sink.placeholderFor(alice.uuid(), "player")).isEqualTo("Bob");
    }

    private record Sent(UUID viewer, String resolvedKey) {}

    /** An effects port that records every inventory it was asked to clear. */
    private static final class RecordingEffects implements PlayerEffects {
        private final List<UUID> cleared = new ArrayList<>();

        @Override
        public void clearInventory(PlayerRef who) {
            cleared.add(who.uuid());
        }

        @Override
        public void heal(PlayerRef who, boolean clearEffects) {}

        @Override
        public void feed(PlayerRef who) {}

        @Override
        public void extinguish(PlayerRef who) {}

        @Override
        public void kill(PlayerRef who) {}

        @Override
        public boolean toggleNightVision(PlayerRef who) {
            return false;
        }

        @Override
        public boolean toggleGlow(PlayerRef who) {
            return false;
        }

        @Override
        public void applyTime(PlayerRef who, PersonalTime time) {}

        @Override
        public void applyWeather(PlayerRef who, PersonalWeather weather) {}

        @Override
        public void applyExperience(
                PlayerRef who,
                ExperienceChange change,
                java.util.function.Consumer<PlayerEffects.ExperienceReport> onResult) {}

        @Override
        public void setRemainingAir(PlayerRef who, AirAmount air) {}

        @Override
        public void setFire(PlayerRef who, BurnDuration duration) {}

        @Override
        public void setFreeze(PlayerRef who, FreezeDuration duration) {}

        @Override
        public void setFoodLevel(PlayerRef who, FoodLevel value) {}

        @Override
        public void setHealth(PlayerRef who, HealthLevel value) {}

        @Override
        public void resetRest(PlayerRef who) {}
    }

    /** A sink that records the viewer and the resolved key text for each delivery. */
    private static final class CapturingSink implements MessageSink {
        private final List<Sent> sent = new ArrayList<>();
        private final Map<UUID, Map<String, String>> placeholders = new HashMap<>();

        @Override
        public void deliver(PlayerRef viewer, String renderedText) {
            sent.add(new Sent(viewer.uuid(), renderedText));
        }

        @Nullable String placeholderFor(UUID viewer, String name) {
            return placeholders.getOrDefault(viewer, Map.of()).get(name);
        }
    }

    /** A resolver that returns the bare key and records the placeholders it was handed. */
    private final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            sink.placeholders.put(viewer.uuid(), placeholders);
            return key.key();
        }
    }
}
