package com.uxplima.uxmessentials.holograms.adapter.inbound.gui;

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

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.ListPropertyLayout;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.Nullable;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

/**
 * The two sub-menu geometries the hologram editor needs beyond the standard
 * {@link com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityEditorLayout}: the {@link ListPropertyLayout}
 * for the line-list and blacklist sub-menus, and the enum-selector geometry (rows, option slots, option/filler
 * icons) for the billboard / alignment / visibility pickers. The framework's editor-layout loader only reads the
 * standard property-grid keys, so these extra keys are parsed here from the same
 * {@code modules/holograms/gui/hologram-editor.conf} (disk-first, then the bundled resource, then a code
 * default), keeping every slot and material operator-editable without a second conf file.
 *
 * @param listLayout the geometry of the line-list and blacklist sub-menus (a {@link ListPropertyLayout})
 * @param selectorRows the row count of an enum-selector sub-menu (1..6)
 * @param selectorSlots the slots an enum-selector draws its options into
 * @param selectorOptionIcon the per-option button material in an enum selector
 * @param selectorFiller the background filler material in an enum selector
 */
public record HologramEditorSubLayouts(
        ListPropertyLayout listLayout,
        int selectorRows,
        List<Integer> selectorSlots,
        Material selectorOptionIcon,
        Material selectorFiller) {

    public HologramEditorSubLayouts {
        Objects.requireNonNull(listLayout, "listLayout");
        selectorSlots = List.copyOf(Objects.requireNonNull(selectorSlots, "selectorSlots"));
        if (selectorRows < 1 || selectorRows > 6) {
            throw new IllegalArgumentException("selectorRows must be 1..6, was " + selectorRows);
        }
        if (selectorSlots.isEmpty()) {
            throw new IllegalArgumentException("selectorSlots must not be empty");
        }
        Objects.requireNonNull(selectorOptionIcon, "selectorOptionIcon");
        Objects.requireNonNull(selectorFiller, "selectorFiller");
    }

    /** The built-in geometry used when no conf is present or a key is missing. */
    public static HologramEditorSubLayouts codeDefault() {
        ListPropertyLayout list = new ListPropertyLayout(
                6,
                List.of(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34),
                48,
                50,
                Material.PAPER,
                Material.LIME_DYE,
                Material.ARROW,
                Material.BLACK_STAINED_GLASS_PANE);
        return new HologramEditorSubLayouts(
                list, 3, List.of(10, 11, 12, 13, 14, 15, 16), Material.PAPER, Material.BLACK_STAINED_GLASS_PANE);
    }

    /**
     * Resolve the sub-layouts for {@code module}/{@code name}, preferring an operator's on-disk edit, then the
     * bundled resource, then the code default. A missing file or unparsable key logs and falls back, so a typo
     * never stops the editor opening.
     */
    public static HologramEditorSubLayouts load(Path dataFolder, String module, String name, Logger log) {
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(log, "log");
        HologramEditorSubLayouts codeDefault = codeDefault();
        ConfigurationNode root = root(dataFolder, module, name, log);
        if (root == null) {
            return codeDefault;
        }
        ListPropertyLayout listDefault = codeDefault.listLayout();
        ListPropertyLayout list = new ListPropertyLayout(
                clampRows(root.node("lines-rows").getInt(listDefault.rows()), listDefault.rows(), log),
                intList(root.node("lines-entry-slots"), listDefault.entrySlots()),
                Math.max(0, root.node("lines-add-slot").getInt(listDefault.addSlot())),
                Math.max(0, root.node("lines-back-slot").getInt(listDefault.backSlot())),
                material(root.node("lines-entry-icon").getString(), listDefault.entryIcon(), log),
                material(root.node("lines-add-icon").getString(), listDefault.addIcon(), log),
                material(root.node("lines-back-icon").getString(), listDefault.backIcon(), log),
                material(root.node("lines-filler").getString(), listDefault.fillerIcon(), log));
        int selectorRows = clampRows(
                root.node("selector-rows").getInt(codeDefault.selectorRows()), codeDefault.selectorRows(), log);
        List<Integer> selectorSlots = intList(root.node("selector-option-slots"), codeDefault.selectorSlots());
        Material optionIcon =
                material(root.node("selector-option-icon").getString(), codeDefault.selectorOptionIcon(), log);
        Material filler = material(root.node("selector-filler").getString(), codeDefault.selectorFiller(), log);
        return new HologramEditorSubLayouts(list, selectorRows, selectorSlots, optionIcon, filler);
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
            if (HologramEditorSubLayouts.class.getClassLoader().getResource(resource) == null) {
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
            log.error("failed to load hologram editor sub-layout " + origin, failure);
            return null;
        }
    }

    private static int clampRows(int rows, int fallback, Logger log) {
        if (rows < 1 || rows > 6) {
            log.warn("hologram editor sub-layout rows {} out of range 1..6, using {}", rows, fallback);
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
            log.warn("hologram editor sub-layout material {} is unknown, using {}", raw, fallback);
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
        InputStream in = HologramEditorSubLayouts.class.getClassLoader().getResourceAsStream(resource);
        if (in == null) {
            throw new java.io.FileNotFoundException(resource);
        }
        return new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
    }
}
