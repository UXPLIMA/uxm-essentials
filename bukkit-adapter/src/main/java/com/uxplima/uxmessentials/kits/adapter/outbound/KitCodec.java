package com.uxplima.uxmessentials.kits.adapter.outbound;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.uxplima.uxmessentials.kits.domain.KitCost;
import com.uxplima.uxmessentials.kits.domain.KitDefinition;
import com.uxplima.uxmessentials.kits.domain.KitId;
import com.uxplima.uxmessentials.kits.domain.KitItem;
import org.jspecify.annotations.NullMarked;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;

/**
 * Reads and writes one {@code kits.conf} entry as a {@link KitDefinition}. The HOCON shape is
 *
 * <pre>{@code
 * kits {
 *   starter {
 *     cooldown = 0          # seconds between claims; 0 = no cooldown
 *     one-time = true       # claimable once per player
 *     permission = false    # require uxmessentials.kit.<id>
 *     cost = 0              # claim price; charged only when economy is wired
 *     items = [ "<base64>", { data = "<base64>", amount = 16 } ]
 *   }
 * }
 * }</pre>
 *
 * An item is either a bare Base64 string (its serialized form carries the amount) or a {@code {data, amount}}
 * map for readability. A read that cannot produce a valid definition returns empty so the repository skips
 * the entry rather than failing the whole load.
 */
@NullMarked
final class KitCodec {

    private KitCodec() {}

    /** Parse the node {@code node} under id {@code id} into a definition, or empty when it is malformed. */
    static Optional<KitDefinition> read(String id, ConfigurationNode node) {
        try {
            KitId kitId = KitId.of(id);
            List<KitItem> items = readItems(node.node("items"));
            Duration cooldown =
                    Duration.ofSeconds(Math.max(0L, node.node("cooldown").getLong(0L)));
            boolean oneTime = node.node("one-time").getBoolean(false);
            boolean permission = node.node("permission").getBoolean(false);
            KitCost cost = readCost(node.node("cost"));
            return Optional.of(new KitDefinition(kitId, items, cooldown, oneTime, permission, cost));
        } catch (RuntimeException malformed) {
            return Optional.empty();
        }
    }

    /** Write {@code definition} into {@code node} in the documented shape. */
    static void write(ConfigurationNode node, KitDefinition definition) throws ConfigurateException {
        node.node("cooldown").set(definition.cooldownSeconds());
        node.node("one-time").set(definition.oneTime());
        node.node("permission").set(definition.permission());
        node.node("cost").set(definition.cost().amount().toPlainString());
        ConfigurationNode items = node.node("items");
        items.set(null);
        for (KitItem item : definition.items()) {
            ConfigurationNode child = items.appendListNode();
            child.node("data").set(item.data());
            child.node("amount").set(item.amount());
        }
    }

    private static List<KitItem> readItems(ConfigurationNode node) {
        List<KitItem> items = new ArrayList<>();
        for (ConfigurationNode child : node.childrenList()) {
            readItem(child).ifPresent(items::add);
        }
        return items;
    }

    private static Optional<KitItem> readItem(ConfigurationNode child) {
        if (child.isMap()) {
            String data = child.node("data").getString("");
            int amount = Math.max(1, child.node("amount").getInt(1));
            return data.isBlank() ? Optional.empty() : Optional.of(KitItem.of(data, amount));
        }
        String raw = child.getString("");
        return raw.isBlank() ? Optional.empty() : Optional.of(KitItem.of(raw, 1));
    }

    private static KitCost readCost(ConfigurationNode node) {
        String raw = node.getString("");
        if (raw.isBlank()) {
            return KitCost.free();
        }
        try {
            BigDecimal amount = new BigDecimal(raw.strip());
            return amount.signum() <= 0 ? KitCost.free() : KitCost.of(amount);
        } catch (NumberFormatException notANumber) {
            return KitCost.free();
        }
    }
}
