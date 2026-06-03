package com.uxplima.uxmessentials.shared.adapter.inbound.gui;

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

    private BufferedReader openReader(String resource) throws java.io.IOException {
        InputStream in = getClass().getClassLoader().getResourceAsStream(resource);
        if (in == null) {
            throw new java.io.FileNotFoundException(resource);
        }
        return new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
    }
}
