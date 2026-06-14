package com.uxplima.uxmessentials.kits.adapter.outbound;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.kits.application.port.KitClaimStore;
import com.uxplima.uxmessentials.kits.domain.KitId;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.item.PdcFlag;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link KitClaimStore} implementation: a per-player one-time-claim flag stamped in PDC. Each one-time
 * kit a player has consumed is recorded as a {@code (byte) 1} under a per-kit key, and the set of claimed kit
 * ids is mirrored into a single index key so {@link #resetAll} can clear every stamp without scanning all
 * possible kit ids. A one-time claim is transient per-holder state that may safely die with a world rollback
 * (docs/03-paper-api §3.6), which is why it lives in PDC rather than the database.
 *
 * <h2>Concurrency</h2>
 * The per-kit {@link NamespacedKey}s are cached in a {@link ConcurrentHashMap} populated via
 * {@code computeIfAbsent}, so each key is built only once and never on a hot path. PDC reads and writes
 * happen on the player's region thread (the claim command thread), where Paper allows them. An offline
 * player has no PDC, so {@link #hasClaimed} reads {@code false} and the mutators no-op.
 */
@NullMarked
public final class PdcKitClaims implements KitClaimStore {

    private static final String CLAIM_PREFIX = "kit-claimed-";

    private final Plugin plugin;
    private final NamespacedKey indexKey;
    private final ConcurrentHashMap<String, NamespacedKey> keys = new ConcurrentHashMap<>();

    public PdcKitClaims(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.indexKey = new NamespacedKey(plugin, "kit-claimed-index");
    }

    @Override
    public boolean hasClaimed(PlayerRef who, KitId kit) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(kit, "kit");
        Player player = Bukkit.getPlayer(who.uuid());
        if (player == null) {
            return false;
        }
        return PdcFlag.has(player.getPersistentDataContainer(), keyFor(kit));
    }

    @Override
    public void markClaimed(PlayerRef who, KitId kit) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(kit, "kit");
        Player player = Bukkit.getPlayer(who.uuid());
        if (player == null) {
            return;
        }
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        PdcFlag.set(pdc, keyFor(kit), true);
        writeIndex(pdc, add(readIndex(pdc), kit.value()));
    }

    @Override
    public void reset(PlayerRef who, KitId kit) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(kit, "kit");
        Player player = Bukkit.getPlayer(who.uuid());
        if (player == null) {
            return;
        }
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        PdcFlag.remove(pdc, keyFor(kit));
        writeIndex(pdc, remove(readIndex(pdc), kit.value()));
    }

    @Override
    public void resetAll(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        Player player = Bukkit.getPlayer(who.uuid());
        if (player == null) {
            return;
        }
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        for (String id : readIndex(pdc)) {
            PdcFlag.remove(pdc, keyFor(KitId.of(id)));
        }
        pdc.remove(indexKey);
    }

    private NamespacedKey keyFor(KitId kit) {
        return keys.computeIfAbsent(kit.value(), id -> new NamespacedKey(plugin, CLAIM_PREFIX + sanitize(id)));
    }

    private Set<String> readIndex(PersistentDataContainer pdc) {
        String raw = pdc.getOrDefault(indexKey, PersistentDataType.STRING, "");
        if (raw.isEmpty()) {
            return new LinkedHashSet<>();
        }
        return Arrays.stream(raw.split(","))
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private void writeIndex(PersistentDataContainer pdc, Set<String> ids) {
        if (ids.isEmpty()) {
            pdc.remove(indexKey);
            return;
        }
        pdc.set(indexKey, PersistentDataType.STRING, String.join(",", ids));
    }

    private static Set<String> add(Set<String> ids, String id) {
        ids.add(id);
        return ids;
    }

    private static Set<String> remove(Set<String> ids, String id) {
        ids.remove(id);
        return ids;
    }

    /** A NamespacedKey value segment accepts only {@code [a-z0-9._-]}; fold anything else to {@code _}. */
    private static String sanitize(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            boolean legal = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '.' || c == '_' || c == '-';
            out.append(legal ? c : '_');
        }
        return out.toString();
    }
}
