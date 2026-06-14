package com.uxplima.uxmessentials.communication.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.uxplima.uxmessentials.shared.display.BroadcastChannel;
import org.junit.jupiter.api.Test;

/**
 * The {@link AdvancementNoticeConfig} value object's invariants: the disabled default's shape, the empty-channels
 * fallback to chat, the defensive copies of the mutable inputs, and the lowercase normalization of every operator
 * key (allow, deny, and the per-advancement template map) so matching downstream is case-insensitive.
 */
class AdvancementNoticeConfigTest {

    @Test
    void disabledCarriesSensibleDefaults() {
        AdvancementNoticeConfig cfg = AdvancementNoticeConfig.disabled();

        assertThat(cfg.enabled()).isFalse();
        assertThat(cfg.excludeRecipes()).isTrue();
        assertThat(cfg.onlyAnnounceable()).isTrue();
        assertThat(cfg.allow()).isEmpty();
        assertThat(cfg.deny()).isEmpty();
        assertThat(cfg.template()).isEmpty();
        assertThat(cfg.perAdvancementTemplates()).isEmpty();
        assertThat(cfg.channels()).containsExactly(BroadcastChannel.CHAT);
        assertThat(cfg.sound()).isEmpty();
    }

    @Test
    void anEmptyChannelSetDefaultsToChat() {
        AdvancementNoticeConfig cfg = config(Set.of(), Set.of(), Map.of(), Set.of());

        assertThat(cfg.channels()).containsExactly(BroadcastChannel.CHAT);
    }

    @Test
    void theRequestedChannelsAreRetainedWhenPresent() {
        AdvancementNoticeConfig cfg =
                config(Set.of(), Set.of(), Map.of(), Set.of(BroadcastChannel.TITLE, BroadcastChannel.ACTION_BAR));

        assertThat(cfg.channels()).containsExactlyInAnyOrder(BroadcastChannel.TITLE, BroadcastChannel.ACTION_BAR);
    }

    @Test
    void allowAndDenyAreDefensivelyCopied() {
        Set<String> allow = new HashSet<>(Set.of("minecraft:end/kill_dragon"));
        Set<String> deny = new HashSet<>(Set.of("minecraft:adventure/root"));
        AdvancementNoticeConfig cfg = config(allow, deny, Map.of(), Set.of(BroadcastChannel.CHAT));

        allow.add("minecraft:smuggled");
        deny.add("minecraft:smuggled");

        assertThat(cfg.allow()).containsExactly("minecraft:end/kill_dragon");
        assertThat(cfg.deny()).containsExactly("minecraft:adventure/root");
    }

    @Test
    void thePerAdvancementMapIsDefensivelyCopied() {
        Map<String, String> templates = new HashMap<>(Map.of("minecraft:end/kill_dragon", "override"));
        AdvancementNoticeConfig cfg = config(Set.of(), Set.of(), templates, Set.of(BroadcastChannel.CHAT));

        templates.put("minecraft:smuggled", "leak");

        assertThat(cfg.perAdvancementTemplates()).containsOnlyKeys("minecraft:end/kill_dragon");
    }

    @Test
    void allowDenyAndPerAdvancementKeysAreLowercased() {
        AdvancementNoticeConfig cfg = config(
                Set.of("MINECRAFT:END/KILL_DRAGON"),
                Set.of("Minecraft:Adventure/Root"),
                Map.of("MINECRAFT:Story/Mine_Stone", "override"),
                Set.of(BroadcastChannel.CHAT));

        assertThat(cfg.allow()).containsExactly("minecraft:end/kill_dragon");
        assertThat(cfg.deny()).containsExactly("minecraft:adventure/root");
        assertThat(cfg.perAdvancementTemplates()).containsOnlyKeys("minecraft:story/mine_stone");
    }

    private static AdvancementNoticeConfig config(
            Set<String> allow, Set<String> deny, Map<String, String> perAdvancement, Set<BroadcastChannel> channels) {
        return new AdvancementNoticeConfig(
                true, true, true, allow, deny, "{player} earned {title}", perAdvancement, channels, Optional.empty());
    }
}
