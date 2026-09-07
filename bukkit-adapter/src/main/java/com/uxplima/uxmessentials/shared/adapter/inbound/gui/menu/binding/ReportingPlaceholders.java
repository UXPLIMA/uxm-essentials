package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.uxplima.uxmlib.menu.binding.PlaceholderRegistry;
import com.uxplima.uxmlib.menu.runtime.MenuContext;
import org.jspecify.annotations.NullMarked;

/**
 * Resolve a placeholder the way a condition does: as {@link PlaceholderRegistry#resolve}, except that a handler
 * which throws yields no text and is named on the console once instead of escaping.
 *
 * <p>The engine's own renderer catches a throwing handler already. A condition does not: it asks for one token by
 * name in the middle of evaluating an operator block, so without this a single broken handler takes the whole gate
 * with it, and a menu whose {@code view-requirement} named that token stops drawing rather than losing one icon.
 * That is the failure this class exists to keep out of a window.
 *
 * <p>The dedupe is per instance and never global. One instance is built where a vocabulary registers itself and is
 * captured by the conditions that vocabulary installs, so a plugin that registers twice reports twice and no
 * consumer sharing a classloader can spend another's budget.
 *
 * <p>{@link PlaceholderRegistry#resolve} keeps throwing on purpose, which is why this wraps it rather than replacing
 * it. A caller that would rather know is entitled to know.
 */
@NullMarked
public final class ReportingPlaceholders {

    private static final Logger LOG = Logger.getLogger(ReportingPlaceholders.class.getName());

    private final PlaceholderRegistry placeholders;

    /**
     * The ids already reported as broken, so a handler that throws on every render of an {@code update = true} menu
     * writes one line rather than one line per tick.
     */
    private final Set<String> reported = ConcurrentHashMap.newKeySet();

    public ReportingPlaceholders(PlaceholderRegistry placeholders) {
        this.placeholders = Objects.requireNonNull(placeholders, "placeholders");
    }

    /** The value of {@code id} for {@code ctx}, or empty when it is unknown or its handler threw. */
    public Optional<String> resolveOrReport(String id, MenuContext ctx) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(ctx, "ctx");
        try {
            return placeholders.resolve(id, ctx);
        } catch (RuntimeException failure) {
            if (reported.add(id)) {
                LOG.log(Level.WARNING, "event=placeholder_failed token=" + id, failure);
            }
            return Optional.empty();
        }
    }
}
