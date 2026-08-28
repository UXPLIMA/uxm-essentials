package com.uxplima.uxmessentials.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * The justified-suppression guard (CLAUDE.md §3 "no @SuppressWarnings without a one-line comment explaining why").
 *
 * <p><strong>The bug this freezes out.</strong> A suppression is a claim that the compiler is wrong here, and the
 * claim expires. `@SuppressWarnings("deprecation")` over a Paper call is right until the replacement API ships,
 * `@SuppressWarnings("unchecked")` is right until the cast stops being provably sound. Without the reason written
 * down, the next reader cannot tell an expired suppression from a live one, so the safe move is always to leave it,
 * and the annotations accumulate until the warning they hide is a real bug nobody can see.
 *
 * <p><strong>The invariant.</strong> Every `@SuppressWarnings` carries a reason next to it. The comment may sit on
 * the annotation's own line, on the lines just above it (annotations such as `@Override` may come between), or as
 * the first line of the body, which is where a "the cast is sound because" note reads most naturally.
 */
class SuppressWarningsCommentDriftTest {

    /** How far above the annotation a justifying comment may sit, past any intervening annotations. */
    private static final int LOOK_BEHIND = 3;

    /** How far into the body to look for a justification written at the point it applies. */
    private static final int LOOK_AHEAD = 2;

    @Test
    void everySuppressionSaysWhy() {
        List<String> unexplained = new ArrayList<>();
        for (Path file : ProductionSources.files()) {
            List<String> lines = ProductionSources.read(file).lines().toList();
            for (int i = 0; i < lines.size(); i++) {
                if (!lines.get(i).contains("@SuppressWarnings")) {
                    continue;
                }
                if (!explained(lines, i)) {
                    unexplained.add(ProductionSources.repoRoot().relativize(file) + ":" + (i + 1));
                }
            }
        }
        assertThat(unexplained)
                .as("a suppression without its reason cannot be retired: the next reader cannot tell an expired one"
                        + " from a live one, so it stays forever and hides the warning that matters")
                .isEmpty();
    }

    private static boolean explained(List<String> lines, int index) {
        if (isComment(lines.get(index))) {
            return true;
        }
        for (int i = index - 1; i >= 0 && i >= index - LOOK_BEHIND; i--) {
            String line = lines.get(i).strip();
            if (isComment(line)) {
                return true;
            }
            if (!line.startsWith("@") && !line.isEmpty()) {
                break;
            }
        }
        for (int i = index + 1; i < lines.size() && i <= index + LOOK_AHEAD; i++) {
            if (isComment(lines.get(i))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isComment(String line) {
        String stripped = line.strip();
        return stripped.contains("//") || stripped.startsWith("*") || stripped.startsWith("/*");
    }
}
