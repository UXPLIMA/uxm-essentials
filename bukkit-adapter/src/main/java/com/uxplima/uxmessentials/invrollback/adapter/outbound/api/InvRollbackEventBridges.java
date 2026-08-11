package com.uxplima.uxmessentials.invrollback.adapter.outbound.api;

import java.util.Objects;

import com.uxplima.uxmessentials.api.bukkit.event.invrollback.UxmInventoryRestoreEvent;
import com.uxplima.uxmessentials.api.view.UxmSnapshotCause;
import com.uxplima.uxmessentials.invrollback.domain.SnapshotCause;
import com.uxplima.uxmessentials.invrollback.domain.event.SnapshotRestored;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.EventBridgeRegistry;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.Region;
import org.jspecify.annotations.NullMarked;

/** Which Bukkit event the restore fact becomes: the player's own, because they are holding the result of it. */
@NullMarked
public final class InvRollbackEventBridges {

    private InvRollbackEventBridges() {}

    public static void register(EventBridgeRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        registry.register(
                SnapshotRestored.class,
                UxmInventoryRestoreEvent.getHandlerList(),
                fact -> new UxmInventoryRestoreEvent(
                        fact.target().uuid(),
                        fact.target().name(),
                        fact.snapshot().value(),
                        cause(fact.cause()),
                        fact.takenAt()),
                fact -> Region.entity(fact.target()));
    }

    /** The domain cause as the published one; the two sets are the same three and are kept in step by this switch. */
    static UxmSnapshotCause cause(SnapshotCause cause) {
        return switch (cause) {
            case DEATH -> UxmSnapshotCause.DEATH;
            case LOGOUT -> UxmSnapshotCause.LOGOUT;
            case RESTORE -> UxmSnapshotCause.RESTORE;
        };
    }
}
