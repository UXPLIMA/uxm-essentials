package com.uxplima.uxmessentials.moderation.adapter.outbound;

import java.util.Objects;
import java.util.Optional;

import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.moderation.application.port.TargetResolver;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link TargetResolver} over the Paper {@link Server}: resolve an online player by exact name first,
 * then fall back to the offline-player profile cache (has-played-before) so a sanction reaches an offline or
 * since-disconnected target. A name that the server has never seen resolves to empty.
 *
 * <p>The offline fallback only accepts a profile that has actually played before — {@code getOfflinePlayer}
 * always returns a non-null handle, so an unknown name would otherwise resolve to a fabricated UUID; gating on
 * {@code hasPlayedBefore} keeps a typo from creating a ghost sanction target.
 */
@NullMarked
public final class BukkitTargetResolver implements TargetResolver {

    private final Server server;

    public BukkitTargetResolver(Server server) {
        this.server = Objects.requireNonNull(server, "server");
    }

    @Override
    public Optional<PlayerRef> resolve(String name) {
        Objects.requireNonNull(name, "name");
        Player online = server.getPlayerExact(name);
        if (online != null) {
            return Optional.of(BukkitRefs.toRef(online));
        }
        OfflinePlayer offline = server.getOfflinePlayer(name);
        String resolvedName = offline.getName();
        if (!offline.hasPlayedBefore() || resolvedName == null) {
            return Optional.empty();
        }
        return Optional.of(new PlayerRef(offline.getUniqueId(), resolvedName));
    }
}
