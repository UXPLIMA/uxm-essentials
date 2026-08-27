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
    private final LocaleStore overrides;
    private final Locale serverDefault;

    public LocalizedCommandVisibilityListener(
            List<EffectiveCommand> commands, LocaleStore overrides, Locale serverDefault, String commandNamespace) {
        this.localesByAlias = buildIndex(commands, commandNamespace);
        this.overrides = Objects.requireNonNull(overrides, "overrides");
        this.serverDefault = Objects.requireNonNull(serverDefault, "serverDefault");
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
        if (!localesByAlias.isEmpty()
                && overrides.override(BukkitRefs.toRef(player)).isEmpty()) {
            player.updateCommands();
        }
    }

    private Locale resolveLocale(Player player) {
        Locale resolved = overrides.override(BukkitRefs.toRef(player)).orElseGet(player::locale);
        return resolved.getLanguage().isEmpty() ? serverDefault : resolved;
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
