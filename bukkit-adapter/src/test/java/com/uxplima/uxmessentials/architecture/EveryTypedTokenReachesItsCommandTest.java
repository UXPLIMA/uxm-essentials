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
 * No command argument is Brigadier's {@code word()}, and a link is not a quoted string.
 *
 * <p>{@code word()} stops at any character outside {@code 0-9 A-Z a-z _ - . +}, and it stops while parsing, before
 * the command can say why. uxm-plots found its own ids, written {@code creative:-1;0}, refused that way. Here it
 * refused {@code /give minecraft:diamond}, a firework colour {@code #ff0000}, a world generator {@code Plugin:id},
 * an IPv6 address to unban, and every Turkish letter. {@code Args.token()} reads up to the next space instead, and the
 * client is still told the argument is a word. A quoted {@code string()} stays where a name may hold spaces, but a
 * link unquoted is read by the same word rule, so {@code https://} never parsed.
 */
final class EveryTypedTokenReachesItsCommandTest {

    private static final Pattern WORD = Pattern.compile("StringArgumentType\\s*\\.\\s*word\\s*\\(\\s*\\)");
    private static final Pattern QUOTED_LINK = Pattern.compile(
            "argument\\(\\s*\"(link|url)\"\\s*,\\s*(?:[\\w.]+\\.)?StringArgumentType\\s*\\.\\s*string\\s*\\(");

    @Test
    @DisplayName("no argument is a word() that refuses a colon, a hash or a Turkish letter")
    void noArgumentIsAWord() {
        List<String> found = new ArrayList<>();
        for (Path file : ProductionSources.files()) {
            String code = ProductionSources.code(ProductionSources.read(file));
            find(WORD, code, file, found);
            find(QUOTED_LINK, code, file, found);
        }
        assertThat(found).describedAs("read these with Args.token()").isEmpty();
    }

    private static void find(Pattern pattern, String code, Path file, List<String> found) {
        Matcher matcher = pattern.matcher(code);
        while (matcher.find()) {
            if (code.contains("implements CustomArgumentType")
                    && code.substring(Math.max(0, matcher.start() - 7), matcher.start())
                            .equals("return ")) {
                continue; // a token type telling the client it is a word, which is what the client should draw
            }
            found.add(file.getFileName() + ":" + ProductionSources.lineOf(code, matcher.start()));
        }
    }
}
