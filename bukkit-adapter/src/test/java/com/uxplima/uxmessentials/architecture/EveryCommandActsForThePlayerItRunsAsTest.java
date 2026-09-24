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
 * A command handler reads the player it acts for, never the sender alone.
 *
 * <p>{@code execute as <player> run warp spawn} keeps the console as the sender and makes the player the executor.
 * Operators wire an NPC click or another plugin's button that way. A handler that read the sender answered the
 * console and did nothing for the player: a bare command opened no menu and a player-only one refused. uxm-plots
 * found it with {@code /plot shop}. {@code Sender.audience} answers with the executor when it is a player, else the
 * sender. A {@code requires} gate reads the source it is given, which is the sender, as vanilla does.
 *
 * <p>The server module only. The REST add-on's token command hands a secret to whoever issued it, and that is the
 * sender however the command was run: it must never be read out to the player it runs as.
 */
final class EveryCommandActsForThePlayerItRunsAsTest {

    private static final Pattern SENDER_OF_CONTEXT =
            Pattern.compile("\\b\\w+\\s*\\.\\s*getSource\\(\\)\\s*\\.\\s*getSender\\(\\)");

    @Test
    @DisplayName("no handler reads a command context's sender instead of the player it runs as")
    void noHandlerReadsTheSenderAlone() {
        List<String> found = new ArrayList<>();
        for (Path file : ProductionSources.files()) {
            if (!file.toString().contains("bukkit-adapter")) {
                continue;
            }
            String code = ProductionSources.code(ProductionSources.read(file));
            Matcher matcher = SENDER_OF_CONTEXT.matcher(code);
            while (matcher.find()) {
                found.add(file.getFileName() + ":" + ProductionSources.lineOf(code, matcher.start()));
            }
        }
        assertThat(found)
                .describedAs("read these through com.uxplima.uxmlib.command.Sender.audience(ctx.getSource())")
                .isEmpty();
    }
}
