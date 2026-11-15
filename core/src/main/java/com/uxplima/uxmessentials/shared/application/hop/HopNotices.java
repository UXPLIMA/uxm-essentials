package com.uxplima.uxmessentials.shared.application.hop;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.message.MessageKey;

/**
 * How one kind words its hop notices: a catalog key for every {@link HopNotice}, and the placeholder its lines
 * name the destination with. A kind keeps its own keys so its players read the lines they already know.
 *
 * @param labelTag the placeholder that carries the destination label, such as {@code warp}
 * @param keys a key for every notice
 */
public record HopNotices(String labelTag, Map<HopNotice, MessageKey> keys) {

    public HopNotices {
        Objects.requireNonNull(labelTag, "labelTag");
        Objects.requireNonNull(keys, "keys");
        for (HopNotice notice : HopNotice.values()) {
            if (!keys.containsKey(notice)) {
                throw new IllegalArgumentException("no key for the hop notice " + notice);
            }
        }
        keys = Map.copyOf(new EnumMap<>(keys));
    }

    /** The catalog key for {@code notice}. */
    public MessageKey key(HopNotice notice) {
        return Objects.requireNonNull(keys.get(notice), "key");
    }
}
