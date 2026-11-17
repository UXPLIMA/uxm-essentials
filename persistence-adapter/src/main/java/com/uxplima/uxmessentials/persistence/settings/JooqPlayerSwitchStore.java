package com.uxplima.uxmessentials.persistence.settings;

import static com.uxplima.uxmessentials.persistence.jooq.tables.PlayerSwitches.PLAYER_SWITCHES;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import com.uxplima.uxmessentials.persistence.runtime.PersistenceException;
import com.uxplima.uxmessentials.shared.application.port.PlayerSwitchStore;
import org.jooq.Condition;
import org.jooq.DSLContext;

/**
 * The jOOQ-backed {@link PlayerSwitchStore} over {@code player_switches}: one row per player and switch they set,
 * keyed by both. The state is a SMALLINT, 1 for on. A switch is written only over a row set at the same time or
 * earlier, so two backends writing it at once keep the newer setting: an update guarded by {@code changed_at}, then an
 * insert when no row exists, and the guarded update again when another backend inserted first.
 */
public final class JooqPlayerSwitchStore extends JooqRepository implements PlayerSwitchStore {

    public JooqPlayerSwitchStore(DSLContext dsl) {
        super(dsl);
    }

    @Override
    public Map<String, Switch> load(UUID player, Set<String> names) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(names, "names");
        if (names.isEmpty()) {
            return Map.of();
        }
        Map<String, Switch> switches = new HashMap<>();
        read(dsl -> dsl.select(PLAYER_SWITCHES.SWITCH_NAME, PLAYER_SWITCHES.SWITCH_ON, PLAYER_SWITCHES.CHANGED_AT)
                        .from(PLAYER_SWITCHES)
                        .where(PLAYER_SWITCHES.PLAYER_UUID.eq(player.toString()))
                        .and(PLAYER_SWITCHES.SWITCH_NAME.in(names))
                        .fetch())
                .forEach(row -> switches.put(row.value1(), new Switch(row.value2() != 0, row.value3())));
        return Map.copyOf(switches);
    }

    @Override
    public void save(UUID player, Map<String, Switch> switches) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(switches, "switches");
        switches.forEach((name, set) -> saveOne(player.toString(), name, set));
    }

    private void saveOne(String player, String name, Switch set) {
        if (write(dsl -> updateIfNotNewer(dsl, player, name, set)) > 0) {
            return;
        }
        if (read(dsl -> dsl.fetchExists(PLAYER_SWITCHES, row(player, name)))) {
            return; // the stored setting was made later than this one
        }
        try {
            write(dsl -> dsl.insertInto(PLAYER_SWITCHES)
                    .set(PLAYER_SWITCHES.PLAYER_UUID, player)
                    .set(PLAYER_SWITCHES.SWITCH_NAME, name)
                    .set(PLAYER_SWITCHES.SWITCH_ON, flag(set.on()))
                    .set(PLAYER_SWITCHES.CHANGED_AT, set.changedAtMillis())
                    .execute());
        } catch (PersistenceException raced) {
            // Another backend inserted the row between the existence check and this insert.
            write(dsl -> updateIfNotNewer(dsl, player, name, set));
        }
    }

    private static int updateIfNotNewer(DSLContext dsl, String player, String name, Switch set) {
        return dsl.update(PLAYER_SWITCHES)
                .set(PLAYER_SWITCHES.SWITCH_ON, flag(set.on()))
                .set(PLAYER_SWITCHES.CHANGED_AT, set.changedAtMillis())
                .where(row(player, name))
                .and(PLAYER_SWITCHES.CHANGED_AT.le(set.changedAtMillis()))
                .execute();
    }

    private static Condition row(String player, String name) {
        return PLAYER_SWITCHES.PLAYER_UUID.eq(player).and(PLAYER_SWITCHES.SWITCH_NAME.eq(name));
    }

    private static short flag(boolean on) {
        return on ? (short) 1 : (short) 0;
    }
}
