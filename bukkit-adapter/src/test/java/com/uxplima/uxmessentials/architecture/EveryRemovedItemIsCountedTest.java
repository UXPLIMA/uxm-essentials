package com.uxplima.uxmessentials.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Nothing calls {@code removeItem} and walks away from its answer.
 *
 * <p>{@code Inventory.removeItem} takes only stacks similar to the one it is given and returns what it could not find.
 * A caller that discards that return has taken nothing from a player holding a renamed or worn item and goes on as
 * though it had: a payment taken for nothing. uxm-plots found the same shape in its item
 * currency on 2026-09-23, and uxmAuction, uxmShop, uxmSkills and uxmEssentials' {@code /sell} had it too.
 */
final class EveryRemovedItemIsCountedTest {

    private static final Path MAIN = Path.of("src", "main", "java");

    /** A comment, so a sentence that names the call is not read as one. */
    private static final Pattern COMMENT = Pattern.compile("//[^\\n]*|/\\*.*?\\*/", Pattern.DOTALL);

    /** What ends one statement and starts the next. */
    private static final Pattern BOUNDARY = Pattern.compile("[;{}]");

    /**
     * A statement that is a chain ending in the call and nothing else, so its answer goes nowhere. The chain may be
     * split over lines at its dots, and an assignment or a {@code return} in front of it is a read.
     */
    private static final Pattern DISCARDED =
            Pattern.compile("^\\s*[\\w()\\[\\]]+(?:\\s*\\.\\s*[\\w()\\[\\]]+)*\\s*\\.\\s*removeItem\\s*\\(");

    /**
     * Files whose {@code removeItem} is not an inventory's, each with why. The grid editor calls
     * {@code MenuEditSession.removeItem(String)}, which drops a slot from the menu being edited and answers the
     * session itself.
     */
    private static final Set<String> NOT_AN_INVENTORY = Set.of("MenuGridView.java");

    @Test
    @DisplayName("every removeItem's answer is read")
    void everyAnswerIsRead() throws IOException {
        List<String> discarded = new ArrayList<>();
        for (Path file : sources()) {
            if (!NOT_AN_INVENTORY.contains(file.getFileName().toString()) && discards(Files.readString(file))) {
                discarded.add(file.getFileName().toString());
            }
        }

        assertThat(discarded)
                .describedAs("these take items and never ask whether they were there")
                .isEmpty();
    }

    @Test
    @DisplayName("the pattern finds a discarded call and passes a read one")
    void thePatternReadsRightly() {
        assertThat(discards("        player.getInventory().removeItem(new ItemStack(coal, 1));"))
                .isTrue();
        assertThat(discards("        player.getInventory()\n                .removeItem(new ItemStack(coal, 1));"))
                .isTrue();
        assertThat(discards("        // why\n        inventory.removeItem(stack);"))
                .isTrue();
        assertThat(discards("        var missing = inventory.removeItem(stack.clone());"))
                .isFalse();
        assertThat(discards("        Map<Integer, ItemStack> missing =\n                inventory.removeItem(stack);"))
                .isFalse();
        assertThat(discards("        return inventory.removeItem(stack).isEmpty();"))
                .isFalse();
        assertThat(discards("        // inventory.removeItem(stack);")).isFalse();
    }

    private static boolean discards(String source) {
        String code = COMMENT.matcher(source).replaceAll(" ");
        return BOUNDARY.splitAsStream(code)
                .anyMatch(statement -> DISCARDED.matcher(statement).find());
    }

    private static List<Path> sources() throws IOException {
        try (Stream<Path> files = Files.walk(MAIN)) {
            return files.filter(path -> path.toString().endsWith(".java")).toList();
        }
    }
}
