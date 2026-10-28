package com.uxplima.uxmessentials.holograms.adapter.outbound;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import org.jspecify.annotations.NullMarked;

/**
 * MiniMessage results kept by their source text, so words that many viewers read alike are parsed once.
 *
 * <p>A hologram whose lines come from the message catalogue is resolved for each viewer, and on a busy lobby a
 * hundred viewers of one board hand the parser the same string once their placeholders are filled. Parsing it once
 * and sharing the component, which is immutable, turns a hundred parses into one. A {@code <head:name>} tag, which
 * fails a UUID parse before it reads the name, pays that failure once too.
 *
 * <p>Only a parse with no extra tags is kept. With MiniPlaceholders installed the global tags read live server state
 * ({@code <server_online>}), so the same source can read differently a second later; that parse runs every time.
 * The store starts again when it holds {@link #LIMIT} texts, which a board whose values keep changing would
 * otherwise grow without end.
 */
@NullMarked
final class ParsedText {

    /** How many parsed texts are kept before the store starts again. */
    static final int LIMIT = 4096;

    private final MiniMessage miniMessage;
    private final Map<String, Component> parsed = new ConcurrentHashMap<>();

    ParsedText(MiniMessage miniMessage) {
        this.miniMessage = Objects.requireNonNull(miniMessage, "miniMessage");
    }

    /** {@code source} parsed with {@code tags}, shared with every earlier parse of the same source. */
    Component parse(String source, TagResolver tags) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(tags, "tags");
        // TagResolver.empty() is one shared instance: anything else may read live state and is never kept.
        if (tags != TagResolver.empty()) {
            return miniMessage.deserialize(source, tags);
        }
        Component known = parsed.get(source);
        if (known != null) {
            return known;
        }
        Component fresh = miniMessage.deserialize(source, tags);
        if (parsed.size() >= LIMIT) {
            parsed.clear();
        }
        Component raced = parsed.putIfAbsent(source, fresh);
        return raced == null ? fresh : raced;
    }
}
