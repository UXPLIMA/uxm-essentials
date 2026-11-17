package com.uxplima.uxmessentials.shared.adapter.inbound.gui;

import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.adapter.outbound.message.CatalogMessages;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.PlaceholderApiSupport;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyledText;
import com.uxplima.uxmessentials.shared.application.message.CatalogLine;
import com.uxplima.uxmessentials.shared.application.message.TrustedMarkup;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.message.MessageKey;
import org.jspecify.annotations.NullMarked;

/**
 * Resolves a {@link MessageKey} into an Adventure {@link Component} in the viewer's locale, rendering the
 * catalog entry through {@link StyledText} so a menu's item names, lore, and title pick up the same style
 * tokens the chat sink uses. The shared management-GUI framework (and any module view that opts in) holds one
 * instance, so there is exactly one place a key turns into text and every label reads identically wherever it
 * is raised. This is the framework-wide generalisation of the per-module text helpers (e.g. the worlds
 * editor's own), so a new module's management GUI needs no bespoke text adapter.
 */
@NullMarked
public final class GuiText implements com.uxplima.uxmlib.gui.GuiText {

    private final Messages messages;

    public GuiText(Messages messages) {
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    /** Resolve {@code key} for {@code viewer} with no placeholders. */
    public Component text(PlayerRef viewer, MessageKey key) {
        return text(viewer, key, Map.of());
    }

    /** Resolve {@code key} for {@code viewer} with {@code placeholders} substituted. */
    public Component text(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(placeholders, "placeholders");
        return StyledText.render(messages.resolve(viewer, key, placeholders));
    }

    /**
     * Resolve {@code key} for {@code viewer} with the leading brand prefix ({@code <tag:'…'>} / {@code <etag:'…'>})
     * removed before rendering. Catalog prompt keys carry the chat prefix, but an inventory title (such as an anvil
     * prompt) must read clean (just the prompt, no {@code uxmEssentials »} brand) so this strips that one leading
     * token. A key with no prefix is rendered unchanged.
     */
    public Component unprefixedText(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(placeholders, "placeholders");
        return StyledText.render(stripBrandPrefix(messages.resolve(viewer, key, placeholders)));
    }

    /**
     * The engine's own lookup: a catalog key as a plain string, for a live viewer.
     *
     * <p>uxmLib's menu engine names no {@link MessageKey} and no {@link PlayerRef}, so this is where the two
     * vocabularies meet. A key is its catalog string and nothing else, which is what lets the lambda stand in
     * for a constant here, and the viewer is narrowed to the record the catalog resolves a locale against.
     */
    @Override
    public Component text(Player viewer, String key, Map<String, String> placeholders) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(placeholders, "placeholders");
        Reference reference = reference(key);
        PlayerRef who = BukkitRefs.toRef(viewer);
        Map<String, String> engine = trusting(placeholders, GuiText::madeFit);
        String resolved =
                messages.resolve(who, reference.key(), overlay(trusting(reference.arguments(), name -> true), engine));
        if (!PlaceholderApiSupport.hasPlaceholder(resolved)) {
            return StyledText.render(resolved);
        }
        // An operator's entry may carry a %papi% placeholder, and the order then is the point. The entry and the
        // operator's own arguments are the operator's words, so their placeholders resolve. The engine's values go in
        // afterwards as they are: one may be text a player typed, and a %placeholder% in it would otherwise be
        // answered on somebody else's screen.
        String template = messages.resolveAsWritten(who, reference.key(), reference.arguments());
        String expanded = PlaceholderApiSupport.hasPlaceholder(template)
                ? PlaceholderApiSupport.markupBridge(viewer.getUniqueId()).apply(template)
                : template;
        return StyledText.render(CatalogMessages.fill(expanded, engine));
    }

    /**
     * {@code values} with each one {@code trusted} names marked to go into the line as written ({@link TrustedMarkup}).
     * The engine hands over the values it also puts into a menu's own lines, so they are fit for a line already: a
     * placeholder's value was escaped where it was bound ({@link MenuText}), and an operator's own {@code @key}
     * arguments are the operator's words.
     */
    private static Map<String, String> trusting(Map<String, String> values, Predicate<String> trusted) {
        return new java.util.AbstractMap<>() {
            @Override
            public @org.jspecify.annotations.Nullable String get(@org.jspecify.annotations.Nullable Object name) {
                String value = values.get(name);
                return value == null || !(name instanceof String named) ? value : marked(named, value);
            }

            @Override
            public java.util.Set<Map.Entry<String, String>> entrySet() {
                Map<String, String> listed = new java.util.LinkedHashMap<>();
                values.forEach((name, value) -> listed.put(name, marked(name, value)));
                return listed.entrySet();
            }

            private String marked(String name, String value) {
                return trusted.test(name) ? TrustedMarkup.of(value) : value;
            }
        };
    }

    /**
     * The key and arguments an engine key names. A menu line written as {@code @key name=value} reaches here whole,
     * after the at sign, and its arguments fill the entry before the engine's own values can.
     */
    private static Reference reference(String key) {
        return CatalogLine.parse("@" + key)
                .map(line -> {
                    String named = line.key();
                    return new Reference(() -> named, line.arguments());
                })
                .orElseGet(() -> new Reference(() -> key, Map.of()));
    }

    /**
     * The operator's arguments in front of the engine's values. The engine's map answers a name when asked rather than
     * listing every value it could give, so this asks it by name too instead of copying it.
     */
    private static Map<String, String> overlay(Map<String, String> written, Map<String, String> engine) {
        if (written.isEmpty()) {
            return engine;
        }
        return new java.util.AbstractMap<>() {
            @Override
            public @org.jspecify.annotations.Nullable String get(@org.jspecify.annotations.Nullable Object name) {
                String own = written.get(name);
                return own != null ? own : engine.get(name);
            }

            @Override
            public java.util.Set<Map.Entry<String, String>> entrySet() {
                Map<String, String> listed = new java.util.LinkedHashMap<>(engine);
                listed.putAll(written);
                return listed.entrySet();
            }
        };
    }

    /** The engine's own prefix for a menu argument, which is what a player typed when they opened the menu. */
    private static final String ARGUMENT = "argument_";

    /**
     * Whether the engine's value for {@code name} was made fit for a line before it got here. Every value is but a menu
     * argument: the engine fills that from what a player typed and binds it nowhere, so the fill escapes it.
     */
    private static boolean madeFit(String name) {
        return !name.startsWith(ARGUMENT);
    }

    private record Reference(MessageKey key, Map<String, String> arguments) {}

    /** The engine's unprefixed lookup, which strips the same brand token {@link #unprefixedText} does. */
    @Override
    public Component textUnprefixed(Player viewer, String key, Map<String, String> placeholders) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(key, "key");
        return unprefixedText(BukkitRefs.toRef(viewer), () -> key, trusting(placeholders, GuiText::madeFit));
    }

    /**
     * Operator-authored MiniMessage out of a menu file, rendered through the same style tags every other line
     * here goes through. It carries its own words, so it needs no viewer and no catalog.
     */
    @Override
    public Component render(String raw) {
        Objects.requireNonNull(raw, "raw");
        return StyledText.render(raw);
    }

    /** A leading {@code <tag:'…'>} or {@code <etag:'…'>} brand prefix token and the space after it, if present. */
    private static final java.util.regex.Pattern BRAND_PREFIX =
            java.util.regex.Pattern.compile("^\\s*<(?:tag|etag):'[^']*'>\\s*");

    /** Remove a leading brand-prefix token from {@code source}; returns it unchanged when there is none. */
    static String stripBrandPrefix(String source) {
        Objects.requireNonNull(source, "source");
        return BRAND_PREFIX.matcher(source).replaceFirst("");
    }
}
