package com.uxplima.uxmessentials.playerwarps.adapter.inbound.gui;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.bukkit.Material;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.Nullable;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

/**
 * The enum-selector geometry the player-warp editor's {@code EnumProperty} buttons (the visibility picker) draw
 * into, beyond the standard {@link com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityEditorLayout}. The
 * framework's editor-layout loader only reads the property-grid keys, so these extra keys are parsed here from
 * the same {@code modules/playerwarps/gui/pwarp-editor.conf} (disk-first, then the bundled resource, then a code
 * default), keeping every slot and material operator-editable without a second conf file. Mirrors the NPC
 * editor's sub-layout loader, trimmed to just the selector keys this editor needs.
 *
 * @param selectorRows the row count of an enum-selector sub-menu (1..6)
 * @param selectorSlots the slots an enum-selector draws its options into
 * @param selectorOptionIcon the per-option button material in an enum selector
 * @param selectorFiller the background filler material in an enum selector
 */
public record PlayerWarpEditorSubLayouts(
        int selectorRows, List<Integer> selectorSlots, Material selectorOptionIcon, Material selectorFiller) {

    public PlayerWarpEditorSubLayouts {
        if (selectorRows < 1 || selectorRows > 6) {
            throw new IllegalArgumentException("selectorRows must be 1..6, was " + selectorRows);
        }
        selectorSlots = List.copyOf(Objects.requireNonNull(selectorSlots, "selectorSlots"));
        if (selectorSlots.isEmpty()) {
            throw new IllegalArgumentException("selectorSlots must not be empty");
        }
        Objects.requireNonNull(selectorOptionIcon, "selectorOptionIcon");
        Objects.requireNonNull(selectorFiller, "selectorFiller");
    }

    /** The built-in geometry used when no conf is present or a key is missing. */
    public static PlayerWarpEditorSubLayouts codeDefault() {
        return new PlayerWarpEditorSubLayouts(
                3, List.of(11, 12, 13, 14, 15), Material.PAPER, Material.BLACK_STAINED_GLASS_PANE);
    }

    /**
     * Resolve the sub-layouts for {@code module}/{@code name}, preferring an operator's on-disk edit, then the
     * bundled resource, then the code default. A missing file or unparsable key logs and falls back, so a typo
     * never stops the editor opening.
     */
    public static PlayerWarpEditorSubLayouts load(Path dataFolder, String module, String name, Logger log) {
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(log, "log");
        PlayerWarpEditorSubLayouts codeDefault = codeDefault();
        ConfigurationNode root = root(dataFolder, module, name, log);
        if (root == null) {
            return codeDefault;
        }
        int selectorRows = clampRows(
                root.node("selector-rows").getInt(codeDefault.selectorRows()), codeDefault.selectorRows(), log);
        List<Integer> selectorSlots = intList(root.node("selector-option-slots"), codeDefault.selectorSlots());
        Material optionIcon =
                material(root.node("selector-option-icon").getString(), codeDefault.selectorOptionIcon(), log);
        Material filler = material(root.node("selector-filler").getString(), codeDefault.selectorFiller(), log);
        return new PlayerWarpEditorSubLayouts(selectorRows, selectorSlots, optionIcon, filler);
    }

    private static @Nullable ConfigurationNode root(Path dataFolder, String module, String name, Logger log) {
        Path onDisk =
                dataFolder.resolve("modules").resolve(module).resolve("gui").resolve(name + ".conf");
        HoconConfigurationLoader loader;
        String origin;
        if (Files.isRegularFile(onDisk)) {
            loader = HoconConfigurationLoader.builder().path(onDisk).build();
            origin = onDisk.toString();
        } else {
            String resource = "modules/" + module + "/gui/" + name + ".conf";
            if (PlayerWarpEditorSubLayouts.class.getClassLoader().getResource(resource) == null) {
                return null;
            }
            loader = HoconConfigurationLoader.builder()
                    .source(() -> openReader(resource))
                    .build();
            origin = resource;
        }
        try {
            return loader.load();
        } catch (ConfigurateException failure) {
            log.error("failed to load playerwarp editor sub-layout " + origin, failure);
            return null;
        }
    }

    private static int clampRows(int rows, int fallback, Logger log) {
        if (rows < 1 || rows > 6) {
            log.warn("playerwarp editor sub-layout rows {} out of range 1..6, using {}", rows, fallback);
            return fallback;
        }
        return rows;
    }

    private static Material material(@Nullable String raw, Material fallback, Logger log) {
        if (raw == null) {
            return fallback;
        }
        Material matched = Material.matchMaterial(raw);
        if (matched == null) {
            log.warn("playerwarp editor sub-layout material {} is unknown, using {}", raw, fallback);
            return fallback;
        }
        return matched;
    }

    private static List<Integer> intList(ConfigurationNode node, List<Integer> fallback) {
        if (node.virtual() || node.empty()) {
            return fallback;
        }
        List<Integer> values = new ArrayList<>();
        for (ConfigurationNode child : node.childrenList()) {
            values.add(child.getInt());
        }
        return values.isEmpty() ? fallback : values;
    }

    private static BufferedReader openReader(String resource) throws java.io.IOException {
        InputStream in = PlayerWarpEditorSubLayouts.class.getClassLoader().getResourceAsStream(resource);
        if (in == null) {
            throw new java.io.FileNotFoundException(resource);
        }
        return new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
    }
}
