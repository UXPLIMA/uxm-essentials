package com.uxplima.uxmessentials.presence.application;

import java.util.Objects;

import com.uxplima.uxmessentials.presence.application.port.PresenceStore;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * The read-only visibility query the messaging and teleport contexts consume cross-context: whether a
 * {@code target} is hidden from a {@code viewer} by vanish. A target is hidden when they are vanished and the
 * viewer lacks the vanish-see node ({@code uxmessentials.vanish.see}); a viewer holding that node sees vanished
 * players and so can still message them or accept their {@code /tpa}. The read goes straight to the
 * {@link PresenceStore}'s {@code ConcurrentHashMap}, safe from any thread (docs/02-concurrency.md §6.9).
 *
 * <p>This is the canonical source of the vanish answer. The bukkit-adapter already enforces the same outcome
 * structurally through Bukkit's {@code canSee} graph (which the {@code VisibilityApplier} drives), so messaging
 * and teleport need not call this directly — but it is exposed so the presence context owns the rule and can be
 * queried by a context that prefers an explicit lookup over the {@code canSee} seam. The coupling is soft: when
 * presence is disabled the dependent context simply never reaches this and its own binding degrades to "fully
 * visible".
 */
public final class ResolveVisibility {

    private static final String SEE_NODE = "uxmessentials.vanish.see";

    private final PresenceStore store;
    private final Permissions permissions;

    public ResolveVisibility(PresenceStore store, Permissions permissions) {
        this.store = Objects.requireNonNull(store, "store");
        this.permissions = Objects.requireNonNull(permissions, "permissions");
    }

    /** True when {@code target} is vanished and {@code viewer} lacks the vanish-see node. */
    public boolean isHiddenFrom(PlayerRef viewer, PlayerRef target) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(target, "target");
        if (viewer.equals(target)) {
            return false;
        }
        return store.current(target).vanished() && !permissions.has(viewer, SEE_NODE);
    }
}
