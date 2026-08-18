package com.uxplima.uxmessentials.shared.gui;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.uxplima.uxmessentials.bootstrap.di.DefaultModuleRegistry;
import com.uxplima.uxmessentials.shared.application.module.FeatureModule;
import org.junit.jupiter.api.Test;

/**
 * The hub-coverage guard: every module that owns a screen a viewer can open from nothing must appear on the
 * {@code /uxmess gui} hub. The regression it exists for is quiet: a module ships its GUI, registers the command
 * that opens it, and never registers a {@link com.uxplima.uxmessentials.shared.adapter.inbound.gui.ManagementGuiEntry},
 * so the hub silently stays a partial list and an operator concludes the feature has no GUI at all.
 *
 * <p>It reads the registration ids straight out of the production wiring source, the same way the other drift
 * guards read the code rather than a copy of it, and compares them against every module in
 * {@link DefaultModuleRegistry}. A module with no hub entry has to be named in {@link #WITHOUT_HUB_ENTRY} with
 * the reason, which makes adding one a deliberate decision instead of an oversight.
 */
class ManagementHubCoverageDriftTest {

    /**
     * {@code new ManagementGuiEntry(} followed by the quoted entry id on the same line or the next. The class name
     * is written both plain and fully qualified in the wiring, so the package prefix is optional here.
     */
    private static final Pattern ENTRY =
            Pattern.compile("new (?:[\\w.]+\\.)?ManagementGuiEntry\\(\\s*\"([a-z0-9-]+)\"");

    /**
     * The modules that deliberately have no hub entry, and why. Two shapes qualify: a module with no GUI of its
     * own, and a module whose GUI only exists relative to something the viewer picks first (a target player, a
     * villager, a trade partner), which the hub has no way to supply.
     */
    private static final Set<String> WITHOUT_HUB_ENTRY = Set.of(
            // No GUI at all: these configure entirely through their .conf files.
            "tablist",
            "nametags",
            "commandcontrol",
            "servertweaks",
            "vanish",
            "skin", // a skin is chosen by name or url through /skin, so there is no catalogue to browse
            // GUIs that only open onto a subject the viewer names first.
            "playerstate", // /invsee, /endersee and /playtime all open onto another player
            "invrollback", // /invrestore opens the snapshot list of one named player
            "villagers", // the trade editor opens onto the villager the staff member clicked
            "trade", // a trade window needs the partner who accepted
            "security"); // the PIN keypad is an auth prompt, not a screen to browse to

    @Test
    void everyModuleWithABrowsableGuiIsOnTheHub() {
        Set<String> registered = registeredEntryIds();
        List<String> missing = new ArrayList<>();
        for (FeatureModule module : new DefaultModuleRegistry().all()) {
            String id = module.id().value();
            if (WITHOUT_HUB_ENTRY.contains(id) || hasEntry(registered, id)) {
                continue;
            }
            missing.add(id);
        }
        assertThat(missing)
                .as(
                        "these modules register no /uxmess gui hub entry: either register one in their wiring, "
                                + "or name them in WITHOUT_HUB_ENTRY with the reason:\n%s",
                        String.join("\n", missing))
                .isEmpty();
    }

    @Test
    void theExceptionListNamesOnlyRealModules() {
        Set<String> modules = new LinkedHashSet<>();
        new DefaultModuleRegistry()
                .all()
                .forEach(module -> modules.add(module.id().value()));

        // A renamed or dropped module must not leave a stale excuse behind that silently exempts nothing.
        assertThat(modules).containsAll(WITHOUT_HUB_ENTRY);
    }

    @Test
    void theEntryPatternSelfTest() {
        assertThat(ENTRY.matcher("guiRegistry.register(new ManagementGuiEntry(\"survival\",")
                        .find())
                .isTrue();
        assertThat(ENTRY.matcher("new ManagementGuiEntry(\n                \"messaging-mailbox\",")
                        .find())
                .isTrue();
        assertThat(ENTRY.matcher("new com.uxplima.uxmessentials.shared.adapter.inbound.gui.ManagementGuiEntry(\n"
                                + "                \"worlds\",")
                        .find())
                .isTrue();
        assertThat(ENTRY.matcher("registry.register(entry);").find()).isFalse();
    }

    /** An entry covers a module when its id is the module id, or the module id plus a per-screen suffix. */
    private static boolean hasEntry(Set<String> registered, String moduleId) {
        return registered.stream().anyMatch(id -> id.equals(moduleId) || id.startsWith(moduleId + "-"));
    }

    private static Set<String> registeredEntryIds() {
        Set<String> ids = new LinkedHashSet<>();
        Path root = Path.of("src", "main", "java");
        try (Stream<Path> files = Files.walk(root)) {
            files.filter(path -> path.toString().endsWith(".java")).forEach(path -> collect(path, ids));
        } catch (IOException failure) {
            throw new UncheckedIOException("could not scan the adapter sources for hub registrations", failure);
        }
        return ids;
    }

    private static void collect(Path file, Set<String> ids) {
        String source;
        try {
            source = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new UncheckedIOException("could not read " + file, failure);
        }
        Matcher matcher = ENTRY.matcher(source);
        while (matcher.find()) {
            ids.add(matcher.group(1));
        }
    }
}
