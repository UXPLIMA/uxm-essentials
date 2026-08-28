package com.uxplima.uxmessentials.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * The legacy-chat guard (CLAUDE.md §3 "ChatColor and § colour codes are forbidden", docs/03-paper-api.md,
 * docs/14-ui-style.md).
 *
 * <p><strong>The bug this freezes out.</strong> A legacy colour code is a dead end in three directions at once. It
 * cannot express what the palette needs (no hex, no gradients, no hover or click events), it cannot be translated
 * because the code sits inside the string a translator is handed, and it defeats the design system: a `§a` written
 * into one message is a colour nobody can restyle from `docs/14-ui-style.md` afterwards. Mixed with MiniMessage it
 * is worse than either alone, because the serializer that finally renders the component decides which half wins.
 *
 * <p><strong>The invariant.</strong> Every user-visible string is a `MessageKey` resolved through the locale
 * catalog and rendered as an Adventure `Component` from MiniMessage. `ChatColor` appears nowhere.
 *
 * <p><strong>The exception, and why it is one.</strong> Two classes read legacy codes rather than write them:
 * importing a foreign menu file that predates us, and stripping codes out of player input. Recognising a legacy
 * code is the opposite of emitting one, so those two are listed here by name with the reason, which is a cheaper
 * and more honest fence than a rule that cannot tell reading from writing.
 */
class LegacyChatApiDriftTest {

    private static final Pattern CHAT_COLOR = Pattern.compile("\\bChatColor\\b");

    private static final Pattern SECTION_SIGN = Pattern.compile("§");

    /** Classes that detect or strip legacy codes rather than emit them. */
    private static final List<String> READS_LEGACY_INPUT = List.of(
            // Warns the operator that an imported GUIPlus menu still carries '&' / '§' colour codes.
            "custommenus/adapter/convert/GuiPlusConverter.java",
            // Strips a '§' a player typed, so it can never reach a component as a colour.
            "messaging/adapter/inbound/command/MessagingCommandSupport.java");

    @Test
    void noProductionCodeNamesTheLegacyColourEnum() {
        List<String> offenders = scan(CHAT_COLOR, false);
        assertThat(offenders)
                .as("user-visible text is a MessageKey rendered through MiniMessage; ChatColor cannot express the"
                        + " palette and cannot be translated")
                .isEmpty();
    }

    @Test
    void noProductionCodeEmitsASectionColourCode() {
        List<String> offenders = scan(SECTION_SIGN, true);
        assertThat(offenders)
                .as("a '§' inside a string literal is a colour no locale catalog can translate and no style token"
                        + " can restyle. The two classes that *read* legacy codes are listed in this test by name.")
                .isEmpty();
    }

    private static List<String> scan(Pattern pattern, boolean allowLegacyReaders) {
        List<String> offenders = new ArrayList<>();
        for (Path file : ProductionSources.files()) {
            String relative = ProductionSources.repoRoot().relativize(file).toString();
            if (allowLegacyReaders && READS_LEGACY_INPUT.stream().anyMatch(relative::endsWith)) {
                continue;
            }
            String code = ProductionSources.code(ProductionSources.read(file));
            Matcher matcher = pattern.matcher(code);
            while (matcher.find()) {
                offenders.add(relative + ":" + ProductionSources.lineOf(code, matcher.start()));
            }
        }
        return offenders;
    }
}
