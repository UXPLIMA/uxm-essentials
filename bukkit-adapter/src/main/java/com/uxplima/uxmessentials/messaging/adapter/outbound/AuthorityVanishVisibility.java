package com.uxplima.uxmessentials.messaging.adapter.outbound;

import java.util.Objects;

import com.uxplima.uxmessentials.messaging.application.port.VanishVisibility;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vanish.application.port.NetworkVanishStore;
import com.uxplima.uxmessentials.vanish.application.port.VanishLevelResolver;
import com.uxplima.uxmessentials.vanish.application.port.VanishStore;
import com.uxplima.uxmessentials.vanish.domain.VanishLevels;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link VanishVisibility} implementation, reading the vanish context's single {@link VanishStore} authority
 * directly (rather than an indirect {@code canSee} projection). A {@code target} is hidden from a {@code viewer} when
 * the target is vanished in the store and the viewer's see level is below the target's use level, so a sender who
 * cannot see a vanished player resolves them as if offline, while staff whose see level clears the target's use level
 * can still message them. The rule itself is the pure {@code VanishState#canSee}; this adapter only supplies the two
 * inputs (the store's vanished level and the viewer's see level, resolved through the shared {@link VanishLevelResolver}
 * so the layered see/use semantics apply here exactly as they do in the world).
 *
 * <p>A target on another backend of the network is not in the local store, so the network-wide vanish view answers for
 * them: a player vanished elsewhere is hidden from {@code /msg} here exactly as they would be on their own backend.
 *
 * <p>Bound only when the vanish module is enabled; with vanish off the wiring binds {@link VanishVisibility#ALWAYS_VISIBLE}
 * instead, so messaging degrades to "no one is hidden" rather than failing.
 */
@NullMarked
public final class AuthorityVanishVisibility implements VanishVisibility {

    private final VanishStore store;
    private final NetworkVanishStore network;
    private final VanishLevelResolver levels;

    public AuthorityVanishVisibility(VanishStore store, NetworkVanishStore network, VanishLevelResolver levels) {
        this.store = Objects.requireNonNull(store, "store");
        this.network = Objects.requireNonNull(network, "network");
        this.levels = Objects.requireNonNull(levels, "levels");
    }

    @Override
    public boolean isHiddenFrom(PlayerRef viewer, PlayerRef target) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(target, "target");
        if (viewer.equals(target)) {
            return false;
        }
        int seeLevel = levels.seeLevel(viewer);
        if (!store.snapshot().canSee(viewer.uuid(), target.uuid(), seeLevel)) {
            return true;
        }
        return network.levelOf(target.uuid())
                .filter(level -> !VanishLevels.sees(seeLevel, level))
                .isPresent();
    }
}
