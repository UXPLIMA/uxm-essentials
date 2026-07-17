package com.uxplima.uxmessentials.messaging.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.messaging.application.port.VanishVisibility;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.vanish.adapter.outbound.InMemoryVanishStore;
import com.uxplima.uxmessentials.vanish.domain.VanishLevel;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * The migrated messaging vanish gate reads the vanish {@code VanishStore} authority directly: a vanished target is
 * hidden from a viewer who lacks the vanish-see node, visible to one who holds it, and never hidden when not vanished.
 */
class AuthorityVanishVisibilityTest {

    private final InMemoryVanishStore store = new InMemoryVanishStore();
    private final FakePermissions permissions = new FakePermissions();
    private final PlayerRef viewer = new PlayerRef(UUID.randomUUID(), "Viewer");
    private final PlayerRef target = new PlayerRef(UUID.randomUUID(), "Target");

    @Test
    void aVanishedTargetIsHiddenFromAViewerWithoutTheSeeNode() {
        store.vanish(target.uuid(), VanishLevel.DEFAULT);
        VanishVisibility vanish = new AuthorityVanishVisibility(store, permissions);

        assertThat(vanish.isHiddenFrom(viewer, target)).isTrue();
    }

    @Test
    void aVanishedTargetIsVisibleToAViewerHoldingTheSeeNode() {
        store.vanish(target.uuid(), VanishLevel.DEFAULT);
        permissions.grant(viewer, "uxmessentials.vanish.see");
        VanishVisibility vanish = new AuthorityVanishVisibility(store, permissions);

        assertThat(vanish.isHiddenFrom(viewer, target)).isFalse();
    }

    @Test
    void aNonVanishedTargetIsNeverHidden() {
        VanishVisibility vanish = new AuthorityVanishVisibility(store, permissions);

        assertThat(vanish.isHiddenFrom(viewer, target)).isFalse();
    }

    private static final class FakePermissions implements Permissions {
        private final Set<String> grants = new HashSet<>();

        void grant(PlayerRef who, String node) {
            grants.add(who.uuid() + "|" + node);
        }

        @Override
        public boolean has(PlayerRef who, String node) {
            return grants.contains(who.uuid() + "|" + node);
        }

        @Override
        public QuotaResult resolveQuota(
                PlayerRef who, QuotaFamily family, @Nullable WorldRef world, long configDefault) {
            return QuotaResult.unlimited();
        }
    }
}
