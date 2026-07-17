package com.uxplima.uxmessentials.messaging.adapter.outbound;

import java.util.Objects;

import com.uxplima.uxmessentials.messaging.application.port.VanishVisibility;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vanish.application.port.VanishStore;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link VanishVisibility} implementation, reading the vanish context's single {@link VanishStore} authority
 * directly (rather than an indirect {@code canSee} projection). A {@code target} is hidden from a {@code viewer} when
 * the target is vanished in the store and the viewer lacks the vanish-see node — so a sender who cannot see a vanished
 * player resolves them as if offline, while staff holding the node can still message them. The rule itself is the pure
 * {@code VanishState#canSee}; this adapter only supplies the two inputs (the store's vanished flag and the viewer's
 * permission).
 *
 * <p>Bound only when the vanish module is enabled; with vanish off the wiring binds {@link VanishVisibility#ALWAYS_VISIBLE}
 * instead, so messaging degrades to "no one is hidden" rather than failing.
 */
@NullMarked
public final class AuthorityVanishVisibility implements VanishVisibility {

    private static final String SEE_NODE = "uxmessentials.vanish.see";

    private final VanishStore store;
    private final Permissions permissions;

    public AuthorityVanishVisibility(VanishStore store, Permissions permissions) {
        this.store = Objects.requireNonNull(store, "store");
        this.permissions = Objects.requireNonNull(permissions, "permissions");
    }

    @Override
    public boolean isHiddenFrom(PlayerRef viewer, PlayerRef target) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(target, "target");
        return !store.snapshot().canSee(viewer.uuid(), target.uuid(), permissions.has(viewer, SEE_NODE));
    }
}
