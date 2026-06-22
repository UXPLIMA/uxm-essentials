package com.uxplima.uxmessentials.shared.display;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * The editor's world/permission targets compose into and read back out of the raw display-condition string. These
 * pin the round-trip the {@code /announce} editor depends on: reading the current target, setting one without
 * dropping the other, clearing a target, and an empty pair yielding the unconditional blank.
 */
class ConditionTargetsTest {

    @Test
    void readsTheWorldAndPermissionAtomsFromACombinedCondition() {
        String condition = "permission:uxmessentials.vip && world:hub";

        assertThat(ConditionTargets.permission(condition)).contains("uxmessentials.vip");
        assertThat(ConditionTargets.world(condition)).contains("hub");
    }

    @Test
    void aBlankConditionHasNoTargets() {
        assertThat(ConditionTargets.world("")).isEmpty();
        assertThat(ConditionTargets.permission("")).isEmpty();
    }

    @Test
    void settingTheWorldOnABlankConditionProducesAWorldAtom() {
        assertThat(ConditionTargets.withWorld("", "nether")).isEqualTo("world:nether");
    }

    @Test
    void settingTheWorldPreservesAnExistingPermission() {
        String composed = ConditionTargets.withWorld("permission:vip", "hub");

        assertThat(composed).isEqualTo("permission:vip && world:hub");
        assertThat(ConditionTargets.permission(composed)).contains("vip");
        assertThat(ConditionTargets.world(composed)).contains("hub");
    }

    @Test
    void settingThePermissionPreservesAnExistingWorld() {
        String composed = ConditionTargets.withPermission("world:hub", "uxmessentials.staff");

        assertThat(composed).isEqualTo("permission:uxmessentials.staff && world:hub");
    }

    @Test
    void clearingTheWorldLeavesOnlyThePermission() {
        assertThat(ConditionTargets.withWorld("permission:vip && world:hub", ""))
                .isEqualTo("permission:vip");
    }

    @Test
    void clearingBothTargetsYieldsTheUnconditionalBlank() {
        String onlyWorld = ConditionTargets.withPermission("world:hub", "");
        assertThat(ConditionTargets.withWorld(onlyWorld, "")).isEmpty();
    }

    @Test
    void replacingAWorldOverwritesRatherThanAppends() {
        String composed = ConditionTargets.withWorld("world:hub", "nether");

        assertThat(composed).isEqualTo("world:nether");
        assertThat(ConditionTargets.world(composed)).contains("nether");
    }
}
