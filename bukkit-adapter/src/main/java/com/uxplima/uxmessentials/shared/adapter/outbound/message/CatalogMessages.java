package com.uxplima.uxmessentials.shared.adapter.outbound.message;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.LocaleCatalog;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link Messages} implementation: resolve the viewer's locale, fetch the catalog template, and do
 * literal {@code {name}} placeholder substitution. The return is a plain MiniMessage source string —
 * no Adventure type crosses this boundary, which is what keeps the kernel free of {@code net.kyori};
 * the tag parsing into a {@code Component} happens once downstream in {@link BukkitMessageSink}.
 *
 * <p>The viewer's locale comes from the online {@link Player}'s client locale, falling back to English
 * for an offline or unknown viewer. The per-key {@code en} fallback chain itself lives behind the
 * {@link LocaleCatalog}; this class only chooses which locale to ask for.
 */
@NullMarked
public final class CatalogMessages implements Messages {

    private final LocaleCatalog catalog;

    public CatalogMessages(LocaleCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
    }

    @Override
    public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(placeholders, "placeholders");
        String template = catalog.template(localeOf(viewer), key);
        return substitute(template, placeholders);
    }

    private Locale localeOf(PlayerRef viewer) {
        Player player = Bukkit.getPlayer(viewer.uuid());
        return player != null ? player.locale() : Locale.ENGLISH;
    }

    private static String substitute(String template, Map<String, String> placeholders) {
        String result = template;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }
}
