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
 * An action refused because the player is not online says so in plain words.
 *
 * <p>The message reaches a panel through the REST add-on, where an operator reads it. They read "the items need
 * somewhere to go", "only somebody at a keyboard can be away from it" and "this is state on a live player", and had
 * to guess that each meant the player was offline. Every such message now says who is not online, or that the player
 * left before the action landed.
 */
final class AnOfflineFailureSaysSoPlainlyTest {

    private static final Pattern OFFLINE = Pattern.compile("UxmFailure\\.PLAYER_OFFLINE,\\s*\"([^\"]*)\"");
    private static final Pattern PLAIN =
            Pattern.compile("^(the (player|sender|recipient)|that player) (is not online|left before)");

    @Test
    @DisplayName("every offline refusal says who is not online")
    void everyOfflineRefusalIsPlain() {
        List<String> found = new ArrayList<>();
        List<String> riddles = new ArrayList<>();
        for (Path file : ProductionSources.files()) {
            Matcher matcher = OFFLINE.matcher(ProductionSources.read(file));
            while (matcher.find()) {
                found.add(matcher.group(1));
                if (!PLAIN.matcher(matcher.group(1)).find()) {
                    riddles.add(file.getFileName() + ": " + matcher.group(1));
                }
            }
        }
        assertThat(found).describedAs("the scan reaches the actions").hasSizeGreaterThan(10);
        assertThat(riddles).isEmpty();
    }
}
