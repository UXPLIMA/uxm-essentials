package com.uxplima.uxmessentials.moderation.adapter.outbound;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.moderation.application.ModerationGuard;
import com.uxplima.uxmessentials.moderation.application.port.ExemptionSightings;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.application.port.StoredPermissions;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The punishment exemption as the backends recorded it: {@link Answer#HELD} when any backend last saw the player hold
 * it, {@link Answer#NOT_HELD} when backends recorded the player and none saw a grant, {@link Answer#NO_SOURCE} when
 * no backend recorded them. Any other node is not recorded and grants nothing here. A read that fails is
 * {@link Answer#NO_ANSWER}.
 */
@NullMarked
public final class RecordedExemptions implements StoredPermissions {

    private final ExemptionSightings sightings;
    private final Scheduler scheduler;
    private final Logger log;

    public RecordedExemptions(ExemptionSightings sightings, Scheduler scheduler, Logger log) {
        this.sightings = Objects.requireNonNull(sightings, "sightings");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public CompletableFuture<Answer> read(PlayerRef who, String node) {
        Objects.requireNonNull(who, "who");
        if (!ModerationGuard.EXEMPT_NODE.equals(Objects.requireNonNull(node, "node"))) {
            return CompletableFuture.completedFuture(Answer.NOT_HELD);
        }
        CompletableFuture<Answer> answer = new CompletableFuture<>();
        try {
            scheduler.async(() -> {
                try {
                    answer.complete(
                            switch (sightings.sighting(who.uuid())) {
                                case HELD -> Answer.HELD;
                                case NOT_HELD -> Answer.NOT_HELD;
                                case NEVER_SEEN -> Answer.NO_SOURCE;
                            });
                } catch (RuntimeException failure) {
                    log.error("event=moderation_exemption_record_unread player=" + who.name(), failure);
                    answer.complete(Answer.NO_ANSWER);
                }
            });
        } catch (RuntimeException refused) {
            // The scheduler refuses work while the plugin stops.
            answer.complete(Answer.NO_ANSWER);
        }
        return answer;
    }
}
