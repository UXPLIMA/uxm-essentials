package com.uxplima.uxmessentials.communication.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.uxplima.uxmessentials.communication.domain.AdvancementNoticeConfig;
import com.uxplima.uxmessentials.shared.display.BroadcastChannel;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

/**
 * Parse coverage of the {@code advancements} block in {@link CommunicationContentCodec}: an authored block maps to
 * the right {@link AdvancementNoticeConfig} (flags, allow/deny, template, per-advancement overrides, channels,
 * sound), an absent block yields {@link AdvancementNoticeConfig#disabled()}, an unknown channel falls back to
 * {@code CHAT}, and the keys lowercase through the domain record.
 */
class AdvancementNoticeCodecTest {

    @Test
    void anAbsentBlockIsDisabled() {
        AdvancementNoticeConfig cfg = parse("").advancements();

        assertThat(cfg).isEqualTo(AdvancementNoticeConfig.disabled());
        assertThat(cfg.enabled()).isFalse();
    }

    @Test
    void anAuthoredBlockMapsEveryField() {
        AdvancementNoticeConfig cfg = parse(
                        """
                        advancements {
                          enabled = true
                          exclude-recipes = false
                          only-announceable = false
                          allow = [ "minecraft:end/kill_dragon", "minecraft:nether/all_potions" ]
                          deny = [ "minecraft:husbandry/root" ]
                          template = "<yellow>{player} earned [{title}]"
                          channels = [ "CHAT", "ACTION_BAR", "bogus" ]
                          sound = "entity.player.levelup"
                          per-advancement {
                            "minecraft:end/kill_dragon" = "<gold>{player} slew the dragon!"
                          }
                        }
                        """)
                .advancements();

        assertThat(cfg.enabled()).isTrue();
        assertThat(cfg.excludeRecipes()).isFalse();
        assertThat(cfg.onlyAnnounceable()).isFalse();
        assertThat(cfg.allow()).containsExactlyInAnyOrder("minecraft:end/kill_dragon", "minecraft:nether/all_potions");
        assertThat(cfg.deny()).containsExactly("minecraft:husbandry/root");
        assertThat(cfg.template()).isEqualTo("<yellow>{player} earned [{title}]");
        // "bogus" is dropped, the two valid channels are kept.
        assertThat(cfg.channels()).containsExactlyInAnyOrder(BroadcastChannel.CHAT, BroadcastChannel.ACTION_BAR);
        assertThat(cfg.sound()).contains("entity.player.levelup");
        assertThat(cfg.perAdvancementTemplates())
                .containsEntry("minecraft:end/kill_dragon", "<gold>{player} slew the dragon!");
    }

    @Test
    void anEnabledBlockWithNoChannelsDefaultsToChat() {
        AdvancementNoticeConfig cfg = parse(
                        """
                        advancements {
                          enabled = true
                          template = "{player} {title}"
                        }
                        """)
                .advancements();

        assertThat(cfg.channels()).containsExactly(BroadcastChannel.CHAT);
        assertThat(cfg.sound()).isEmpty();
    }

    private static CommunicationContent parse(String hocon) {
        try {
            ConfigurationNode node = HoconConfigurationLoader.builder().buildAndLoadString(hocon);
            return CommunicationContentCodec.read(node);
        } catch (ConfigurateException failure) {
            throw new AssertionError("failed to parse test HOCON", failure);
        }
    }
}
