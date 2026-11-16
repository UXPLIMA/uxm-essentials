package com.uxplima.uxmessentials.shared.adapter.outbound.cooldown;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.NamespacedKey;

import org.junit.jupiter.api.Test;

/** The cooldown stamps a write sweeps out of a player's data: only this plugin's, and only those that ran out. */
class PdcCooldownsTest {

    private static final String OURS = "uxmessentials";
    private static final long NOW = 1_000_000L;

    private final Map<NamespacedKey, Long> held = new HashMap<>();

    private NamespacedKey hold(String key, long readyAt) {
        NamespacedKey namespaced = Objects.requireNonNull(NamespacedKey.fromString(key));
        held.put(namespaced, readyAt);
        return namespaced;
    }

    private List<NamespacedKey> expired() {
        return PdcCooldowns.expired(held.keySet(), OURS, held::get, NOW);
    }

    @Test
    void aStampThatRanOutIsSweptAndOneStillRunningIsKept() {
        NamespacedKey spent = hold("uxmessentials:cooldown-tp.warp.arena", NOW - 1);
        NamespacedKey endsNow = hold("uxmessentials:cooldown-tp.warp.shop", NOW);
        hold("uxmessentials:cooldown-tp.warp.mine", NOW + 1);

        assertThat(expired()).containsExactlyInAnyOrder(spent, endsNow);
    }

    @Test
    void keysThatAreNotThisPluginsCooldownStampsAreNeverSwept() {
        hold("uxmessentials:kit-claimed.starter", 0);
        hold("otherplugin:cooldown-tp", 0);

        assertThat(expired()).isEmpty();
    }

    @Test
    void aStampThatCannotBeReadIsKept() {
        NamespacedKey unreadable = Objects.requireNonNull(NamespacedKey.fromString("uxmessentials:cooldown-tp"));

        assertThat(PdcCooldowns.expired(List.of(unreadable), OURS, key -> null, NOW))
                .isEmpty();
    }
}
