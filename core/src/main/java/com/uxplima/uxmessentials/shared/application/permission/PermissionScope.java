package com.uxplima.uxmessentials.shared.application.permission;

import java.util.Objects;
import java.util.Optional;

/**
 * The permission answers a piece of work carries onto a worker thread.
 *
 * <p>A permission plugin keeps its answers on the server's own threads, and reading them from a worker races the
 * plugin while it recalculates. Work that leaves the server thread and still has to ask a permission question
 * therefore asks it before it leaves, keeps the answers in a {@link PermissionSnapshot}, and binds that snapshot
 * here for as long as it runs, so the permission reader answers from the snapshot instead of the live player.
 *
 * <p>A plain {@link ThreadLocal}, as {@code LocaleScope} uses for the request locale: {@link #runWith} restores the
 * previous binding when the body returns, so nothing leaks from one task to the next on a pooled thread.
 */
public final class PermissionScope {

    private static final ThreadLocal<PermissionSnapshot> CURRENT = new ThreadLocal<>();

    private PermissionScope() {}

    /** Run {@code body} with {@code snapshot} bound, restoring whatever was bound before once it returns. */
    public static void runWith(PermissionSnapshot snapshot, Runnable body) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(body, "body");
        PermissionSnapshot previous = CURRENT.get();
        CURRENT.set(snapshot);
        try {
            body.run();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    /** The snapshot bound on this thread, if any. */
    public static Optional<PermissionSnapshot> current() {
        return Optional.ofNullable(CURRENT.get());
    }
}
