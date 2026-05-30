package com.uxplima.uxmessentials.shared.adapter.outbound.permission;

import static org.assertj.core.api.Assertions.assertThat;

import com.uxplima.uxmessentials.shared.application.port.Permissions.QuotaFamily;
import com.uxplima.uxmessentials.shared.application.port.Permissions.QuotaResult;
import org.junit.jupiter.api.Test;

/**
 * The uniform numbered/world quota reducer (docs/permissions.md "Uniform numeric reducer"): quotas keep
 * the maximum matching node, cooldown/warmup thresholds keep the minimum, the {@code -1} sentinel
 * short-circuits a quota to unlimited, the optional {@code <world>} segment folds in alongside the
 * unscoped form, and a player with no matching node falls back to the seeded config default. This is the
 * pure numeric core the teleport (and every other) context's cooldown/warmup tiers resolve through.
 */
class QuotaNodeReducerTest {

    private static final QuotaFamily WARMUP = QuotaFamily.threshold("uxmessentials.tp.warmup");
    private static final QuotaFamily HOME_LIMIT = QuotaFamily.quota("uxmessentials.home.limit");

    @Test
    void cooldownAndWarmupThresholdsKeepTheSmallestNode() {
        QuotaNodeReducer reducer = new QuotaNodeReducer(WARMUP, null);
        reducer.seedDefault(3);
        reducer.offerNode("uxmessentials.tp.warmup.5");
        reducer.offerNode("uxmessentials.tp.warmup.1");

        assertThat(reducer.result().orElse(99)).isEqualTo(1L); // lowest tier wins — the most privileged
    }

    @Test
    void warmupZeroRemovesTheWarmup() {
        QuotaNodeReducer reducer = new QuotaNodeReducer(WARMUP, null);
        reducer.seedDefault(3);
        reducer.offerNode("uxmessentials.tp.warmup.0");

        assertThat(reducer.result().orElse(99)).isZero();
    }

    @Test
    void quotaFamiliesKeepTheLargestNode() {
        QuotaNodeReducer reducer = new QuotaNodeReducer(HOME_LIMIT, null);
        reducer.seedDefault(1);
        reducer.offerNode("uxmessentials.home.limit.5");
        reducer.offerNode("uxmessentials.home.limit.10");

        assertThat(reducer.result().orElse(0)).isEqualTo(10L); // more is better for a quota
    }

    @Test
    void minusOneShortCircuitsAQuotaToUnlimited() {
        QuotaNodeReducer reducer = new QuotaNodeReducer(HOME_LIMIT, null);
        reducer.seedDefault(3);
        reducer.offerNode("uxmessentials.home.limit.-1");
        reducer.offerNode("uxmessentials.home.limit.5");

        QuotaResult result = reducer.result();
        assertThat(result.isUnlimited()).isTrue();
        assertThat(result.orElse(7)).isEqualTo(7L); // unlimited yields the caller's fallback in arithmetic
    }

    @Test
    void theWorldScopedFormFoldsInAlongsideTheUnscopedForm() {
        QuotaNodeReducer reducer = new QuotaNodeReducer(WARMUP, "nether");
        reducer.seedDefault(3);
        reducer.offerNode("uxmessentials.tp.warmup.4"); // unscoped
        reducer.offerNode("uxmessentials.tp.warmup.nether.0"); // world-scoped, more privileged

        assertThat(reducer.result().orElse(99)).isZero();
    }

    @Test
    void aNonMatchingWorldNodeIsIgnored() {
        QuotaNodeReducer reducer = new QuotaNodeReducer(WARMUP, "nether");
        reducer.seedDefault(3);
        reducer.offerNode("uxmessentials.tp.warmup.creative.0"); // different world — not folded

        assertThat(reducer.result().orElse(99)).isEqualTo(3L); // only the default remains
    }

    @Test
    void noMatchingNodeFallsBackToTheSeededDefault() {
        QuotaNodeReducer reducer = new QuotaNodeReducer(WARMUP, null);
        reducer.seedDefault(3);
        reducer.offerNode("uxmessentials.tp.warmup.bypass"); // non-numeric tail, ignored
        reducer.offerNode("uxmessentials.other.node.1"); // wrong family, ignored

        assertThat(reducer.result().orElse(99)).isEqualTo(3L);
    }

    @Test
    void luckPermsMetaFoldsInLikeANode() {
        QuotaNodeReducer reducer = new QuotaNodeReducer(WARMUP, null);
        reducer.seedDefault(3);
        reducer.offerMeta(1);

        assertThat(reducer.result().orElse(99)).isEqualTo(1L);
    }
}
