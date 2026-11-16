package com.uxplima.uxmessentials.worlds.application;

import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.worlds.application.port.WorldClaims;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import com.uxplima.uxmessentials.worlds.domain.WorldsMessageKey;

/**
 * {@code /worlds claim <world>}: an operator copies a world no backend has claimed onto this backend, for a world the
 * start-up claim could not place (this backend had no folder of it at start, or the world arrives later). The claim is a database write, so
 * it runs on the async lane and only the answer comes back to the requester.
 */
public final class ClaimWorld {

    private final WorldClaims claims;
    private final Notifier notifier;
    private final Scheduler scheduler;
    private final Logger log;

    public ClaimWorld(WorldClaims claims, Notifier notifier, Scheduler scheduler, Logger log) {
        this.claims = Objects.requireNonNull(claims, "claims");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.log = Objects.requireNonNull(log, "log");
    }

    public void claim(PlayerRef who, WorldName name) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(name, "name");
        scheduler.async(() -> {
            WorldsMessageKey answer;
            try {
                answer = claims.claim(name) ? WorldsMessageKey.WORLD_CLAIMED : WorldsMessageKey.WORLD_CLAIM_NONE;
            } catch (RuntimeException failed) {
                // The claim failed and the world could not be confirmed as kept here, so whether it was claimed is
                // uncertain; the requester still hears back.
                log.error("event=world_server_claim_failed by=command world=" + name.value(), failed);
                answer = WorldsMessageKey.WORLD_IO_ERROR;
            }
            WorldsMessageKey reply = answer;
            scheduler.onEntity(who, () -> notifier.send(who, reply, Map.of("world", name.value())));
        });
    }
}
