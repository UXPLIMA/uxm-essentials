package com.uxplima.uxmessentials.guard.domain;

import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * Why a check came out the way it did, step by step, for {@code /guard why}, {@code /guard test} and the inspect
 * mode. Built only on request: the hot path never allocates one.
 *
 * @param verdict the outcome
 * @param steps each region looked at, in order
 * @param lockedWorld true when the world's regions could not be loaded and everything is protected
 */
public record Explanation(Verdict verdict, List<Step> steps, boolean lockedWorld) {

    public Explanation {
        Objects.requireNonNull(verdict, "verdict");
        steps = List.copyOf(Objects.requireNonNull(steps, "steps"));
    }

    /**
     * One region's part in the decision.
     *
     * @param region the region
     * @param priority its priority
     * @param role the subject's role in it, or null
     * @param node the action node that decided inside it, or null when it said nothing
     * @param rule the deciding rule, or null
     * @param result what this region said
     * @param decisive true for the region whose answer stood
     * @param origin where the rule came from: the region, its parent, its template or the world defaults
     */
    public record Step(
            RegionKey region,
            int priority,
            @Nullable RoleName role,
            @Nullable ActionId node,
            @Nullable Rule rule,
            Verdict result,
            boolean decisive,
            String origin) {

        public Step {
            Objects.requireNonNull(region, "region");
            Objects.requireNonNull(result, "result");
            Objects.requireNonNull(origin, "origin");
        }
    }
}
