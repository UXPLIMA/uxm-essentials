package com.uxplima.uxmessentials.vanish.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vanish.application.port.VanishLevelResolver;
import com.uxplima.uxmessentials.vanish.application.port.VanishStore;
import com.uxplima.uxmessentials.vanish.application.port.VanishView;
import com.uxplima.uxmessentials.vanish.domain.VanishLevel;
import com.uxplima.uxmessentials.vanish.domain.VanishState;
import org.junit.jupiter.api.Test;

/**
 * {@link SetVanishLevel} re-resolves a vanished player's use level from their permissions and re-applies their
 * visibility; a non-vanished player is left untouched. The re-apply writes the fresh level to the store first, then
 * reconciles the view at that level so every {@code canSee} reader agrees.
 */
class SetVanishLevelTest {

    private final FakeStore store = new FakeStore();
    private final RecordingView view = new RecordingView();
    private final FakeLevels levels = new FakeLevels();
    private final SetVanishLevel setVanishLevel = new SetVanishLevel(store, view, levels);

    private final PlayerRef who = new PlayerRef(UUID.randomUUID(), "Who");

    @Test
    void aNonVanishedPlayerIsLeftUntouched() {
        Optional<VanishLevel> applied = setVanishLevel.reapply(who);

        assertThat(applied).isEmpty();
        assertThat(view.hidden).isEmpty();
    }

    @Test
    void aVanishedPlayerHasTheirLevelReResolvedAndReapplied() {
        store.vanish(who.uuid(), VanishLevel.DEFAULT); // currently at level 1
        levels.useLevel = VanishLevel.of(3); // permissions now say level 3

        Optional<VanishLevel> applied = setVanishLevel.reapply(who);

        assertThat(applied).contains(VanishLevel.of(3));
        assertThat(store.levelOf(who.uuid())).contains(VanishLevel.of(3)); // store updated first
        assertThat(view.hidden).containsExactly(VanishLevel.of(3)); // then reconciled at the new level
    }

    private static final class RecordingView implements VanishView {
        private final List<VanishLevel> hidden = new ArrayList<>();

        @Override
        public void hide(PlayerRef target, VanishLevel level) {
            hidden.add(level);
        }

        @Override
        public void reveal(PlayerRef target) {}
    }

    private static final class FakeLevels implements VanishLevelResolver {
        private VanishLevel useLevel = VanishLevel.DEFAULT;

        @Override
        public VanishLevel useLevel(PlayerRef p) {
            return useLevel;
        }

        @Override
        public int seeLevel(PlayerRef p) {
            return 0;
        }
    }

    private static final class FakeStore implements VanishStore {
        private final ConcurrentHashMap<UUID, VanishLevel> vanished = new ConcurrentHashMap<>();

        @Override
        public boolean isVanished(UUID p) {
            return vanished.containsKey(p);
        }

        @Override
        public void vanish(UUID p, VanishLevel level) {
            vanished.put(p, level);
        }

        @Override
        public void reveal(UUID p) {
            vanished.remove(p);
        }

        @Override
        public Optional<VanishLevel> levelOf(UUID p) {
            return Optional.ofNullable(vanished.get(p));
        }

        @Override
        public Set<UUID> vanished() {
            return Set.copyOf(vanished.keySet());
        }

        @Override
        public VanishState snapshot() {
            return new VanishState(vanished);
        }
    }
}
