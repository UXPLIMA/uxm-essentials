package com.uxplima.uxmessentials.module;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.uxplima.uxmessentials.bootstrap.di.DefaultModuleRegistry;
import com.uxplima.uxmessentials.shared.application.module.FeatureModule;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import org.junit.jupiter.api.Test;

/**
 * Guards what a fresh install actually starts with.
 *
 * <p>A module's on/off default lives in two places that must agree: the code fallback in its {@code
 * enabled(ConfigStore)} gate, which decides what happens when a key is absent, and the {@code enabled = }
 * line in the per-module {@code config.conf} resource an install writes on first run. Change one and
 * forget the other and the plugin behaves differently before and after an operator touches the file, which is
 * the kind of bug nobody reports because it looks like they broke it themselves.
 *
 * <p>The second test pins the list of modules that ship off. Turning one on or off is a deliberate decision
 * about the out-of-the-box experience, so it should never happen as a side effect of editing a module.
 */
class ShippedModuleDefaultsDriftTest {

    /**
     * The modules a fresh install starts with OFF. Three groups, each for its own reason: the HUD trio
     * (scoreboard, tablist, nametags) rewrites what every player sees and collides with a dedicated HUD
     * plugin; survival and villagers change vanilla gameplay rules; discordlink, vote and regions do nothing
     * without an external service (a bot, Votifier, WorldGuard); invrollback writes a snapshot on every death
     * and logout, a cost only a server that restores inventories should pay.
     */
    private static final Set<String> SHIPS_DISABLED = Set.of(
            "scoreboard",
            "tablist",
            "nametags",
            "survival",
            "villagers",
            "discordlink",
            "vote",
            "regions",
            "invrollback");

    /** The single column-0 {@code enabled} switch at the head of a module's shipped config. */
    private static final Pattern ENABLED_LINE = Pattern.compile("^enabled\\s*=\\s*(true|false)", Pattern.MULTILINE);

    @Test
    void everyModuleShipsAConfigWhoseSwitchMatchesItsCodeDefault() throws Exception {
        DefaultModuleRegistry registry = new DefaultModuleRegistry();
        for (FeatureModule module : registry.all()) {
            String id = module.id().value();
            Path config = resources().resolve("modules").resolve(id).resolve("config.conf");
            assertThat(config).as(id + " ships a config resource").isRegularFile();

            Matcher matcher = ENABLED_LINE.matcher(Files.readString(config));
            assertThat(matcher.find())
                    .as(id + " config carries a top-level enabled switch")
                    .isTrue();
            assertThat(Boolean.parseBoolean(matcher.group(1)))
                    .as(id + ": the shipped enabled switch and the code default must agree")
                    .isEqualTo(module.enabled(EMPTY_CONFIG));
        }
    }

    @Test
    void exactlyTheDocumentedModulesShipDisabled() {
        DefaultModuleRegistry registry = new DefaultModuleRegistry();
        Set<String> off = new TreeSet<>();
        for (FeatureModule module : registry.all()) {
            if (!module.enabled(EMPTY_CONFIG)) {
                off.add(module.id().value());
            }
        }

        assertThat(off).containsExactlyInAnyOrderElementsOf(SHIPS_DISABLED);
    }

    /** A config with nothing in it, so every gate falls back to its code default. */
    private static final ConfigStore EMPTY_CONFIG = new ConfigStore() {
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
    };

    private static Path resources() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            if (Files.exists(dir.resolve("settings.gradle.kts"))) {
                return dir.resolve("bukkit-adapter/src/main/resources");
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("could not locate the repo root (settings.gradle.kts)");
    }
}
