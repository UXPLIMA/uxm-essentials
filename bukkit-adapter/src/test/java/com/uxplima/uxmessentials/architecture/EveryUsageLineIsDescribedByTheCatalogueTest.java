package com.uxplima.uxmessentials.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A usage line is written in one place, and that place reads the command's description from the catalogue.
 *
 * <p>Eight command families each wrote their own, with the description in English in the code: a player reading
 * another language read English, and the line missed the lettering the catalogue gives every other line.
 */
final class EveryUsageLineIsDescribedByTheCatalogueTest {

    /** The shared key alone: a module's own usage key, MENU_COMMAND_USAGE, is a different line. */
    private static final Pattern SHARED_USAGE_KEY = Pattern.compile("\\bCOMMAND_USAGE\\b");

    @Test
    @DisplayName("only CommandUsage sends the usage line")
    void onlyCommandUsageSendsTheUsageLine() {
        List<String> found = new ArrayList<>();
        for (Path file : ProductionSources.files()) {
            if (!file.toString().contains("bukkit-adapter") || file.endsWith("CommandUsage.java")) {
                continue;
            }
            String code = ProductionSources.code(ProductionSources.read(file));
            Matcher matcher = SHARED_USAGE_KEY.matcher(code);
            if (matcher.find()) {
                found.add(file.getFileName() + ":" + ProductionSources.lineOf(code, matcher.start()));
            }
        }
        assertThat(found)
                .describedAs("send the usage line through CommandUsage.send")
                .isEmpty();
    }
}
