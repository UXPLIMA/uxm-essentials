package com.uxplima.uxmessentials.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

/**
 * Pins the three-way rule behind the config upgrade: a key is appended to the operator's file only when this
 * version's bundled default has it, the previously shipped default did not, and the operator's file does not.
 * The baseline is what separates "a setting the update added" from "a setting the operator deleted on purpose",
 * so both cases are covered here.
 */
class BundledDefaultsMergeTest {

    @Test
    void appendsASettingTheUpdateAdded() throws Exception {
        Optional<String> added = BundledDefaultsMerge.newSettings("a = 1\nb = 2\n", "a = 1\n", "a = 9\n");

        assertThat(added).isPresent();
        assertThat(added.get()).contains("b").doesNotContain("a");
    }

    @Test
    void leavesASettingTheOperatorDeletedDeleted() throws Exception {
        // b was in the default the operator installed from, so its absence is their edit, not our new key.
        Optional<String> added = BundledDefaultsMerge.newSettings("a = 1\nb = 2\n", "a = 1\nb = 2\n", "a = 9\n");

        assertThat(added).isEmpty();
    }

    @Test
    void appendsANewKeyInsideABlockTheOperatorAlreadyHas() throws Exception {
        Optional<String> added = BundledDefaultsMerge.newSettings(
                "smelt {\n  p = 1\n  q = 2\n}\n", "smelt {\n  p = 1\n}\n", "smelt {\n  p = 9\n}\n");

        assertThat(added).isPresent();
        assertThat(added.get()).contains("smelt").contains("q").doesNotContain("p");
    }

    @Test
    void leavesABlockTheOperatorDeletedDeleted() throws Exception {
        Optional<String> added = BundledDefaultsMerge.newSettings(
                "smelt {\n  p = 1\n  q = 2\n}\n", "smelt {\n  p = 1\n}\n", "other = 1\n");

        assertThat(added).isEmpty();
    }

    @Test
    void carriesTheCommentThatExplainsTheNewSetting() throws Exception {
        Optional<String> added =
                BundledDefaultsMerge.newSettings("# What this knob does.\nnew-knob = false\n", "", "old = 1\n");

        assertThat(added).isPresent();
        assertThat(added.get()).contains("What this knob does.");
    }

    @Test
    void aFileWithNothingNewIsLeftAlone() throws Exception {
        assertThat(BundledDefaultsMerge.newSettings("a = 1\n", "a = 1\n", "a = 9\n"))
                .isEmpty();
    }

    @Test
    void doesNotOverwriteAScalarTheOperatorTurnedIntoSomethingElse() throws Exception {
        // Their file says "block = 5" where we now ship an object. Appending "block { q = 2 }" would replace
        // their scalar wholesale, so the odd file is left exactly as it is.
        Optional<String> added = BundledDefaultsMerge.newSettings("block {\n  q = 2\n}\n", "", "block = 5\n");

        assertThat(added).isEmpty();
    }

    @Test
    void theAppendedBlockMergesWithTheOperatorFileRatherThanReplacingIt() throws Exception {
        String operator = "smelt {\n  p = 9\n}\ntop = 3\n";
        String bundled = "smelt {\n  p = 1\n  q = 2\n}\ntop = 1\nfresh = true\n";
        String baseline = "smelt {\n  p = 1\n}\ntop = 1\n";

        String upgraded = operator + "\n"
                + BundledDefaultsMerge.newSettings(bundled, baseline, operator).orElseThrow();
        CommentedConfigurationNode root = HoconConfigurationLoader.builder()
                .source(() -> new BufferedReader(new StringReader(upgraded)))
                .build()
                .load();

        // The operator's own values survive the appended block; only the genuinely new keys arrive.
        assertThat(root.node("smelt", "p").getInt(-1)).isEqualTo(9);
        assertThat(root.node("smelt", "q").getInt(-1)).isEqualTo(2);
        assertThat(root.node("top").getInt(-1)).isEqualTo(3);
        assertThat(root.node("fresh").getBoolean(false)).isTrue();
    }

    @Test
    void aMalformedOperatorFileIsReportedRatherThanRewritten() {
        assertThat(org.assertj.core.api.Assertions.catchThrowable(
                        () -> BundledDefaultsMerge.newSettings("a = 1\n", "", "a = {{{\n")))
                .isInstanceOf(ConfigurateException.class);
    }
}
