package com.uxplima.uxmessentials.docs;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.uxplima.uxmessentials.shared.application.command.CommandSpec;
import com.uxplima.uxmessentials.shared.application.command.CommandSpecCatalog;
import com.uxplima.uxmessentials.shared.application.permission.PermissionCatalog;
import com.uxplima.uxmessentials.shared.application.permission.PermissionSpec;
import com.uxplima.uxmessentials.shared.application.placeholder.PlaceholderCatalog;
import com.uxplima.uxmessentials.shared.application.placeholder.PlaceholderSpec;

/**
 * Renders the command, permission and placeholder reference under {@code docs/user/} from the code catalogues.
 *
 * <p>The pages used to be written by hand and fell behind the code by a third of their rows. They are now an output:
 * {@link ReferencePagesTest} fails when a committed page differs from what this renders, and
 * {@code ./gradlew :core:generateReference} writes them. The prose around each table lives here, so a page is edited
 * by editing this class or the catalogue it reads, never the page.
 *
 * <p>Each page lists the areas it holds by name. An area no page claims fails the test rather than vanishing from
 * the reference, and a page that grows past the documentation limit fails it too, so the split is revisited before
 * the text check rejects the page.
 */
final class ReferencePages {

    /** One page: where it goes and what it says. */
    record Page(String path, String content) {

        Page {
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(content, "content");
        }

        long lines() {
            return content.lines().count();
        }
    }

    static final String GENERATED =
            "<!-- Generated from the code catalogues by ./gradlew :core:generateReference. Edit the catalogue, not"
                    + " this page. -->";

    static final List<String> PERMISSIONS_GAMEPLAY = List.of(
            "economy",
            "homes",
            "kits",
            "playerstate",
            "playerwarps",
            "poses",
            "survival",
            "teleport",
            "trade",
            "vaults",
            "villagers",
            "warps",
            "worlds");

    static final List<String> PERMISSIONS_ITEMS = List.of("itemworld");

    static final List<String> PERMISSIONS_SERVER = List.of(
            "shared",
            "bossbar",
            "commandcontrol",
            "communication",
            "customcommands",
            "custommenus",
            "guard",
            "holograms",
            "invrollback",
            "messaging",
            "moderation",
            "nametags",
            "npc",
            "presence",
            "ranks",
            "scoreboard",
            "security",
            "servertweaks",
            "skin",
            "staff",
            "tablist",
            "vanish",
            "vote");

    /** The placeholders every module answers, apart from the kernel's own facts about a player and the server. */
    static final List<String> PLACEHOLDERS_FACTS = List.of("shared");

    private static final String DEFAULTS = "Every node the plugin checks, by module, with the default it gets when no"
            + " permission plugin grants it: `true` for every player, `op` for operators, `false` for nobody.";

    private static final String TIERS = " A node with `<n>` takes a number; its description says whether the highest"
            + " or the lowest granted number wins. A cooldown tier sets the wait: the shortest tier held wins, and the"
            + " configured wait applies only to a player who holds none. Where one tier covers waits configured apart"
            + " (each kit's own cooldown, a teleport verb's or warp's own cooldown), it only lowers them, so a tier"
            + " meant as a perk never makes a shorter wait longer. A warmup tier only lowers the configured warmup.";

    private static final String EXPORT = " `/uxmess permissions export` writes the same catalogue from the running"
            + " build to `permissions.md` in the plugin folder.";

    private ReferencePages() {}

    /** Every page, rendered with the English description line of each command keyed by its id. */
    static List<Page> render(Map<String, String> commandDescriptions) {
        return List.of(
                commands(commandDescriptions),
                permissions(
                        "permissions-gameplay.md",
                        "Permissions: gameplay modules",
                        DEFAULTS
                                + TIERS
                                + " The staff and server modules and the plugin-wide nodes are in"
                                + " [permissions-server.md](permissions-server.md), and the item and world tools are"
                                + " in [permissions-items.md](permissions-items.md)."
                                + EXPORT,
                        PERMISSIONS_GAMEPLAY),
                permissions(
                        "permissions-server.md",
                        "Permissions: staff and server modules",
                        DEFAULTS
                                + TIERS
                                + " The plugin-wide nodes are under [shared](#shared), the gameplay modules are in"
                                + " [permissions-gameplay.md](permissions-gameplay.md), and the item and world tools"
                                + " are in [permissions-items.md](permissions-items.md)."
                                + EXPORT
                                + " Every exemption starts with `uxmessentials.bypass.`; [upgrading.md](upgrading.md)"
                                + " maps the earlier names. The node of the optional REST add-on is listed under"
                                + " shared, because the add-on is a separate jar.",
                        PERMISSIONS_SERVER),
                permissions(
                        "permissions-items.md",
                        "Permissions: item and world tools",
                        "Every node the itemworld module checks, with the default it gets when no permission plugin"
                                + " grants it: `true` for every player, `op` for operators, `false` for nobody. A node"
                                + " ending in `<enchantment>`, `<item>` or `<mob>` takes the name of one. The gameplay"
                                + " modules are in [permissions-gameplay.md](permissions-gameplay.md), and the staff"
                                + " and server modules and the plugin-wide nodes are in"
                                + " [permissions-server.md](permissions-server.md)."
                                + EXPORT,
                        PERMISSIONS_ITEMS),
                placeholders(
                        "placeholders.md",
                        "Placeholders",
                        " The facts every server has, about a player, the item in their hand or the server itself, are"
                                + " in [placeholders-facts.md](placeholders-facts.md).",
                        PlaceholderCatalog.areas().stream()
                                .filter(area -> !PLACEHOLDERS_FACTS.contains(area))
                                .toList()),
                placeholders(
                        "placeholders-facts.md",
                        "Placeholders: player and server facts",
                        " The keys each module answers are in [placeholders.md](placeholders.md).",
                        PLACEHOLDERS_FACTS));
    }

    private static Page commands(Map<String, String> descriptions) {
        StringBuilder out = new StringBuilder();
        out.append("# Commands\n\n").append(GENERATED).append("\n\n");
        out.append("Every command the plugin ships, with the aliases it answers to on a fresh install and the node that"
                + " gates it. A module that is switched off registers none of its commands, and a command whose"
                + " feature is switched off in the module's file (the worth list, banknotes, the bank, loans) is left"
                + " out too. `commands.conf` renames a command, changes its aliases or turns it off, and"
                + " [permissions-gameplay.md](permissions-gameplay.md),"
                + " [permissions-server.md](permissions-server.md) and"
                + " [permissions-items.md](permissions-items.md) explain every node, including the ones a"
                + " subcommand checks.\n\n");
        out.append("`/uxmess` is the administration root, gated by `uxmessentials.admin`: `/uxmess status` and"
                + " `/uxmess help` show the state of the plugin, `/uxmess doctor` checks the setup (`doctor repair`"
                + " fixes what it can, `uxmessentials.admin.doctor.repair`), and `/uxmess reload` reads every file"
                + " again (`uxmessentials.admin.reload`). `/info`, `/motd` and `/rules` are the info pages a fresh"
                + " install ships in `modules/communication/info-pages.conf`; a page an operator adds there registers"
                + " a command of its own, as does every custom command and every menu with an open command.\n\n");
        out.append("| Command | Aliases | Module | What it does | Permission |\n");
        out.append("|---|---|---|---|---|\n");
        for (String area : CommandSpecCatalog.areas()) {
            for (CommandSpec spec : CommandSpecCatalog.forArea(area)) {
                String description = descriptions.get(spec.id());
                if (description == null) {
                    throw new IllegalStateException("no describe." + spec.id() + " line for /" + spec.name());
                }
                out.append("| `/")
                        .append(spec.name())
                        .append("` | ")
                        .append(
                                spec.aliases().isEmpty()
                                        ? ""
                                        : spec.aliases().stream()
                                                .map(alias -> "`/" + alias + "`")
                                                .collect(Collectors.joining(", ")))
                        .append(" | ")
                        .append(area)
                        .append(" | ")
                        .append(cell(description))
                        .append(" | `")
                        .append(spec.permission())
                        .append("` |\n");
            }
        }
        return new Page("commands.md", out.toString());
    }

    private static Page permissions(String path, String title, String intro, List<String> areas) {
        StringBuilder out = new StringBuilder();
        out.append("# ").append(title).append("\n\n").append(GENERATED).append("\n\n");
        out.append(intro).append("\n\n");
        List<String> present = areas.stream()
                .filter(area -> !PermissionCatalog.forArea(area).isEmpty())
                .toList();
        if (present.size() > 1) {
            out.append("Contents: ")
                    .append(present.stream()
                            .map(area -> "[" + area + "](#" + area + ")")
                            .collect(Collectors.joining(" · ")))
                    .append("\n\n");
        }
        for (String area : present) {
            out.append("## ").append(area).append("\n\n");
            out.append("| Permission | What it allows | Default |\n");
            out.append("|---|---|---|\n");
            for (PermissionSpec spec : PermissionCatalog.forArea(area)) {
                out.append("| `")
                        .append(spec.node())
                        .append("` | ")
                        .append(cell(spec.description()))
                        .append(" | ")
                        .append(spec.fallback().name().toLowerCase(Locale.ROOT).replace('_', ' '))
                        .append(" |\n");
            }
            out.append('\n');
        }
        return new Page(path, trimEnd(out));
    }

    private static Page placeholders(String path, String title, String elsewhere, List<String> areas) {
        StringBuilder out = new StringBuilder();
        out.append("# ").append(title).append("\n\n").append(GENERATED).append("\n\n");
        out.append("Registered when PlaceholderAPI is installed, as `%uxm_essentials_<key>%`. A `player` key reads the"
                + " player it is asked about, offline too; a `session` key reads live state and answers the dash"
                + " while the player is away; a `global` one is the same for everybody; a `relational` one is typed"
                + " `%rel_uxm_essentials_<key>%` and answers about the player a line is drawn for, as the viewer sees"
                + " them. `<n>` and other angle brackets are filled in by you, for example"
                + " `%uxm_essentials_homes_1_world%`. The example shows the shape of an answer.");
        out.append(elsewhere);
        out.append(" The leaderboard keys are in [reference.md](reference.md#boards). The earlier"
                + " `%uxmessentials_<key>%` form still answers in this release and logs a warning the first time it is"
                + " read; [upgrading.md](upgrading.md) says what to change. `/uxmess placeholders export` writes the"
                + " same catalogue from the running build to a file in the plugin folder.\n\n");
        out.append("| Key | Module | Value | Example | Scope |\n");
        out.append("|---|---|---|---|---|\n");
        for (String area : areas) {
            for (PlaceholderSpec spec : PlaceholderCatalog.forArea(area)) {
                out.append("| `")
                        .append(spec.key())
                        .append("` | ")
                        .append(area)
                        .append(" | ")
                        .append(cell(spec.description()))
                        .append(" | `\"")
                        .append(spec.example().replace("|", "\\|"))
                        .append("\"` | ")
                        .append(spec.scope().name().toLowerCase(Locale.ROOT))
                        .append(" |\n");
            }
        }
        return new Page(path, out.toString());
    }

    /** Every area a placeholder or permission page names, so the test can find the ones none of them claims. */
    static Set<String> permissionAreasOnPages() {
        Set<String> areas = new LinkedHashSet<>();
        areas.addAll(PERMISSIONS_GAMEPLAY);
        areas.addAll(PERMISSIONS_ITEMS);
        areas.addAll(PERMISSIONS_SERVER);
        return areas;
    }

    /** Text that sits in a table cell: a pipe would end the cell, so it is escaped. */
    private static String cell(String text) {
        return text.replace("|", "\\|");
    }

    private static String trimEnd(StringBuilder out) {
        List<String> lines = new ArrayList<>(out.toString().lines().toList());
        while (!lines.isEmpty() && lines.getLast().isBlank()) {
            lines.removeLast();
        }
        return String.join("\n", lines) + "\n";
    }
}
