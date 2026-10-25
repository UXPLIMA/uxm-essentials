package com.uxplima.uxmessentials.bossbar.adapter.outbound;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.uxplima.uxmessentials.bossbar.domain.BossBarWindow;
import org.jspecify.annotations.NullMarked;

/**
 * The bars a command has put up for a while: for everybody with {@code /bossbars announce}, or for one player with
 * {@code /bossbars send}. Kept in memory, because an announcement lasts seconds or minutes and a restart may end it.
 * A window that has ended is simply never active again; it is dropped the next time the same bar is announced.
 */
@NullMarked
public final class BossBarAnnouncements {

    private final Map<String, BossBarWindow> everybody = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, BossBarWindow>> personal = new ConcurrentHashMap<>();

    /** Put bar {@code id} up for everybody for {@code length} from {@code now}. */
    public void announce(String id, Duration length, Instant now) {
        everybody.put(Objects.requireNonNull(id, "id"), window(length, now));
    }

    /** Put bar {@code id} up for {@code player} alone for {@code length} from {@code now}. */
    public void send(UUID player, String id, Duration length, Instant now) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(id, "id");
        BossBarWindow window = window(length, now);
        personal.compute(player, (who, current) -> {
            Map<String, BossBarWindow> next = new HashMap<>(current == null ? Map.of() : current);
            next.put(id, window);
            return Map.copyOf(next);
        });
    }

    /** The announcement window of bar {@code id} for {@code player}: their own first, then everybody's. */
    public Optional<BossBarWindow> windowFor(UUID player, String id) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(id, "id");
        BossBarWindow own = personal.getOrDefault(player, Map.of()).get(id);
        return Optional.ofNullable(own != null ? own : everybody.get(id));
    }

    /** Drop what was sent to a player who left. */
    public void forget(UUID player) {
        personal.remove(Objects.requireNonNull(player, "player"));
    }

    private static BossBarWindow window(Duration length, Instant now) {
        Objects.requireNonNull(length, "length");
        Objects.requireNonNull(now, "now");
        if (length.isNegative() || length.isZero()) {
            throw new IllegalArgumentException("an announcement must last some time");
        }
        return new BossBarWindow(Optional.of(now), now.plus(length));
    }
}
