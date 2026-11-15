package com.uxplima.uxmessentials.messaging.adapter.outbound;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;

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
 * them, as far as it knows (it is empty with {@code cross-server} off; the target's backend then refuses the message
 * itself through {@link #isHiddenHereFrom}). A target online here is judged by the local store alone: the network view
 * never hears this backend's own reveals, so an entry left in it must not hide a player standing here.
 *
 * <p>Bound only when the vanish module is enabled; with vanish off the wiring binds {@link VanishVisibility#ALWAYS_VISIBLE}
 * instead, so messaging degrades to "no one is hidden" rather than failing.
 */
@NullMarked
public final class AuthorityVanishVisibility implements VanishVisibility {

    private final VanishStore store;
    private final NetworkVanishStore network;
    private final VanishLevelResolver levels;
    private final Predicate<UUID> onlineHere;

    public AuthorityVanishVisibility(
            VanishStore store, NetworkVanishStore network, VanishLevelResolver levels, Predicate<UUID> onlineHere) {
        this.store = Objects.requireNonNull(store, "store");
        this.network = Objects.requireNonNull(network, "network");
        this.levels = Objects.requireNonNull(levels, "levels");
        this.onlineHere = Objects.requireNonNull(onlineHere, "onlineHere");
    }

    @Override
    public boolean isHiddenFrom(PlayerRef viewer, PlayerRef target) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(target, "target");
        if (viewer.equals(target)) {
            return false;
        }
        int seeLevel = levels.seeLevel(viewer);
        if (onlineHere.test(target.uuid())) {
            return !store.snapshot().canSee(viewer.uuid(), target.uuid(), seeLevel);
        }
        return network.levelOf(target.uuid())
                .filter(level -> !VanishLevels.sees(seeLevel, level))
                .isPresent();
    }

    @Override
    public int seeLevel(PlayerRef viewer) {
        return levels.seeLevel(Objects.requireNonNull(viewer, "viewer"));
    }

    @Override
    public boolean isHiddenHereFrom(PlayerRef viewer, int viewerSeeLevel, PlayerRef target) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(target, "target");
        return !store.snapshot().canSee(viewer.uuid(), target.uuid(), viewerSeeLevel);
    }
}
