package com.uxplima.uxmessentials.messaging.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.uxplima.uxmessentials.messaging.application.port.VanishVisibility;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vanish.adapter.outbound.InMemoryVanishStore;
import com.uxplima.uxmessentials.vanish.application.port.VanishLevelResolver;
import com.uxplima.uxmessentials.vanish.domain.VanishLevel;
import org.junit.jupiter.api.Test;

/**
 * The migrated messaging vanish gate reads the vanish {@code VanishStore} authority directly, comparing the viewer's
 * resolved see level against the target's use level: a vanished target is hidden from a viewer whose see level is below
 * it, visible to one whose see level clears it, and never hidden when not vanished.
 */
class AuthorityVanishVisibilityTest {

    private final InMemoryVanishStore store = new InMemoryVanishStore();
    private final FakeLevels levels = new FakeLevels();
    private final PlayerRef viewer = new PlayerRef(UUID.randomUUID(), "Viewer");
    private final PlayerRef target = new PlayerRef(UUID.randomUUID(), "Target");

    @Test
    void aVanishedTargetIsHiddenFromAViewerBelowItsUseLevel() {
        store.vanish(target.uuid(), VanishLevel.DEFAULT);
        VanishVisibility vanish = new AuthorityVanishVisibility(store, levels);

        assertThat(vanish.isHiddenFrom(viewer, target)).isTrue();
    }

    @Test
    void aVanishedTargetIsVisibleToAViewerWhoseSeeLevelClearsIt() {
        store.vanish(target.uuid(), VanishLevel.DEFAULT);
        levels.seeLevels.put(viewer.uuid(), 1);
        VanishVisibility vanish = new AuthorityVanishVisibility(store, levels);

        assertThat(vanish.isHiddenFrom(viewer, target)).isFalse();
    }

    @Test
    void aHigherUseLevelStillHidesFromALowerSeeLevelViewer() {
        store.vanish(target.uuid(), VanishLevel.of(2));
        levels.seeLevels.put(viewer.uuid(), 1);
        VanishVisibility vanish = new AuthorityVanishVisibility(store, levels);

        assertThat(vanish.isHiddenFrom(viewer, target)).isTrue();
    }

    @Test
    void aNonVanishedTargetIsNeverHidden() {
        VanishVisibility vanish = new AuthorityVanishVisibility(store, levels);

        assertThat(vanish.isHiddenFrom(viewer, target)).isFalse();
    }

    private static final class FakeLevels implements VanishLevelResolver {
        private final Map<UUID, Integer> seeLevels = new HashMap<>();

        @Override
        public VanishLevel useLevel(PlayerRef who) {
            return VanishLevel.DEFAULT;
        }

        @Override
        public int seeLevel(PlayerRef who) {
            return seeLevels.getOrDefault(who.uuid(), 0);
        }
    }
}
