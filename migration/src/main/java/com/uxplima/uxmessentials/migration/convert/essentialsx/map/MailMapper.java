package com.uxplima.uxmessentials.migration.convert.essentialsx.map;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.uxplima.uxmessentials.migration.convert.essentialsx.parse.EssXMail;
import com.uxplima.uxmessentials.migration.convert.map.ImportedMail;
import org.jspecify.annotations.NullMarked;

/**
 * Turns an EssentialsX mail entry into an {@link ImportedMail}. EssentialsX stores a colour a player was allowed
 * to use as section-sign codes, which nothing here renders, so they are removed rather than shown as stray
 * characters. A legacy entry has no sender of its own, but old EssentialsX wrote the sender into the text as
 * {@code [name] message}; that name is recovered when it is there, and otherwise the mail is attributed to
 * EssentialsX itself.
 */
@NullMarked
final class MailMapper {

    /** The sender of a legacy mail whose text names nobody: the plugin it came from, a proper name in any language. */
    static final String UNKNOWN_SENDER = "EssentialsX";

    private static final char SECTION = '§';

    /** Old EssentialsX legacy format after its colour codes are gone: {@code [name] message}. */
    private static final Pattern LEGACY_SENDER = Pattern.compile("^\\[([A-Za-z0-9_.]{1,16})]\\s*(.+)$", Pattern.DOTALL);

    Optional<ImportedMail> map(EssXMail src) {
        String text = stripColours(src.message()).strip();
        Optional<String> senderName = src.senderName()
                .map(MailMapper::stripColours)
                .map(String::strip)
                .filter(name -> !name.isEmpty());
        if (senderName.isEmpty()) {
            Matcher legacy = LEGACY_SENDER.matcher(text);
            if (legacy.matches()) {
                senderName = Optional.of(legacy.group(1));
                text = legacy.group(2).strip();
            }
        }
        if (text.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new ImportedMail(
                src.senderId(), senderName.orElse(UNKNOWN_SENDER), text, src.sentAt(), src.expiresAt(), src.read()));
    }

    /** Remove every section-sign code with the character it governs, hex colours included (each digit is a pair). */
    static String stripColours(String raw) {
        StringBuilder plain = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char character = raw.charAt(i);
            if (character == SECTION) {
                i++;
                continue;
            }
            plain.append(character);
        }
        return plain.toString();
    }
}
