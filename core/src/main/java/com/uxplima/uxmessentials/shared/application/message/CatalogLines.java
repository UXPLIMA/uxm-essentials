package com.uxplima.uxmessentials.shared.application.message;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Turns a {@link CatalogLine} an operator wrote into the words of the viewer's language, for every surface whose text
 * the operator writes: holograms, menus, the scoreboard, the tablist, nametags, the announcer and the click actions.
 * Each of those surfaces calls this before its placeholders run, so an entry may itself carry {@code %papi%}
 * placeholders and MiniMessage tags and they are read for the same viewer.
 *
 * <p>A reference to a key the catalogue does not declare is left as written. Without that, a line such as
 * {@code @everyone} would turn into the bare key on the day this arrived, and an operator who wrote it meant it.
 */
public final class CatalogLines {

    /** Declares nothing, so its catalogue is never asked. */
    private static final CatalogLines NONE = new CatalogLines((viewer, key, arguments) -> key.key(), key -> false);

    private final Messages messages;
    private final Predicate<String> declared;

    /**
     * @param messages the catalogue, read in the viewer's language
     * @param declared whether a key names an entry the catalogue holds in some language
     */
    public CatalogLines(Messages messages, Predicate<String> declared) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.declared = Objects.requireNonNull(declared, "declared");
    }

    /** Lines left exactly as written, for a surface that runs before the catalogue exists. */
    public static CatalogLines none() {
        return NONE;
    }

    /** Whether {@code line} names a declared catalogue entry, without resolving it. */
    public boolean isCatalogLine(String line) {
        return reference(line).isPresent();
    }

    /** {@code line} in {@code viewer}'s language when it names a declared entry, otherwise {@code line} itself. */
    public String expand(PlayerRef viewer, String line) {
        Objects.requireNonNull(viewer, "viewer");
        Optional<CatalogLine> reference = reference(line);
        if (reference.isEmpty()) {
            return line;
        }
        String key = reference.get().key();
        return messages.resolve(viewer, () -> key, reference.get().arguments());
    }

    private Optional<CatalogLine> reference(String line) {
        Objects.requireNonNull(line, "line");
        return CatalogLine.parse(line).filter(reference -> declared.test(reference.key()));
    }
}
