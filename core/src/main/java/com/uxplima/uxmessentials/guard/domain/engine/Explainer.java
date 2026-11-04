package com.uxplima.uxmessentials.guard.domain.engine;

import com.uxplima.uxmessentials.guard.domain.ActionId;
import com.uxplima.uxmessentials.guard.domain.ActionTree;
import com.uxplima.uxmessentials.guard.domain.Explanation;
import com.uxplima.uxmessentials.guard.domain.GuardSubject;
import com.uxplima.uxmessentials.guard.domain.Verdict;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the step by step account of a decision. It runs the same order as the hot path but records every
 * region it asks, which the hot path never does.
 */
public final class Explainer {

    private Explainer() {}

    /** Explain {@code subject} doing {@code action} at a block of {@code index}'s world. */
    public static Explanation explain(
            ActionTree tree, WorldIndex index, int x, int y, int z, ActionId action, GuardSubject subject) {
        int node = tree.indexOf(action);
        if (index.locked()) {
            return new Explanation(Verdict.DENY, List.of(), true);
        }
        RegionSet set = index.at(x, y, z);
        List<Explanation.Step> steps = new ArrayList<>();
        Verdict outcome = Verdict.UNSET;
        int start = 0;
        int decidingGroupStart = -1;
        int decidingGroupEnd = -1;
        for (int end : set.groupEnds()) {
            boolean allow = false;
            boolean deny = false;
            for (int i = start; i < end; i++) {
                CompiledRegion region = set.at(i);
                CompiledRegion.Decision decision = node < 0 ? CompiledRegion.Decision.NONE
                        : region.explain(node, subject);
                deny |= decision.verdict() == Verdict.DENY;
                allow |= decision.verdict() == Verdict.ALLOW;
            }
            if (outcome == Verdict.UNSET && (deny || allow)) {
                outcome = deny ? Verdict.DENY : Verdict.ALLOW;
                decidingGroupStart = start;
                decidingGroupEnd = end;
            }
            start = end;
        }
        for (int i = 0; i < set.size(); i++) {
            CompiledRegion region = set.at(i);
            CompiledRegion.Decision decision = node < 0 ? CompiledRegion.Decision.NONE : region.explain(node, subject);
            boolean inDecidingGroup = i >= decidingGroupStart && i < decidingGroupEnd;
            boolean decisive = inDecidingGroup && decision.verdict() == outcome;
            steps.add(new Explanation.Step(
                    region.region().key(),
                    region.region().priority(),
                    region.role(subject),
                    decision.node(),
                    decision.rule(),
                    decision.verdict(),
                    decisive,
                    origin(region, decision)));
        }
        return new Explanation(outcome, steps, false);
    }

    private static String origin(CompiledRegion region, CompiledRegion.Decision decision) {
        if (region.worldDefaults()) {
            return "world-defaults";
        }
        if (decision.rule() == null) {
            return "none";
        }
        return region.region().rules().contains(decision.rule()) ? "region" : "inherited";
    }
}
