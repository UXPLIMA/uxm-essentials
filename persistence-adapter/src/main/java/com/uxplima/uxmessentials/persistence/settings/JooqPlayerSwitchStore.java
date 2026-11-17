package com.uxplima.uxmessentials.persistence.settings;

import static com.uxplima.uxmessentials.persistence.jooq.tables.PlayerSwitches.PLAYER_SWITCHES;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import com.uxplima.uxmessentials.shared.application.port.PlayerSwitchStore;
import org.jooq.DSLContext;

/**
 * The jOOQ-backed {@link PlayerSwitchStore} over {@code player_switches}: one row per player and switch they set,
 * keyed by both, so setting a switch again upserts its row. The state is a SMALLINT, 1 for on.
 */
public final class JooqPlayerSwitchStore extends JooqRepository implements PlayerSwitchStore {

    public JooqPlayerSwitchStore(DSLContext dsl) {
        super(dsl);
    }

    @Override
    public Map<String, Boolean> load(UUID player, Set<String> names) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(names, "names");
        if (names.isEmpty()) {
            return Map.of();
        }
        Map<String, Boolean> switches = new HashMap<>();
        read(dsl -> dsl.select(PLAYER_SWITCHES.SWITCH_NAME, PLAYER_SWITCHES.SWITCH_ON)
                        .from(PLAYER_SWITCHES)
                        .where(PLAYER_SWITCHES.PLAYER_UUID.eq(player.toString()))
                        .and(PLAYER_SWITCHES.SWITCH_NAME.in(names))
                        .fetch())
                .forEach(row -> switches.put(row.value1(), row.value2() != 0));
        return Map.copyOf(switches);
    }

    @Override
    public void save(UUID player, Map<String, Boolean> switches) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(switches, "switches");
        if (switches.isEmpty()) {
            return;
        }
        write(dsl -> {
            switches.forEach((name, on) -> dsl.insertInto(PLAYER_SWITCHES)
                    .set(PLAYER_SWITCHES.PLAYER_UUID, player.toString())
                    .set(PLAYER_SWITCHES.SWITCH_NAME, name)
                    .set(PLAYER_SWITCHES.SWITCH_ON, flag(on))
                    .onConflict(PLAYER_SWITCHES.PLAYER_UUID, PLAYER_SWITCHES.SWITCH_NAME)
                    .doUpdate()
                    .set(PLAYER_SWITCHES.SWITCH_ON, flag(on))
                    .execute());
            return null;
        });
    }

    private static short flag(boolean on) {
        return on ? (short) 1 : (short) 0;
    }
}
