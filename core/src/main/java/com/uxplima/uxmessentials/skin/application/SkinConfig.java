package com.uxplima.uxmessentials.skin.application;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.skin.domain.SkinPolicy;
import org.jspecify.annotations.NullMarked;

/**
 * The typed, immutable view of {@code modules/skin/config.conf}: the enable gate, which sources a player may take
 * a skin from, what a player who has chosen nothing is dressed with, how Bedrock skins are read, the limits on
 * changing a skin, and the skin service that signs an upload. Resolved once when the module starts and, per the
 * atomic-reload rule, swapped whole on reload, so a lookup mid-reload sees one coherent snapshot.
 *
 * <p>The HOCON keys are kebab-case ({@code login.premium-skin}, {@code limits.cooldown-seconds}); every knob
 * carries the default the bundled config ships, so an operator who deletes a line falls back to the shipped value.
 * A negative number is clamped to zero rather than trusted: a negative timeout would expire every login lookup
 * before it began.
 *
 * @param enabled the module enable gate ({@code enabled}, default {@code false})
 * @param nameSource whether /skin &lt;name&gt; is allowed ({@code sources.name}, default true)
 * @param urlSource whether /skin url is allowed ({@code sources.url}, default true)
 * @param fileSource whether /skin file is allowed ({@code sources.file}, default true)
 * @param bedrockSource whether a Bedrock player's own skin is applied ({@code sources.bedrock}, default true)
 * @param premiumSkin whether a paid account's real skin is applied ({@code login.premium-skin}, default true)
 * @param defaultPool the names an undressed player is dressed from ({@code login.default-pool}, default empty)
 * @param loginTimeout how long a login lookup may take ({@code login.timeout-seconds}, default 3)
 * @param bedrockRefreshOnJoin whether a Bedrock skin is re-read on every join ({@code bedrock.refresh-on-join})
 * @param bedrockRetries retries for a failed Bedrock lookup ({@code bedrock.retries}, default 2)
 * @param cooldown the wait between one change and the next ({@code limits.cooldown-seconds}, default 30)
 * @param blockedSkins names nobody may wear ({@code limits.blocked-skins}, default empty)
 * @param allowedUrlHosts the hosts /skin url accepts ({@code limits.allowed-url-hosts}; empty allows any)
 * @param mineskinApiKey the optional MineSkin key ({@code mineskin.api-key}, default empty)
 * @param skinFolder the folder /skin file reads from ({@code mineskin.folder}, default {@code skins})
 */
@NullMarked
public record SkinConfig(
        boolean enabled,
        boolean nameSource,
        boolean urlSource,
        boolean fileSource,
        boolean bedrockSource,
        boolean premiumSkin,
        List<String> defaultPool,
        Duration loginTimeout,
        boolean bedrockRefreshOnJoin,
        int bedrockRetries,
        Duration cooldown,
        List<String> blockedSkins,
        List<String> allowedUrlHosts,
        String mineskinApiKey,
        String skinFolder) {

    private static final int DEFAULT_LOGIN_TIMEOUT_SECONDS = 3;
    private static final int DEFAULT_BEDROCK_RETRIES = 2;
    private static final int DEFAULT_COOLDOWN_SECONDS = 30;
    private static final List<String> DEFAULT_URL_HOSTS = List.of("i.imgur.com", "textures.minecraft.net");
    private static final String DEFAULT_FOLDER = "skins";

    public SkinConfig {
        defaultPool = List.copyOf(Objects.requireNonNull(defaultPool, "defaultPool"));
        blockedSkins = List.copyOf(Objects.requireNonNull(blockedSkins, "blockedSkins"));
        allowedUrlHosts = List.copyOf(Objects.requireNonNull(allowedUrlHosts, "allowedUrlHosts"));
        Objects.requireNonNull(loginTimeout, "loginTimeout");
        Objects.requireNonNull(cooldown, "cooldown");
        Objects.requireNonNull(mineskinApiKey, "mineskinApiKey");
        Objects.requireNonNull(skinFolder, "skinFolder");
    }

    /** Resolve the skin config from the module's scoped {@link ConfigStore} ({@code modules.skin}). */
    public static SkinConfig from(ConfigStore config) {
        Objects.requireNonNull(config, "config");
        return new SkinConfig(
                config.getBoolean("enabled", false),
                config.getBoolean("sources.name", true),
                config.getBoolean("sources.url", true),
                config.getBoolean("sources.file", true),
                config.getBoolean("sources.bedrock", true),
                config.getBoolean("login.premium-skin", true),
                config.getStringList("login.default-pool", List.of()),
                seconds(config.getInt("login.timeout-seconds", DEFAULT_LOGIN_TIMEOUT_SECONDS)),
                config.getBoolean("bedrock.refresh-on-join", true),
                Math.max(0, config.getInt("bedrock.retries", DEFAULT_BEDROCK_RETRIES)),
                seconds(config.getInt("limits.cooldown-seconds", DEFAULT_COOLDOWN_SECONDS)),
                config.getStringList("limits.blocked-skins", List.of()),
                config.getStringList("limits.allowed-url-hosts", DEFAULT_URL_HOSTS),
                config.getString("mineskin.api-key", ""),
                config.getString("mineskin.folder", DEFAULT_FOLDER));
    }

    /** The shipped defaults, as a starting point for a test or a module started without a config file. */
    public static SkinConfig defaults() {
        return from(new ConfigStore() {
            @Override
            public boolean getBoolean(String path, boolean fallback) {
                return fallback;
            }

            @Override
            public String getString(String path, String fallback) {
                return fallback;
            }

            @Override
            public int getInt(String path, int fallback) {
                return fallback;
            }
        });
    }

    /** The pure rules these limits describe. */
    public SkinPolicy policy() {
        return new SkinPolicy(blockedSkins, allowedUrlHosts, defaultPool);
    }

    /** This config with a different blocked-skin list. */
    public SkinConfig withBlockedSkins(List<String> blocked) {
        return new SkinConfig(
                enabled,
                nameSource,
                urlSource,
                fileSource,
                bedrockSource,
                premiumSkin,
                defaultPool,
                loginTimeout,
                bedrockRefreshOnJoin,
                bedrockRetries,
                cooldown,
                blocked,
                allowedUrlHosts,
                mineskinApiKey,
                skinFolder);
    }

    /** This config with a different url allowlist. */
    public SkinConfig withAllowedUrlHosts(List<String> hosts) {
        return new SkinConfig(
                enabled,
                nameSource,
                urlSource,
                fileSource,
                bedrockSource,
                premiumSkin,
                defaultPool,
                loginTimeout,
                bedrockRefreshOnJoin,
                bedrockRetries,
                cooldown,
                blockedSkins,
                hosts,
                mineskinApiKey,
                skinFolder);
    }

    /** This config with a different default pool. */
    public SkinConfig withDefaultPool(List<String> pool) {
        return new SkinConfig(
                enabled,
                nameSource,
                urlSource,
                fileSource,
                bedrockSource,
                premiumSkin,
                pool,
                loginTimeout,
                bedrockRefreshOnJoin,
                bedrockRetries,
                cooldown,
                blockedSkins,
                allowedUrlHosts,
                mineskinApiKey,
                skinFolder);
    }

    private static Duration seconds(int value) {
        return Duration.ofSeconds(Math.max(0, value));
    }
}
