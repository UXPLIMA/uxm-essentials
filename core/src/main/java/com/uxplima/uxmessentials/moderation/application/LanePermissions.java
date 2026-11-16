package com.uxplima.uxmessentials.moderation.application;

import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;

import com.uxplima.uxmessentials.shared.application.permission.PermissionScope;
import com.uxplima.uxmessentials.shared.application.permission.PermissionSnapshot;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.jspecify.annotations.Nullable;

/**
 * The permission reader behind the punishment immunity check and the duration cap, which mostly run on the
 * {@link StorageLane}'s worker threads where the live permission plugin cannot be read safely.
 *
 * <p>An answer the lane read before the work started wins. On the server thread, with nothing captured, the live
 * reader answers when it can; on Folia that is only on the region thread that owns the player. Off the server thread,
 * or on another region's thread, with nothing captured there is no safe answer, so this one refuses: the target counts
 * as immune and the cap as zero, and the miss is logged so it can be found.
 * Every node asked through here is an immunity node and every quota a duration cap, which is why "held" and "zero"
 * are the refusing answers.
 *
 * <p>A system actor (the console, a warn escalation) is never a connected player, so it answers as an offline
 * player does without reading anything: no node held, the quota at its default.
 */
public final class LanePermissions implements Permissions {

    private final Permissions live;
    private final BooleanSupplier onServerThread;
    private final Logger log;

    public LanePermissions(Permissions live, BooleanSupplier onServerThread, Logger log) {
        this.live = Objects.requireNonNull(live, "live");
        this.onServerThread = Objects.requireNonNull(onServerThread, "onServerThread");
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public boolean has(PlayerRef who, String node) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(node, "node");
        if (who.isSystem()) {
            return false;
        }
        Optional<Boolean> kept = PermissionScope.current().flatMap(snapshot -> snapshot.has(who, node));
        if (kept.isPresent()) {
            return kept.get();
        }
        if (onServerThread.getAsBoolean()) {
            // On Folia this thread may belong to another region than the player's, where their permissions cannot be
            // read; an immunity that cannot be read is held.
            Optional<Boolean> read = live.read(who, node);
            if (read.isPresent()) {
                return read.get();
            }
        }
        log.warn("event=moderation_permission_unread player={} node={} answer=held", who.name(), node);
        return true;
    }

    @Override
    public QuotaResult resolveQuota(PlayerRef who, QuotaFamily family, @Nullable WorldRef world, long configDefault) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(family, "family");
        if (who.isSystem()) {
            return configDefault == -1 ? QuotaResult.unlimited() : QuotaResult.limited(configDefault);
        }
        Optional<QuotaResult> kept =
                PermissionScope.current().flatMap(snapshot -> snapshot.quota(who, family, world, configDefault));
        if (kept.isPresent()) {
            return kept.get();
        }
        if (onServerThread.getAsBoolean()) {
            return live.resolveQuota(who, family, world, configDefault);
        }
        log.warn("event=moderation_permission_unread player={} node={} answer=0", who.name(), family.node());
        return QuotaResult.limited(0);
    }

    /**
     * The answers a sanction by {@code actor} will ask about the actor, read now from the live reader: how long they
     * may ban or mute for. Call it on the actor's thread. The target's immunity is read on the target's own thread
     * ({@link TargetExemption}) and added to the snapshot once it is known.
     */
    static PermissionSnapshot.Builder capture(Permissions live, PlayerRef actor) {
        PermissionSnapshot.Builder snapshot = PermissionSnapshot.from(live);
        if (!actor.isSystem()) {
            for (String kind : SanctionDurationLimit.KINDS) {
                snapshot.quota(actor, SanctionDurationLimit.family(kind), null, SanctionDurationLimit.NO_NODE_FALLBACK);
            }
        }
        return snapshot;
    }
}
