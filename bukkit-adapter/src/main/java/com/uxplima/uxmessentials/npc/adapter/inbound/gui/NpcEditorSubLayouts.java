package com.uxplima.uxmessentials.npc.adapter.inbound.gui;

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
 * The three sub-menu geometries the NPC editor needs beyond the standard
 * {@link com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityEditorLayout}: the {@link EquipmentLayout} for
 * the per-slot equipment grid, the {@link ListPropertyLayout} for the click-action sub-menu, and the enum-selector
 * geometry (rows, option slots, option/filler icons) for the type / pose / glow-colour pickers. The framework's
 * editor-layout loader only reads the standard property-grid keys, so these extra keys are parsed here from the
 * same {@code modules/npc/gui/npc-editor.conf} (disk-first, then the bundled resource, then a code default),
 * keeping every slot and material operator-editable without a second conf file.
 *
 * @param equipment the geometry of the equipment sub-menu (per-slot buttons, back, icons)
 * @param actions the geometry of the click-action sub-menu (a {@link ListPropertyLayout})
 * @param selectorRows the row count of an enum-selector sub-menu (1..6)
 * @param selectorSlots the slots an enum-selector draws its options into
 * @param selectorOptionIcon the per-option button material in an enum selector
 * @param selectorFiller the background filler material in an enum selector
 */
public record NpcEditorSubLayouts(
        EquipmentLayout equipment,
        ListPropertyLayout actions,
        int selectorRows,
        List<Integer> selectorSlots,
        Material selectorOptionIcon,
        Material selectorFiller) {

    public NpcEditorSubLayouts {
        Objects.requireNonNull(equipment, "equipment");
        Objects.requireNonNull(actions, "actions");
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

    /**
     * The geometry of the equipment sub-menu: the five armour/main-hand slots are drawn in {@code armourSlots}
     * (head, chest, legs, feet, mainhand order), the off-hand in {@code offhandSlot}, with a back button and the
     * empty-slot/back/filler icons.
     *
     * @param rows the sub-menu row count, 1..6
     * @param armourSlots the slots the head/chest/legs/feet/mainhand buttons fill, in that order
     * @param offhandSlot the slot the off-hand button sits in
     * @param backSlot the slot the back button sits in
     * @param emptyIcon the material an empty slot button renders as
     * @param backIcon the back button material
     * @param fillerIcon the background filler material
     */
    public record EquipmentLayout(
            int rows,
            List<Integer> armourSlots,
            int offhandSlot,
            int backSlot,
            Material emptyIcon,
            Material backIcon,
            Material fillerIcon) {

        public EquipmentLayout {
            if (rows < 1 || rows > 6) {
                throw new IllegalArgumentException("rows must be 1..6, was " + rows);
            }
            armourSlots = List.copyOf(Objects.requireNonNull(armourSlots, "armourSlots"));
            if (armourSlots.size() != 5) {
                throw new IllegalArgumentException("armourSlots must hold exactly 5 slots, had " + armourSlots.size());
            }
            Objects.requireNonNull(emptyIcon, "emptyIcon");
            Objects.requireNonNull(backIcon, "backIcon");
            Objects.requireNonNull(fillerIcon, "fillerIcon");
        }

        /** The slot a list of all six equipment buttons is drawn into, in head..mainhand then off-hand order. */
        public List<Integer> allSlots() {
            List<Integer> slots = new ArrayList<>(armourSlots);
            slots.add(offhandSlot);
            return List.copyOf(slots);
        }
    }

    /** The built-in geometry used when no conf is present or a key is missing. */
    public static NpcEditorSubLayouts codeDefault() {
        EquipmentLayout equipment = new EquipmentLayout(
                3,
                List.of(11, 12, 13, 14, 15),
                16,
                22,
                Material.ITEM_FRAME,
                Material.ARROW,
                Material.BLACK_STAINED_GLASS_PANE);
        ListPropertyLayout actions = new ListPropertyLayout(
                6,
                List.of(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34),
                48,
                50,
                Material.PAPER,
                Material.LIME_DYE,
                Material.ARROW,
                Material.BLACK_STAINED_GLASS_PANE);
        return new NpcEditorSubLayouts(
                equipment,
                actions,
                6,
                List.of(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34),
                Material.PAPER,
                Material.BLACK_STAINED_GLASS_PANE);
    }

    /**
     * Resolve the sub-layouts for {@code module}/{@code name}, preferring an operator's on-disk edit, then the
     * bundled resource, then the code default. A missing file or unparsable key logs and falls back, so a typo
     * never stops the editor opening.
     */
    public static NpcEditorSubLayouts load(Path dataFolder, String module, String name, Logger log) {
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(log, "log");
        NpcEditorSubLayouts codeDefault = codeDefault();
        ConfigurationNode root = root(dataFolder, module, name, log);
        if (root == null) {
            return codeDefault;
        }
        EquipmentLayout equipment = equipmentLayout(root, codeDefault.equipment(), log);
        ListPropertyLayout actions = actionsLayout(root, codeDefault.actions(), log);
        int selectorRows = clampRows(
                root.node("selector-rows").getInt(codeDefault.selectorRows()), codeDefault.selectorRows(), log);
        List<Integer> selectorSlots = intList(root.node("selector-option-slots"), codeDefault.selectorSlots());
        Material optionIcon =
                material(root.node("selector-option-icon").getString(), codeDefault.selectorOptionIcon(), log);
        Material filler = material(root.node("selector-filler").getString(), codeDefault.selectorFiller(), log);
        return new NpcEditorSubLayouts(equipment, actions, selectorRows, selectorSlots, optionIcon, filler);
    }

    private static EquipmentLayout equipmentLayout(ConfigurationNode root, EquipmentLayout fallback, Logger log) {
        List<Integer> armour = intList(root.node("equipment-slots"), fallback.armourSlots());
        if (armour.size() != 5) {
            log.warn("npc editor equipment-slots needs exactly 5 slots, using the default");
            armour = fallback.armourSlots();
        }
        return new EquipmentLayout(
                clampRows(root.node("equipment-rows").getInt(fallback.rows()), fallback.rows(), log),
                armour,
                root.node("equipment-offhand-slot").getInt(fallback.offhandSlot()),
                root.node("equipment-back-slot").getInt(fallback.backSlot()),
                material(root.node("equipment-empty-icon").getString(), fallback.emptyIcon(), log),
                material(root.node("equipment-back-icon").getString(), fallback.backIcon(), log),
                material(root.node("equipment-filler").getString(), fallback.fillerIcon(), log));
    }

    private static ListPropertyLayout actionsLayout(ConfigurationNode root, ListPropertyLayout fallback, Logger log) {
        return new ListPropertyLayout(
                clampRows(root.node("actions-rows").getInt(fallback.rows()), fallback.rows(), log),
                intList(root.node("actions-entry-slots"), fallback.entrySlots()),
                Math.max(0, root.node("actions-add-slot").getInt(fallback.addSlot())),
                Math.max(0, root.node("actions-back-slot").getInt(fallback.backSlot())),
                material(root.node("actions-entry-icon").getString(), fallback.entryIcon(), log),
                material(root.node("actions-add-icon").getString(), fallback.addIcon(), log),
                material(root.node("actions-back-icon").getString(), fallback.backIcon(), log),
                material(root.node("actions-filler").getString(), fallback.fillerIcon(), log));
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
            if (NpcEditorSubLayouts.class.getClassLoader().getResource(resource) == null) {
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
            log.error("failed to load npc editor sub-layout " + origin, failure);
            return null;
        }
    }

    private static int clampRows(int rows, int fallback, Logger log) {
        if (rows < 1 || rows > 6) {
            log.warn("npc editor sub-layout rows {} out of range 1..6, using {}", rows, fallback);
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
            log.warn("npc editor sub-layout material {} is unknown, using {}", raw, fallback);
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
        InputStream in = NpcEditorSubLayouts.class.getClassLoader().getResourceAsStream(resource);
        if (in == null) {
            throw new java.io.FileNotFoundException(resource);
        }
        return new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
    }
}
