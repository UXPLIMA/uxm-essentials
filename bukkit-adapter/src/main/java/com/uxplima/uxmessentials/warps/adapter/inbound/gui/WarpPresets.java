package com.uxplima.uxmessentials.warps.adapter.inbound.gui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Material;

import com.uxplima.uxmessentials.shared.adapter.outbound.config.ConfigurateConfigStore;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.NullMarked;

/**
 * The particles and sounds the warp editor's two pickers offer, read from {@code selector-presets} in the warps
 * file. Until 2026-09-23 both lists were written in the two views with English names, so an operator could not
 * change a picker and a Turkish player read "Dragon Breath".
 *
 * <p>A preset's name is written once per language code, and a reader whose language has none reads the English
 * name, then the effect's own key. A file with no {@code selector-presets} section, which is every file written
 * before the section existed, offers the presets the plugin ships. An empty list offers none, which leaves the
 * picker's custom button. Each kind is a list, because a list keeps the order the operator wrote.
 */
@NullMarked
public final class WarpPresets {

    static final String SECTION = "selector-presets";

    private static final String BUNDLED = "modules/warps/config.conf";
    private static final String ENGLISH = "en";

    private final List<Preset> particles;
    private final List<Preset> sounds;

    private WarpPresets(List<Preset> particles, List<Preset> sounds) {
        this.particles = List.copyOf(particles);
        this.sounds = List.copyOf(sounds);
    }

    /** The presets of {@code config}, or of {@code shipped} where the operator's file has no such section. */
    public static WarpPresets read(ConfigStore config, WarpPresets shipped, Logger log) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(shipped, "shipped");
        Objects.requireNonNull(log, "log");
        List<String> kinds = config.getKeys(SECTION);
        return new WarpPresets(
                kinds.contains("particles") ? list(config, "particles", log) : shipped.particles,
                kinds.contains("sounds") ? list(config, "sounds", log) : shipped.sounds);
    }

    /** The presets the plugin ships, read from the warps file inside the jar. */
    public static WarpPresets bundled(Logger log) {
        Objects.requireNonNull(log, "log");
        ConfigStore shipped = ConfigurateConfigStore.bundled(BUNDLED, log);
        return new WarpPresets(list(shipped, "particles", log), list(shipped, "sounds", log));
    }

    public List<Preset> particles() {
        return particles;
    }

    public List<Preset> sounds() {
        return sounds;
    }

    private static List<Preset> list(ConfigStore config, String kind, Logger log) {
        String base = SECTION + "." + kind;
        List<Preset> presets = new ArrayList<>();
        int count = config.getListSize(base);
        for (int index = 0; index < count; index++) {
            String at = base + "." + index;
            String effect = config.getString(at + ".effect", "").strip();
            Material icon = Material.matchMaterial(config.getString(at + ".icon", ""));
            if (effect.isEmpty() || icon == null || !icon.isItem()) {
                log.warn(
                        "warps: {} entry {} needs an effect and an item icon, so the picker leaves it out",
                        base,
                        index + 1);
                continue;
            }
            Map<String, String> names = new LinkedHashMap<>();
            for (String language : config.getKeys(at + ".name")) {
                names.put(language, config.getString(at + ".name." + language, ""));
            }
            presets.add(new Preset(effect, icon, names));
        }
        return presets;
    }

    /** One entry of a picker: the effect it sets, the item it shows, and its name in each language. */
    public record Preset(String effect, Material icon, Map<String, String> names) {

        public Preset {
            Objects.requireNonNull(effect, "effect");
            Objects.requireNonNull(icon, "icon");
            names = Map.copyOf(names);
        }

        /** The name a reader of {@code language} sees: their own, else the English one, else the effect's key. */
        public String nameIn(String language) {
            Objects.requireNonNull(language, "language");
            String own = names.get(language);
            if (own != null && !own.isBlank()) {
                return own;
            }
            String english = names.get(ENGLISH);
            return english != null && !english.isBlank() ? english : effect;
        }
    }
}
