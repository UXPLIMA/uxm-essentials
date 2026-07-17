package com.uxplima.uxmessentials.nametags.adapter.outbound;

import java.util.Objects;

import com.uxplima.uxmessentials.nametags.application.port.NametagVanish;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vanish.application.port.VanishStore;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link NametagVanish} implementation, reading the vanish context's single {@link VanishStore} authority directly.
 * A {@code viewer} may see a {@code wearer}'s nametag when the wearer is not vanished, or the viewer holds the
 * vanish-see node — the pure {@code VanishState#canSee} rule the messaging and staff surfaces read too, so the nametag
 * cull tracks exactly the same visibility every other consumer sees. The renderer builds an eligible-viewer set, so the
 * polarity here is the natural "can this viewer see the wearer?", the inverse of messaging's {@code isHiddenFrom}.
 *
 * <p>Bound only when the vanish module is enabled; with vanish off the wiring binds {@link NametagVanish#ALWAYS_VISIBLE}
 * instead, so the nametag renderer degrades to "everyone can see everyone" rather than failing.
 */
@NullMarked
public final class AuthorityNametagVanish implements NametagVanish {

    private static final String SEE_NODE = "uxmessentials.vanish.see";

    private final VanishStore store;
    private final Permissions permissions;

    public AuthorityNametagVanish(VanishStore store, Permissions permissions) {
        this.store = Objects.requireNonNull(store, "store");
        this.permissions = Objects.requireNonNull(permissions, "permissions");
    }

    @Override
    public boolean canSee(PlayerRef viewer, PlayerRef wearer) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(wearer, "wearer");
        return store.snapshot().canSee(viewer.uuid(), wearer.uuid(), permissions.has(viewer, SEE_NODE));
    }
}
