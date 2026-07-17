package com.uxplima.uxmessentials.vanish.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vanish.application.port.VanishBuffs;
import com.uxplima.uxmessentials.vanish.application.port.VanishLevelResolver;
import com.uxplima.uxmessentials.vanish.application.port.VanishStore;
import com.uxplima.uxmessentials.vanish.application.port.VanishView;
import com.uxplima.uxmessentials.vanish.domain.VanishLevel;
import com.uxplima.uxmessentials.vanish.domain.VanishState;
import org.junit.jupiter.api.Test;

/**
 * {@link ToggleVanish} flips the store, reconciles the view, and — the Phase 3 addition — applies the configured buffs
 * when a player vanishes and clears them when they reappear, so a hidden player is buffed and a revealed player is not.
 * Every entry point (bare {@code /vanish}, the absolute {@link ToggleVanish#setVanished}) routes through the same
 * apply/clear here, which is what keeps buffs uniform across the command, staff-mode vanish, and the settings panel.
 */
class ToggleVanishTest {

    private final FakeStore store = new FakeStore();
    private final RecordingView view = new RecordingView();
    private final FakeLevels levels = new FakeLevels();
    private final RecordingBuffs buffs = new RecordingBuffs();
    private final ToggleVanish toggleVanish =
            new ToggleVanish(store, view, levels, new VanishNotifier(new KeyMessages(), new DiscardingSink()), buffs);

    private final PlayerRef who = new PlayerRef(UUID.randomUUID(), "Who");

    @Test
    void vanishingAppliesBuffsAndReappearingClearsThem() {
        boolean vanished = toggleVanish.toggle(who);
        assertThat(vanished).isTrue();
        assertThat(buffs.applied).containsExactly(who);
        assertThat(buffs.cleared).isEmpty();

        boolean revealed = toggleVanish.toggle(who);
        assertThat(revealed).isFalse();
        assertThat(buffs.cleared).containsExactly(who); // buffs cleared on reappear
    }

    @Test
    void setVanishedAppliesBuffsOnlyOnAStateChange() {
        toggleVanish.setVanished(who, true);
        assertThat(buffs.applied).containsExactly(who);

        toggleVanish.setVanished(who, true); // already vanished — no second apply
        assertThat(buffs.applied).containsExactly(who);
    }

    private static final class RecordingBuffs implements VanishBuffs {
        private final List<PlayerRef> applied = new ArrayList<>();
        private final List<PlayerRef> cleared = new ArrayList<>();

        @Override
        public void apply(PlayerRef target) {
            applied.add(target);
        }

        @Override
        public void clear(PlayerRef target) {
            cleared.add(target);
        }
    }

    private static final class RecordingView implements VanishView {
        @Override
        public void hide(PlayerRef target, VanishLevel level) {}

        @Override
        public void reveal(PlayerRef target) {}
    }

    private static final class FakeLevels implements VanishLevelResolver {
        @Override
        public VanishLevel useLevel(PlayerRef p) {
            return VanishLevel.DEFAULT;
        }

        @Override
        public int seeLevel(PlayerRef p) {
            return 0;
        }
    }

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    private static final class DiscardingSink implements MessageSink {
        @Override
        public void deliver(PlayerRef viewer, String renderedText) {}
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
