package com.uxplima.uxmessentials.shared.adapter.inbound.gui;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Material;

import com.uxplima.uxmessentials.homes.adapter.inbound.gui.HomeActionsLayout;
import com.uxplima.uxmessentials.homes.adapter.inbound.gui.HomeListLayout;
import com.uxplima.uxmessentials.homes.adapter.inbound.gui.IconSelectorLayout;
import com.uxplima.uxmessentials.homes.adapter.inbound.gui.InvitesMenuLayout;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.NullMarked;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

/**
 * Loads a {@link GuiLayout} for a menu, preferring an operator's edit on disk over the bundled default. The
 * resolution mirrors the message-catalog loader: {@code <dataFolder>/modules/<module>/gui/<name>.conf} on
 * disk is read first so an operator's edit takes effect, else the bundled classpath resource
 * {@code modules/<module>/gui/<name>.conf}, else the code default handed in by the caller. A malformed or
 * missing file never throws — it logs and falls back — so a typo in a layout file can never stop a menu from
 * opening.
 *
 * <p>The conf holds layout integers and {@link Material} names only, never localised text. Every material name
 * is resolved through {@link Material#matchMaterial} once here at load time, never on the menu's open path; an
 * unknown name falls back to the corresponding default material so a typo degrades gracefully.
 */
@NullMarked
public final class GuiLayouts {

    private final Path dataFolder;
    private final Logger log;

    public GuiLayouts(Path dataFolder, Logger log) {
        this.dataFolder = Objects.requireNonNull(dataFolder, "dataFolder");
        this.log = Objects.requireNonNull(log, "log");
    }

    /**
     * Resolve the layout for {@code module}/{@code name}, falling back to {@code codeDefault} when no conf is
     * present or a conf cannot be parsed.
     */
    public GuiLayout load(String module, String name, GuiLayout codeDefault) {
        Objects.requireNonNull(module, "module");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(codeDefault, "codeDefault");
        Path onDisk =
                dataFolder.resolve("modules").resolve(module).resolve("gui").resolve(name + ".conf");
        if (Files.isRegularFile(onDisk)) {
            return parse(HoconConfigurationLoader.builder().path(onDisk).build(), onDisk.toString(), codeDefault);
        }
        String resource = "modules/" + module + "/gui/" + name + ".conf";
        if (getClass().getClassLoader().getResource(resource) == null) {
            return codeDefault;
        }
        return parse(
                HoconConfigurationLoader.builder()
                        .source(() -> openReader(resource))
                        .build(),
                resource,
                codeDefault);
    }

    private GuiLayout parse(HoconConfigurationLoader loader, String origin, GuiLayout codeDefault) {
        ConfigurationNode root;
        try {
            root = loader.load();
        } catch (ConfigurateException failure) {
            log.error("failed to load gui layout " + origin, failure);
            return codeDefault;
        }
        int rows = clampRows(root.node("rows").getInt(codeDefault.rows()), codeDefault.rows());
        Material fallbackIcon = material(root.node("fallback-icon").getString(), codeDefault.fallbackIcon());
        Material navIcon = material(root.node("nav-icon").getString(), codeDefault.navIcon());
        int prevSlot = Math.max(0, root.node("prev-slot").getInt(codeDefault.prevSlot()));
        int nextSlot = Math.max(0, root.node("next-slot").getInt(codeDefault.nextSlot()));
        List<Integer> contentSlots = contentSlots(root, codeDefault.contentSlots());
        return new GuiLayout(rows, fallbackIcon, navIcon, prevSlot, nextSlot, contentSlots);
    }

    private int clampRows(int rows, int fallback) {
        if (rows < 1 || rows > 6) {
            log.warn("gui layout rows {} out of range 1..6, using {}", rows, fallback);
            return fallback;
        }
        return rows;
    }

    private Material material(@org.jspecify.annotations.Nullable String name, Material fallback) {
        if (name == null) {
            return fallback;
        }
        Material matched = Material.matchMaterial(name);
        if (matched == null) {
            log.warn("gui layout material {} is unknown, using {}", name, fallback);
            return fallback;
        }
        return matched;
    }

    private List<Integer> contentSlots(ConfigurationNode root, List<Integer> fallback) {
        ConfigurationNode node = root.node("content-slots");
        if (node.virtual() || node.empty()) {
            return fallback;
        }
        List<Integer> slots = new ArrayList<>();
        for (ConfigurationNode child : node.childrenList()) {
            slots.add(child.getInt());
        }
        return slots;
    }

    /**
     * Resolve a fixed-action menu layout, overriding the {@code codeDefault}'s rows, filler, and per-element
     * slot/material from the conf where present. Resolution mirrors {@link #load}; a missing file or unparsable
     * key falls back to the code default so a typo never stops a menu opening.
     */
    public FixedMenuLayout loadFixedMenu(String module, String name, FixedMenuLayout codeDefault) {
        Objects.requireNonNull(module, "module");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(codeDefault, "codeDefault");
        Path onDisk =
                dataFolder.resolve("modules").resolve(module).resolve("gui").resolve(name + ".conf");
        if (Files.isRegularFile(onDisk)) {
            return parseFixedMenu(
                    HoconConfigurationLoader.builder().path(onDisk).build(), onDisk.toString(), codeDefault);
        }
        String resource = "modules/" + module + "/gui/" + name + ".conf";
        if (getClass().getClassLoader().getResource(resource) == null) {
            return codeDefault;
        }
        return parseFixedMenu(
                HoconConfigurationLoader.builder()
                        .source(() -> openReader(resource))
                        .build(),
                resource,
                codeDefault);
    }

    private FixedMenuLayout parseFixedMenu(
            HoconConfigurationLoader loader, String origin, FixedMenuLayout codeDefault) {
        ConfigurationNode root;
        try {
            root = loader.load();
        } catch (ConfigurateException failure) {
            log.error("failed to load fixed-menu gui layout " + origin, failure);
            return codeDefault;
        }
        int rows = clampRows(root.node("rows").getInt(codeDefault.rows()), codeDefault.rows());
        Material filler = material(root.node("filler-material").getString(), codeDefault.fillerMaterial());
        FixedMenuLayout.Builder builder = FixedMenuLayout.builder(rows, filler);
        ConfigurationNode slotsNode = root.node("slots");
        ConfigurationNode materialsNode = root.node("materials");
        for (Map.Entry<String, Integer> entry : codeDefault.slots().entrySet()) {
            String element = entry.getKey();
            int slot = slotsNode.node(element).getInt(entry.getValue());
            Material defaultMaterial = codeDefault.materials().get(element);
            if (defaultMaterial == null) {
                builder.slotOnly(element, slot);
            } else {
                builder.element(
                        element, slot, material(materialsNode.node(element).getString(), defaultMaterial));
            }
        }
        return builder.build();
    }

    public WarpEditorLayout loadWarpEditor(String module, String name, WarpEditorLayout codeDefault) {
        Objects.requireNonNull(module, "module");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(codeDefault, "codeDefault");
        Path onDisk =
                dataFolder.resolve("modules").resolve(module).resolve("gui").resolve(name + ".conf");
        if (Files.isRegularFile(onDisk)) {
            return parseWarpEditor(
                    HoconConfigurationLoader.builder().path(onDisk).build(), onDisk.toString(), codeDefault);
        }
        String resource = "modules/" + module + "/gui/" + name + ".conf";
        if (getClass().getClassLoader().getResource(resource) == null) {
            return codeDefault;
        }
        try {
            return parseWarpEditor(
                    HoconConfigurationLoader.builder()
                            .source(() -> openReader(resource))
                            .build(),
                    resource,
                    codeDefault);
        } catch (Exception e) {
            log.error("failed to open resource gui layout " + resource, e);
            return codeDefault;
        }
    }

    private WarpEditorLayout parseWarpEditor(
            HoconConfigurationLoader loader, String origin, WarpEditorLayout codeDefault) {
        ConfigurationNode root;
        try {
            root = loader.load();
        } catch (ConfigurateException failure) {
            log.error("failed to load warp editor gui layout " + origin, failure);
            return codeDefault;
        }
        int rows = clampRows(root.node("rows").getInt(codeDefault.rows()), codeDefault.rows());
        int iconSlot = root.node("icon-slot").getInt(codeDefault.iconSlot());
        int lockSlot = root.node("lock-slot").getInt(codeDefault.lockSlot());
        int passwordSlot = root.node("password-slot").getInt(codeDefault.passwordSlot());
        int welcomeSlot = root.node("welcome-slot").getInt(codeDefault.welcomeSlot());
        int soundsSlot = root.node("sounds-slot").getInt(codeDefault.soundsSlot());
        int particlesSlot = root.node("particles-slot").getInt(codeDefault.particlesSlot());
        int warmupSlot = root.node("warmup-slot").getInt(codeDefault.warmupSlot());
        int cooldownSlot = root.node("cooldown-slot").getInt(codeDefault.cooldownSlot());
        int closeSlot = root.node("close-slot").getInt(codeDefault.closeSlot());

        Material lockMaterial = material(root.node("lock-material").getString(), codeDefault.lockMaterial());
        Material passwordMaterial =
                material(root.node("password-material").getString(), codeDefault.passwordMaterial());
        Material welcomeMaterial = material(root.node("welcome-material").getString(), codeDefault.welcomeMaterial());
        Material soundsMaterial = material(root.node("sounds-material").getString(), codeDefault.soundsMaterial());
        Material particlesMaterial =
                material(root.node("particles-material").getString(), codeDefault.particlesMaterial());
        Material warmupMaterial = material(root.node("warmup-material").getString(), codeDefault.warmupMaterial());
        Material cooldownMaterial =
                material(root.node("cooldown-material").getString(), codeDefault.cooldownMaterial());

        return new WarpEditorLayout(
                rows,
                iconSlot,
                lockSlot,
                passwordSlot,
                welcomeSlot,
                soundsSlot,
                particlesSlot,
                warmupSlot,
                cooldownSlot,
                closeSlot,
                lockMaterial,
                passwordMaterial,
                welcomeMaterial,
                soundsMaterial,
                particlesMaterial,
                warmupMaterial,
                cooldownMaterial);
    }

    /** Resolve the {@code /home} slot-grid layout, falling back to the code default when no conf parses. */
    public HomeListLayout loadHomeList(String module, String name, HomeListLayout codeDefault) {
        ConfigurationNode root = root(module, name);
        if (root == null) {
            return codeDefault;
        }
        int rows = clampRows(root.node("rows").getInt(codeDefault.rows()), codeDefault.rows());
        List<Integer> homeSlots = intList(root.node("home-slots"), codeDefault.homeSlots());
        Material fallbackIcon = material(root.node("fallback-icon").getString(), codeDefault.fallbackIcon());
        Material emptyIcon = material(root.node("empty-icon").getString(), codeDefault.emptyIcon());
        Material filler = material(root.node("filler").getString(), codeDefault.filler());
        int prevSlot = Math.max(0, root.node("prev-slot").getInt(codeDefault.prevSlot()));
        int nextSlot = Math.max(0, root.node("next-slot").getInt(codeDefault.nextSlot()));
        int pageInfoSlot = Math.max(0, root.node("page-info-slot").getInt(codeDefault.pageInfoSlot()));
        return new HomeListLayout(rows, homeSlots, fallbackIcon, emptyIcon, filler, prevSlot, nextSlot, pageInfoSlot);
    }

    /** Resolve the per-home action-menu layout, falling back to the code default when no conf parses. */
    public HomeActionsLayout loadHomeActions(String module, String name, HomeActionsLayout codeDefault) {
        ConfigurationNode root = root(module, name);
        if (root == null) {
            return codeDefault;
        }
        int rows = clampRows(root.node("rows").getInt(codeDefault.rows()), codeDefault.rows());
        return new HomeActionsLayout(
                rows,
                Math.max(0, root.node("info-slot").getInt(codeDefault.infoSlot())),
                Math.max(0, root.node("rename-slot").getInt(codeDefault.renameSlot())),
                Math.max(0, root.node("teleport-slot").getInt(codeDefault.teleportSlot())),
                Math.max(0, root.node("delete-slot").getInt(codeDefault.deleteSlot())),
                Math.max(0, root.node("relocate-slot").getInt(codeDefault.relocateSlot())),
                Math.max(0, root.node("change-icon-slot").getInt(codeDefault.changeIconSlot())),
                Math.max(0, root.node("visibility-slot").getInt(codeDefault.visibilitySlot())),
                Math.max(0, root.node("invites-slot").getInt(codeDefault.invitesSlot())),
                Math.max(0, root.node("back-slot").getInt(codeDefault.backSlot())),
                material(root.node("info-material").getString(), codeDefault.infoMaterial()),
                material(root.node("rename-material").getString(), codeDefault.renameMaterial()),
                material(root.node("teleport-material").getString(), codeDefault.teleportMaterial()),
                material(root.node("delete-material").getString(), codeDefault.deleteMaterial()),
                material(root.node("relocate-material").getString(), codeDefault.relocateMaterial()),
                material(root.node("change-icon-material").getString(), codeDefault.changeIconMaterial()),
                material(root.node("visibility-public-material").getString(), codeDefault.visibilityPublicMaterial()),
                material(root.node("visibility-private-material").getString(), codeDefault.visibilityPrivateMaterial()),
                material(root.node("invites-material").getString(), codeDefault.invitesMaterial()),
                material(root.node("back-material").getString(), codeDefault.backMaterial()),
                material(root.node("filler").getString(), codeDefault.filler()));
    }

    /** Resolve the home-icon picker layout, falling back to the code default when no conf parses. */
    public IconSelectorLayout loadIconSelector(String module, String name, IconSelectorLayout codeDefault) {
        ConfigurationNode root = root(module, name);
        if (root == null) {
            return codeDefault;
        }
        int rows = clampRows(root.node("rows").getInt(codeDefault.rows()), codeDefault.rows());
        List<Material> icons = materialList(root.node("icons"), codeDefault.icons());
        Material navMaterial = material(root.node("nav-material").getString(), codeDefault.navMaterial());
        Material resetMaterial = material(root.node("reset-material").getString(), codeDefault.resetMaterial());
        int resetSlot = Math.max(0, root.node("reset-slot").getInt(codeDefault.resetSlot()));
        int prevSlot = Math.max(0, root.node("prev-slot").getInt(codeDefault.prevSlot()));
        int backSlot = Math.max(0, root.node("back-slot").getInt(codeDefault.backSlot()));
        int nextSlot = Math.max(0, root.node("next-slot").getInt(codeDefault.nextSlot()));
        return new IconSelectorLayout(rows, icons, navMaterial, resetMaterial, resetSlot, prevSlot, backSlot, nextSlot);
    }

    /** Resolve the invited-players menu layout, falling back to the code default when no conf parses. */
    public InvitesMenuLayout loadInvitesMenu(String module, String name, InvitesMenuLayout codeDefault) {
        ConfigurationNode root = root(module, name);
        if (root == null) {
            return codeDefault;
        }
        int rows = clampRows(root.node("rows").getInt(codeDefault.rows()), codeDefault.rows());
        List<Integer> contentSlots = intList(root.node("content-slots"), codeDefault.contentSlots());
        Material entryMaterial = material(root.node("entry-material").getString(), codeDefault.entryMaterial());
        Material navMaterial = material(root.node("nav-material").getString(), codeDefault.navMaterial());
        Material addMaterial = material(root.node("add-material").getString(), codeDefault.addMaterial());
        Material backMaterial = material(root.node("back-material").getString(), codeDefault.backMaterial());
        Material filler = material(root.node("filler").getString(), codeDefault.filler());
        int prevSlot = Math.max(0, root.node("prev-slot").getInt(codeDefault.prevSlot()));
        int addSlot = Math.max(0, root.node("add-slot").getInt(codeDefault.addSlot()));
        int backSlot = Math.max(0, root.node("back-slot").getInt(codeDefault.backSlot()));
        int nextSlot = Math.max(0, root.node("next-slot").getInt(codeDefault.nextSlot()));
        return new InvitesMenuLayout(
                rows,
                contentSlots,
                entryMaterial,
                navMaterial,
                addMaterial,
                backMaterial,
                filler,
                prevSlot,
                addSlot,
                backSlot,
                nextSlot);
    }

    /**
     * Load the HOCON root for {@code module}/{@code name}, preferring an operator's on-disk edit over the
     * bundled resource. Returns {@code null} when neither exists or the file cannot be parsed, so the caller
     * falls back to its code default — a typo never stops a menu opening.
     */
    private @org.jspecify.annotations.Nullable ConfigurationNode root(String module, String name) {
        Objects.requireNonNull(module, "module");
        Objects.requireNonNull(name, "name");
        Path onDisk =
                dataFolder.resolve("modules").resolve(module).resolve("gui").resolve(name + ".conf");
        HoconConfigurationLoader loader;
        String origin;
        if (Files.isRegularFile(onDisk)) {
            loader = HoconConfigurationLoader.builder().path(onDisk).build();
            origin = onDisk.toString();
        } else {
            String resource = "modules/" + module + "/gui/" + name + ".conf";
            if (getClass().getClassLoader().getResource(resource) == null) {
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
            log.error("failed to load gui layout " + origin, failure);
            return null;
        }
    }

    private List<Integer> intList(ConfigurationNode node, List<Integer> fallback) {
        if (node.virtual() || node.empty()) {
            return fallback;
        }
        List<Integer> values = new ArrayList<>();
        for (ConfigurationNode child : node.childrenList()) {
            values.add(child.getInt());
        }
        return values.isEmpty() ? fallback : values;
    }

    private List<Material> materialList(ConfigurationNode node, List<Material> fallback) {
        if (node.virtual() || node.empty()) {
            return fallback;
        }
        List<Material> values = new ArrayList<>();
        for (ConfigurationNode child : node.childrenList()) {
            Material matched = Material.matchMaterial(child.getString(""));
            if (matched != null) {
                values.add(matched);
            } else {
                log.warn("gui layout icon {} is unknown, skipping", child.getString(""));
            }
        }
        return values.isEmpty() ? fallback : values;
    }

    private BufferedReader openReader(String resource) throws java.io.IOException {
        InputStream in = getClass().getClassLoader().getResourceAsStream(resource);
        if (in == null) {
            throw new java.io.FileNotFoundException(resource);
        }
        return new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
    }
}
