package com.uxplima.uxmessentials.shared.adapter.inbound.command;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.bukkit.event.player.PlayerLocaleChangeEvent;

import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.adapter.outbound.message.ClientLocales;
import com.uxplima.uxmessentials.shared.adapter.outbound.message.LocaleResolver;
import com.uxplima.uxmessentials.shared.application.command.CommandLocales;
import com.uxplima.uxmessentials.shared.application.command.EffectiveCommand;
import com.uxplima.uxmessentials.shared.application.port.LocaleStore;
import org.jspecify.annotations.NullMarked;

/**
 * Keeps Paper's global localized command aliases cheap and client-specific. Every alias is registered globally
 * so dispatch, console use and Geyser translation remain ordinary Brigadier paths; when Paper builds a player's
 * command graph this listener removes only aliases belonging exclusively to other locales. Canonical names and
 * ordinary aliases are never hidden.
 */
@NullMarked
public final class LocalizedCommandVisibilityListener implements Listener {

    private final Map<String, Set<String>> localesByAlias;
    private final LocaleResolver resolver;

    /** A listener that follows the client and forces nothing, with {@code serverDefault} as the default. */
    public LocalizedCommandVisibilityListener(
            List<EffectiveCommand> commands, LocaleStore overrides, Locale serverDefault, String commandNamespace) {
        this(commands, new LocaleResolver(overrides, new ClientLocales(), serverDefault), commandNamespace);
    }

    /** A listener that reads a player's language by the server's own rules. */
    public LocalizedCommandVisibilityListener(
            List<EffectiveCommand> commands, LocaleResolver resolver, String commandNamespace) {
        this.localesByAlias = buildIndex(commands, commandNamespace);
        this.resolver = Objects.requireNonNull(resolver, "resolver");
    }

    @EventHandler
    public void onCommandSend(PlayerCommandSendEvent event) {
        if (localesByAlias.isEmpty()) {
            return;
        }
        Locale locale = resolveLocale(event.getPlayer());
        event.getCommands().removeIf(label -> hiddenFor(label, locale));
    }

    @EventHandler
    public void onLocaleChange(PlayerLocaleChangeEvent event) {
        Player player = event.getPlayer();
        if (!localesByAlias.isEmpty() && resolver.readsTheClient(BukkitRefs.toRef(player))) {
            player.updateCommands();
        }
    }

    private Locale resolveLocale(Player player) {
        return resolver.resolveAt(BukkitRefs.toRef(player), player.locale());
    }

    private boolean hiddenFor(String sentLabel, Locale locale) {
        Set<String> allowedLocales = localesByAlias.get(key(sentLabel));
        return allowedLocales != null
                && allowedLocales.stream().noneMatch(configured -> CommandLocales.matches(configured, locale));
    }

    private static Map<String, Set<String>> buildIndex(List<EffectiveCommand> commands, String commandNamespace) {
        Objects.requireNonNull(commands, "commands");
        String namespace =
                Objects.requireNonNull(commandNamespace, "commandNamespace").toLowerCase(Locale.ROOT);
        Map<String, Set<String>> mutable = new HashMap<>();
        for (EffectiveCommand command : commands) {
            if (!command.enabled()) {
                continue;
            }
            command.localizedAliases()
                    .forEach((locale, aliases) -> aliases.forEach(alias -> {
                        addLocale(mutable, key(alias), locale);
                        addLocale(mutable, namespace + ":" + key(alias), locale);
                    }));
        }
        Map<String, Set<String>> snapshot = new HashMap<>();
        mutable.forEach((alias, locales) -> snapshot.put(alias, Set.copyOf(locales)));
        return Map.copyOf(snapshot);
    }

    private static void addLocale(Map<String, Set<String>> into, String alias, String locale) {
        into.computeIfAbsent(alias, ignored -> new HashSet<>()).add(locale);
    }

    private static String key(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
    }
}
