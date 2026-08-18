package com.uxplima.uxmessentials.skin.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import org.junit.jupiter.api.Test;

/** The typed view of {@code modules/skin/config.conf}, and the defaults an operator who deletes a line falls to. */
class SkinConfigTest {

    @Test
    void anEmptyStoreYieldsTheShippedDefaults() {
        SkinConfig config = SkinConfig.from(new FixedConfig(Map.of()));

        assertThat(config.enabled()).isFalse();
        assertThat(config.nameSource()).isTrue();
        assertThat(config.urlSource()).isTrue();
        assertThat(config.fileSource()).isTrue();
        assertThat(config.bedrockSource()).isTrue();
        assertThat(config.premiumSkin()).isTrue();
        assertThat(config.defaultPool()).isEmpty();
        assertThat(config.loginTimeout()).isEqualTo(Duration.ofSeconds(3));
        assertThat(config.bedrockRefreshOnJoin()).isTrue();
        assertThat(config.bedrockRetries()).isEqualTo(2);
        assertThat(config.cooldown()).isEqualTo(Duration.ofSeconds(30));
        assertThat(config.blockedSkins()).isEmpty();
        assertThat(config.allowedUrlHosts()).containsExactly("i.imgur.com", "textures.minecraft.net");
        assertThat(config.mineskinApiKey()).isEmpty();
        assertThat(config.skinFolder()).isEqualTo("skins");
    }

    @Test
    void everyKnobIsReadFromItsOwnKey() {
        SkinConfig config = SkinConfig.from(new FixedConfig(Map.of(
                "enabled",
                true,
                "sources.url",
                false,
                "login.premium-skin",
                false,
                "login.timeout-seconds",
                7,
                "bedrock.retries",
                5,
                "limits.cooldown-seconds",
                0,
                "mineskin.folder",
                "faces")));

        assertThat(config.enabled()).isTrue();
        assertThat(config.urlSource()).isFalse();
        assertThat(config.premiumSkin()).isFalse();
        assertThat(config.loginTimeout()).isEqualTo(Duration.ofSeconds(7));
        assertThat(config.bedrockRetries()).isEqualTo(5);
        assertThat(config.cooldown()).isZero();
        assertThat(config.skinFolder()).isEqualTo("faces");
    }

    @Test
    void aNegativeNumberIsClampedRatherThanTrusted() {
        // A negative timeout would make every login lookup expire instantly and a negative retry count would
        // loop; both are operator typos, so they read as the nearest sane value.
        SkinConfig config = SkinConfig.from(new FixedConfig(
                Map.of("login.timeout-seconds", -1, "bedrock.retries", -3, "limits.cooldown-seconds", -5)));

        assertThat(config.loginTimeout()).isZero();
        assertThat(config.bedrockRetries()).isZero();
        assertThat(config.cooldown()).isZero();
    }

    @Test
    void theConfigHandsOutThePolicyItsLimitsDescribe() {
        SkinConfig config = SkinConfig.from(new FixedConfig(Map.of("limits.blocked-skins", List.of("Herobrine"))));

        assertThat(config.policy().blocked("herobrine")).isTrue();
    }

    /** A store answering the paths it was given and the caller's fallback for everything else. */
    private record FixedConfig(Map<String, Object> values) implements ConfigStore {
        @Override
        public boolean getBoolean(String path, boolean fallback) {
            return values.get(path) instanceof Boolean value ? value : fallback;
        }

        @Override
        public String getString(String path, String fallback) {
            return values.get(path) instanceof String value ? value : fallback;
        }

        @Override
        public int getInt(String path, int fallback) {
            return values.get(path) instanceof Integer value ? value : fallback;
        }

        @Override
        public List<String> getStringList(String path, List<String> fallback) {
            return values.get(path) instanceof List<?> value
                    ? value.stream().map(String::valueOf).toList()
                    : List.copyOf(fallback);
        }
    }
}
