package com.uxplima.uxmessentials.vaults.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;

import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import org.junit.jupiter.api.Test;

/**
 * Parses {@code vaults.conf} blocks into {@link VaultSettings}: the item-blacklist policy built from the
 * {@code blacklist-materials} list (normalised case-insensitively, empty by default), and the {@code selector}
 * block (enabled by default, with the documented icon/row defaults and a tolerant unknown-material fallback).
 */
class VaultSettingsTest {

    @Test
    void anAbsentBlacklistAllowsEveryItem() {
        VaultSettings settings = new VaultSettings(new MapConfigStore(Map.of()));

        assertThat(settings.itemPolicy().blockedMaterials()).isEmpty();
        assertThat(settings.itemPolicy().isBlocked("BEDROCK")).isFalse();
    }

    @Test
    void aConfiguredBlacklistBlocksItsMaterialsCaseInsensitively() {
        Map<String, Object> values = new HashMap<>();
        values.put("blacklist-materials", List.of("Bedrock", "BARRIER"));
        VaultSettings settings = new VaultSettings(new MapConfigStore(values));

        assertThat(settings.itemPolicy().isBlocked("BEDROCK")).isTrue();
        assertThat(settings.itemPolicy().isBlocked("barrier")).isTrue();
        assertThat(settings.itemPolicy().isBlocked("diamond")).isFalse();
    }

    @Test
    void theSelectorIsEnabledWithDocumentedDefaults() {
        VaultSettings settings = new VaultSettings(new MapConfigStore(Map.of()));

        assertThat(settings.selectorEnabled()).isTrue();
        assertThat(settings.selectorSettings().layout().rows()).isEqualTo(3);
        assertThat(settings.selectorSettings().showLocked()).isTrue();
        assertThat(settings.selectorSettings().ownedIcon()).isEqualTo(Material.CHEST);
        assertThat(settings.selectorSettings().lockedIcon()).isEqualTo(Material.GRAY_STAINED_GLASS_PANE);
    }

    @Test
    void anUnknownSelectorIconFallsBackRatherThanFailing() {
        Map<String, Object> values = new HashMap<>();
        values.put("selector.owned-icon", "NOT_A_REAL_MATERIAL");
        values.put("selector.locked-icon", "");
        VaultSettings settings = new VaultSettings(new MapConfigStore(values));

        assertThat(settings.selectorSettings().ownedIcon()).isEqualTo(Material.CHEST);
        assertThat(settings.selectorSettings().lockedIcon()).isEqualTo(Material.GRAY_STAINED_GLASS_PANE);
    }

    @Test
    void disablingTheSelectorIsHonoured() {
        VaultSettings settings = new VaultSettings(new MapConfigStore(Map.of("selector.enabled", false)));

        assertThat(settings.selectorEnabled()).isFalse();
    }

    private static final class MapConfigStore implements ConfigStore {
        private final Map<String, Object> values;

        MapConfigStore(Map<String, Object> values) {
            this.values = Map.copyOf(values);
        }

        @Override
        public boolean getBoolean(String path, boolean fallback) {
            Object value = values.get(path);
            return value instanceof Boolean b ? b : fallback;
        }

        @Override
        public String getString(String path, String fallback) {
            Object value = values.get(path);
            return value instanceof String s ? s : fallback;
        }

        @Override
        public int getInt(String path, int fallback) {
            Object value = values.get(path);
            return value instanceof Integer i ? i : fallback;
        }

        @Override
        @SuppressWarnings("unchecked") // the test seeds only List<String> values at list paths
        public List<String> getStringList(String path, List<String> fallback) {
            Object value = values.get(path);
            return value instanceof List<?> list ? (List<String>) list : fallback;
        }
    }
}
