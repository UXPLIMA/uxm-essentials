package com.uxplima.uxmessentials.migration.convert.essentialsx.map;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.uxplima.uxmessentials.kits.domain.KitDefinition;
import com.uxplima.uxmessentials.kits.domain.KitId;
import com.uxplima.uxmessentials.kits.domain.KitItem;
import com.uxplima.uxmessentials.migration.convert.essentialsx.parse.EssXKit;
import com.uxplima.uxmessentials.migration.convert.map.ImportedKit;
import com.uxplima.uxmessentials.shared.application.permission.ContentPermissions;
import com.uxplima.uxmessentials.shared.application.permission.PermissionCatalog;
import com.uxplima.uxmessentials.shared.application.permission.PermissionSpec;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.NullMarked;

/**
 * Translates a parsed EssentialsX kit into a domain {@link KitDefinition}. The
 * EssentialsX {@code delay} (seconds) becomes the kit cooldown, and each raw item descriptor line becomes an
 * opaque {@link KitItem} whose {@code data} is the descriptor and whose amount is the trailing quantity
 * token (defaulting to 1). The descriptor stays opaque. The bukkit-side writer round-trips it through
 * Bukkit's item codec, so this mapper never imports an {@code ItemStack} type.
 *
 * <p>EssentialsX hands a kit only to holders of {@code essentials.kits.<name>}, so every imported kit is reserved
 * behind its per-kit node {@code uxmessentials.kit.<id>}, which defaults to operators. A kit whose per-kit node is one
 * of the plugin's own fixed nodes ({@code use}, {@code edit}, ...) would sit behind that node instead and open to
 * whoever holds it, so such a kit keeps its EssentialsX node as its {@code permission-node}, with a warning.
 */
@NullMarked
public final class KitMapper {

    private static final String ESSENTIALS_KIT_PREFIX = "essentials.kits.";

    private static final Set<String> FIXED_NODES =
            PermissionCatalog.registrable().stream().map(PermissionSpec::node).collect(Collectors.toUnmodifiableSet());

    private final Logger log;

    /** @param log where a kit whose node would be one of the plugin's own fixed nodes is reported */
    public KitMapper(Logger log) {
        this.log = Objects.requireNonNull(log, "log");
    }

    public ImportedKit map(EssXKit src) {
        Objects.requireNonNull(src, "src");
        // EssentialsX reads a negative delay as "each player may claim this kit once", not as a cooldown.
        boolean oneTime = src.delaySeconds() < 0;
        KitId id = KitId.of(src.name());
        KitDefinition definition = KitDefinition.repeatable(
                        id, items(src.items()), Duration.ofSeconds(oneTime ? 0L : src.delaySeconds()))
                .withOneTime(oneTime)
                .withPermission(true)
                .withCustomPermission(collisionNode(id));
        return new ImportedKit(definition);
    }

    private Optional<String> collisionNode(KitId id) {
        String own = ContentPermissions.kit(id.value());
        if (!FIXED_NODES.contains(own)) {
            return Optional.empty();
        }
        String essentials = ESSENTIALS_KIT_PREFIX + id.value();
        log.warn(
                "EssentialsX kit {} would share the node {}; it is gated by {} instead (permission-node in its file)",
                id.value(),
                own,
                essentials);
        return Optional.of(essentials);
    }

    private List<KitItem> items(List<String> rawLines) {
        List<KitItem> items = new ArrayList<>();
        for (String line : rawLines) {
            items.add(KitItem.of(line, amountOf(line)));
        }
        return items;
    }

    private static final java.util.regex.Pattern WHITESPACE = java.util.regex.Pattern.compile("\\s+");

    private static int amountOf(String line) {
        List<String> tokens = WHITESPACE.splitAsStream(line.strip()).toList();
        if (tokens.size() < 2) {
            return 1;
        }
        try {
            return Math.max(1, Integer.parseInt(tokens.get(1)));
        } catch (NumberFormatException notAQuantity) {
            // The second token is not a count (an enchantment or metadata token); the writer reads the
            // full descriptor, so the kit still imports with a single stack of the item.
            return 1;
        }
    }
}
