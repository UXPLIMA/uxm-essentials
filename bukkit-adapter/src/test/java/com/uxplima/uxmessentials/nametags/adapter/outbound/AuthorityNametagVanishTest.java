package com.uxplima.uxmessentials.nametags.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.nametags.application.port.NametagVanish;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.vanish.adapter.outbound.InMemoryVanishStore;
import com.uxplima.uxmessentials.vanish.domain.VanishLevel;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * The migrated nametags vanish gate reads the vanish {@code VanishStore} authority directly: a viewer sees a wearer's
 * nametag when the wearer is not vanished, or the viewer holds the vanish-see node, and cannot when the wearer is
 * vanished and they lack it — the inverse polarity of the messaging gate over the same one state.
 */
class AuthorityNametagVanishTest {

    private final InMemoryVanishStore store = new InMemoryVanishStore();
    private final FakePermissions permissions = new FakePermissions();
    private final PlayerRef viewer = new PlayerRef(UUID.randomUUID(), "Viewer");
    private final PlayerRef wearer = new PlayerRef(UUID.randomUUID(), "Wearer");

    @Test
    void everyoneSeesANonVanishedWearersNametag() {
        NametagVanish vanish = new AuthorityNametagVanish(store, permissions);

        assertThat(vanish.canSee(viewer, wearer)).isTrue();
    }

    @Test
    void aVanishedWearersNametagIsHiddenFromAViewerWithoutTheSeeNode() {
        store.vanish(wearer.uuid(), VanishLevel.DEFAULT);
        NametagVanish vanish = new AuthorityNametagVanish(store, permissions);

        assertThat(vanish.canSee(viewer, wearer)).isFalse();
    }

    @Test
    void aVanishedWearersNametagIsVisibleToAViewerHoldingTheSeeNode() {
        store.vanish(wearer.uuid(), VanishLevel.DEFAULT);
        permissions.grant(viewer, "uxmessentials.vanish.see");
        NametagVanish vanish = new AuthorityNametagVanish(store, permissions);

        assertThat(vanish.canSee(viewer, wearer)).isTrue();
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
