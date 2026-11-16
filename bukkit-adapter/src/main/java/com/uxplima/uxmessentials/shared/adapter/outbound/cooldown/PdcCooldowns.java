package com.uxplima.uxmessentials.shared.adapter.outbound.cooldown;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.application.ledger.CooldownTiers;
import com.uxplima.uxmessentials.shared.application.permission.SharedPermissions;
import com.uxplima.uxmessentials.shared.application.port.Cooldowns;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The {@link Cooldowns} implementation: a per-holder "ready-at" epoch-millis stamped in PDC. The
 * duration is resolved by {@link CooldownTiers} from the player's numbered
 * {@code uxmessentials.<feature>.cooldown.<seconds>} nodes (a held tier sets the wait, the shortest held wins), and
 * {@code uxmessentials.bypass.cooldown.<feature>} skips the gate entirely. The generic per-command
 * layer ({@link #checkLabel}/{@link #stampLabel}) keys the same machinery by an operator-chosen label,
 * skipped by {@code uxmessentials.bypass.cooldown.label.<label>} so a label can never lift a feature.
 *
 * <p>Stamps are transient per-holder state that may safely die with a world rollback, which is why
 * a single server keeps them in PDC rather than the database. A network keeps them in the database instead
 * ({@code NetworkCooldowns}), since each backend has its own PDC. An offline player has no PDC to
 * read, so a check passes and a stamp is a no-op.
 */
@NullMarked
public final class PdcCooldowns implements Cooldowns {

    /** The start of every cooldown stamp's PDC key; the rest is the normalized stamp name. */
    public static final String KEY_PREFIX = "cooldown-";

    private static final String LABEL_FEATURE_PREFIX = "cooldown.";

    private final PdcKeys keys;
    private final Permissions permissions;
    private final Clock clock;

    public PdcCooldowns(Plugin plugin, Permissions permissions, Clock clock) {
        Objects.requireNonNull(plugin, "plugin");
        this.keys = new PdcKeys(plugin, KEY_PREFIX);
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public Result<Unit, Duration> check(PlayerRef who, CooldownKind kind) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(kind, "kind");
        if (permissions.has(who, kind.bypassNode())) {
            return Result.ok();
        }
        return gate(who, keys.forName(kind.stampScope()));
    }

    @Override
    public void stamp(PlayerRef who, CooldownKind kind) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(kind, "kind");
        if (permissions.has(who, kind.bypassNode())) {
            return;
        }
        long seconds = CooldownTiers.waitSeconds(permissions, who, kind);
        writeStamp(who, keys.forName(kind.stampScope()), seconds);
    }

    @Override
    public Result<Unit, Duration> checkLabel(PlayerRef who, String label) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(label, "label");
        if (permissions.has(who, SharedPermissions.cooldownBypass(label))) {
            return Result.ok();
        }
        return gate(who, keys.forName(LABEL_FEATURE_PREFIX + label));
    }

    @Override
    public void stampLabel(PlayerRef who, String label) {
        stampLabel(who, label, Duration.ZERO);
    }

    @Override
    public void stampLabel(PlayerRef who, String label, Duration configDefault) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(configDefault, "configDefault");
        if (permissions.has(who, SharedPermissions.cooldownBypass(label))) {
            return;
        }
        long seconds = CooldownTiers.labelWaitSeconds(permissions, who, label, configDefault.toSeconds());
        writeStamp(who, keys.forName(LABEL_FEATURE_PREFIX + label), seconds);
    }

    private Result<Unit, Duration> gate(PlayerRef who, NamespacedKey key) {
        Player player = Bukkit.getPlayer(who.uuid());
        if (player == null) {
            return Result.ok();
        }
        long readyAt = player.getPersistentDataContainer().getOrDefault(key, PersistentDataType.LONG, 0L);
        long now = clock.millis();
        return now >= readyAt ? Result.ok() : Result.err(Duration.ofMillis(readyAt - now));
    }

    private void writeStamp(PlayerRef who, NamespacedKey key, long seconds) {
        if (seconds <= 0) {
            return; // a zero-length cooldown stamps nothing. There is no wait to enforce
        }
        Player player = Bukkit.getPlayer(who.uuid());
        if (player == null) {
            return;
        }
        long now = clock.millis();
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        // Each warp with its own cooldown adds a stamp of its own, so stamps that already ran out are dropped here
        // rather than left in the player's data for good.
        for (NamespacedKey spent : expired(pdc.getKeys(), keys.namespace(), stamp -> readyAtOf(pdc, stamp), now)) {
            pdc.remove(spent);
        }
        pdc.set(key, PersistentDataType.LONG, CooldownTiers.readyAt(now, seconds));
    }

    /**
     * The cooldown stamps among {@code held} that ran out by {@code nowMillis}: keys of {@code namespace} under
     * {@link #KEY_PREFIX} whose ready-at, as {@code readyAt} reads it, is not after now. A key {@code readyAt} cannot
     * read as a stamp is never offered for removal.
     */
    static List<NamespacedKey> expired(
            Iterable<NamespacedKey> held,
            String namespace,
            Function<NamespacedKey, @Nullable Long> readyAt,
            long nowMillis) {
        List<NamespacedKey> spent = new ArrayList<>();
        for (NamespacedKey key : held) {
            if (!key.getNamespace().equals(namespace) || !key.getKey().startsWith(KEY_PREFIX)) {
                continue;
            }
            Long end = readyAt.apply(key);
            if (end != null && end <= nowMillis) {
                spent.add(key);
            }
        }
        return spent;
    }

    private static @Nullable Long readyAtOf(PersistentDataContainer pdc, NamespacedKey key) {
        return pdc.has(key, PersistentDataType.LONG) ? pdc.get(key, PersistentDataType.LONG) : null;
    }
}
