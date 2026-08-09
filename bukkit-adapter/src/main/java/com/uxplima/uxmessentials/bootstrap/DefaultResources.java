package com.uxplima.uxmessentials.bootstrap;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.spongepowered.configurate.ConfigurateException;

/**
 * Writes the bundled default config files into the plugin data folder on first run, and keeps them current
 * across updates.
 *
 * <p>Every operator-facing file ships as a jar resource and is copied out verbatim the first time the
 * plugin enables, so a fresh install lands an editable root {@code config.conf}, the per-module tree
 * under {@code modules/}, and the message catalogs next to the database rather than an empty folder.
 *
 * <p>A file the operator already has is never overwritten: their values, comments and formatting stay exactly
 * as they left them. What an update does add is the settings that did not exist when they installed. Those are
 * appended as a commented HOCON block at the end of the file, so a new knob is something they can read and edit
 * rather than an invisible default buried in the jar. Which keys count as new is decided against the copy of the
 * previously shipped default kept under {@code .defaults/} (see {@link BundledDefaultsMerge}); a key they
 * deleted on purpose stays deleted. The config tree is still read with per-key fallbacks either way, so a
 * trimmed or half-merged file keeps working.
 */
@NullMarked
final class DefaultResources {

    /** Jar resource paths, also used verbatim as the relative target path under the data folder. */
    private static final List<String> FILES = List.of(
            "config.conf",
            "modules/teleport/config.conf",
            "modules/teleport/rtp.conf",
            "modules/worlds/config.conf",
            "modules/homes/config.conf",
            "modules/warps/config.conf",
            "modules/economy/config.conf",
            "modules/economy/currencies.conf",
            "modules/kits/config.conf",
            "modules/playerstate/config.conf",
            "modules/playerstate/gui/invsee.conf",
            "modules/playerstate/gui/endersee.conf",
            "modules/messaging/config.conf",
            "modules/presence/config.conf",
            "modules/vanish/config.conf",
            "modules/moderation/config.conf",
            "modules/itemworld/config.conf",
            "modules/vaults/config.conf",
            "modules/communication/config.conf",
            "modules/communication/join-quit.conf",
            "modules/communication/announcer.conf",
            "modules/communication/advancements.conf",
            "modules/communication/info-pages.conf",
            "modules/communication/chat.conf",
            "modules/vote/config.conf",
            "modules/discordlink/config.conf",
            "modules/holograms/config.conf",
            "modules/playerwarps/config.conf",
            "modules/scoreboard/config.conf",
            "modules/tablist/config.conf",
            "modules/nametags/config.conf",
            "modules/staff/config.conf",
            "modules/npc/config.conf",
            "modules/custommenus/config.conf",
            "modules/poses/config.conf",
            "modules/survival/config.conf",
            "modules/ranks/config.conf",
            "modules/ranks/ranks.conf",
            "modules/security/config.conf",
            "modules/security/gui/pin-keypad.conf",
            "modules/security/gui/pin-create.conf",
            "modules/commandcontrol/config.conf",
            "modules/trade/config.conf",
            "modules/trade/gui/trade.conf",
            "modules/trade/gui/trade-cross.conf",
            "modules/villagers/config.conf",
            "modules/invrollback/config.conf",
            "modules/regions/config.conf",
            "modules/servertweaks/config.conf",
            "modules/migration/config.conf",
            "modules/communication/gui/announcement-editor-list.conf",
            "modules/communication/gui/announcement-editor.conf",
            "modules/communication/gui/communication-admin.conf",
            "modules/communication/gui/communication-announcer.conf",
            "modules/discordlink/gui/discord-status.conf",
            "modules/economy/gui/economy-admin.conf",
            "modules/economy/gui/economy-baltop.conf",
            "modules/economy/gui/economy-bank-actions.conf",
            "modules/economy/gui/economy-bank-members.conf",
            "modules/economy/gui/economy-banks.conf",
            "modules/economy/gui/currency-picker.conf",
            "modules/vote/gui/vote-sites.conf",
            "modules/regions/gui/region-list.conf",
            "modules/regions/gui/region-flags.conf",
            "modules/regions/gui/region-roster.conf",
            "modules/economy/gui/economy-bulk.conf",
            "modules/economy/gui/economy-exchange.conf",
            "modules/economy/gui/economy-loan.conf",
            "modules/economy/gui/economy-pay-confirm.conf",
            "modules/economy/gui/economy-target.conf",
            "modules/economy/gui/economy-transactions.conf",
            "modules/economy/gui/economy-wallet.conf",
            "modules/holograms/gui/hologram-editor.conf",
            "modules/holograms/gui/hologram-list.conf",
            "modules/homes/gui/home-action.conf",
            "modules/homes/gui/home-icon.conf",
            "modules/homes/gui/home-invites.conf",
            "modules/homes/gui/home-list.conf",
            "modules/homes/gui/icon-selector.conf",
            "modules/itemworld/gui/disposal.conf",
            "modules/itemworld/gui/itemedit.conf",
            "modules/itemworld/gui/itemworld-entitycount-empty.conf",
            "modules/itemworld/gui/itemworld-entitycount.conf",
            "modules/itemworld/gui/itemworld-hub.conf",
            "modules/itemworld/gui/itemworld-recipe-none.conf",
            "modules/itemworld/gui/itemworld-recipe.conf",
            "modules/kits/gui/kit-browse.conf",
            "modules/kits/gui/kit-category-manager.conf",
            "modules/kits/gui/kit-category-parent-selector.conf",
            "modules/kits/gui/kit-category-selector.conf",
            "modules/kits/gui/kit-category-settings.conf",
            "modules/kits/gui/kit-manager.conf",
            "modules/kits/gui/kit-settings.conf",
            "modules/kits/gui/kits-menu.conf",
            "modules/kits/gui/kits-preview.conf",
            "modules/management/gui/colour-picker.conf",
            "modules/management/gui/hub.conf",
            "modules/management/gui/player-picker.conf",
            "modules/messaging/gui/messaging-ignore.conf",
            "modules/messaging/gui/messaging-mail-detail.conf",
            "modules/messaging/gui/messaging-mailbox.conf",
            "modules/messaging/gui/messaging-settings.conf",
            "modules/moderation/gui/moderation-active.conf",
            "modules/moderation/gui/moderation-history.conf",
            "modules/moderation/gui/moderation-jail-edit.conf",
            "modules/moderation/gui/moderation-jailed.conf",
            "modules/moderation/gui/moderation-punishment-confirm.conf",
            "modules/moderation/gui/punishment-detail.conf",
            "modules/npc/gui/npc-editor.conf",
            "modules/npc/gui/npc-list.conf",
            "modules/playerwarps/gui/playerwarp-list.conf",
            "modules/playerwarps/gui/pwarp-browse.conf",
            "modules/playerwarps/gui/pwarp-categories.conf",
            "modules/playerwarps/gui/pwarp-editor.conf",
            "modules/playerwarps/gui/pwarp-icon.conf",
            "modules/playerwarps/gui/pwarp-view.conf",
            "modules/playerwarps/gui/pwarp-rate.conf",
            "modules/playerwarps/gui/pwarp-manage.conf",
            "modules/playerwarps/gui/pwarp-members.conf",
            "modules/playerwarps/gui/pwarp-whitelist.conf",
            "modules/playerwarps/gui/pwarp-bans.conf",
            "modules/poses/gui/poses-settings.conf",
            "modules/ranks/gui/ranks-panel.conf",
            "modules/presence/gui/presence-settings.conf",
            "modules/scoreboard/gui/scoreboard-settings.conf",
            "modules/survival/gui/survival-settings.conf",
            "modules/staff/gui/staff-examine.conf",
            "modules/staff/gui/staff-list.conf",
            "modules/staff/gui/staff-navigator.conf",
            "modules/teleport/gui/rtp.conf",
            "modules/teleport/gui/teleport-settings.conf",
            "modules/vaults/gui/vault-selector.conf",
            "modules/warps/gui/warp-browse.conf",
            "modules/warps/gui/warp-category-manager.conf",
            "modules/warps/gui/warp-category-parent-selector.conf",
            "modules/warps/gui/warp-category-selector.conf",
            "modules/warps/gui/warp-category-settings.conf",
            "modules/warps/gui/warp-editor.conf",
            "modules/warps/gui/warp-manager.conf",
            "modules/warps/gui/warp-sounds.conf",
            "modules/warps/gui/warp-welcome.conf",
            "modules/worlds/gui/world-create.conf",
            "modules/worlds/gui/world-generation.conf",
            "modules/worlds/gui/world-grid.conf",
            "modules/worlds/gui/world-list.conf",
            "modules/worlds/gui/world-main.conf",
            "menus/example.conf",
            "text-input.conf",
            "messages/messages_en.conf",
            "messages/messages_tr.conf");

    private DefaultResources() {}

    /**
     * Where the last reconciled copy of each bundled default is kept. It is the plugin's own bookkeeping, not
     * an operator-facing file: editing anything in here only changes which keys a later update thinks are new.
     */
    static final String BASELINE_DIR = ".defaults";

    /** The bundled default resource paths, exposed package-private for the first-run coverage drift guard. */
    static List<String> files() {
        return FILES;
    }

    /**
     * Write each bundled default into {@code dataFolder}, and append to the files the operator already has any
     * setting this version added since the default they were last reconciled against.
     *
     * @param version the plugin version, named in the comment above an appended block so the operator can see
     *     which update brought the settings in
     */
    static void writeInto(Path dataFolder, Logger log, String version) {
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(log, "log");
        Objects.requireNonNull(version, "version");
        for (String resource : FILES) {
            reconcile(dataFolder, resource, version, log);
        }
    }

    private static void reconcile(Path dataFolder, String resource, String version, Logger log) {
        String bundled = bundled(resource, log);
        if (bundled == null) {
            return;
        }
        Path target = dataFolder.resolve(resource);
        Path baseline = dataFolder.resolve(BASELINE_DIR).resolve(resource);
        try {
            if (!Files.exists(target)) {
                write(target, bundled);
                write(baseline, bundled);
                log.info("wrote default " + resource);
                return;
            }
            if (!Files.exists(baseline)) {
                // First enable after this bookkeeping existed: record what this version ships and merge nothing.
                // Without a baseline there is no way to tell a key an update added from one the operator removed,
                // and guessing wrong would undo their edits. From here on the comparison is exact.
                write(baseline, bundled);
                return;
            }
            String previous = Files.readString(baseline, StandardCharsets.UTF_8);
            if (previous.equals(bundled)) {
                return;
            }
            if (appendNewSettings(target, resource, bundled, previous, version, log)) {
                write(baseline, bundled);
            }
        } catch (IOException failure) {
            log.warning("could not write default " + resource + ": " + failure.getMessage());
        }
    }

    /**
     * Append the settings this version added to the operator's copy. Returns whether the file is now in step
     * with the bundled default: a false means their file could not be read or parsed, so the baseline is left
     * behind and the next enable tries again rather than silently swallowing the new keys.
     */
    private static boolean appendNewSettings(
            Path target, String resource, String bundled, String previous, String version, Logger log)
            throws IOException {
        String operator = Files.readString(target, StandardCharsets.UTF_8);
        Optional<String> added;
        try {
            added = BundledDefaultsMerge.newSettings(bundled, previous, operator);
        } catch (ConfigurateException failure) {
            log.warning("could not check " + resource + " for new settings: " + failure.getMessage());
            return false;
        }
        if (added.isEmpty()) {
            return true;
        }
        StringBuilder upgraded = new StringBuilder(operator);
        if (!operator.endsWith("\n")) {
            upgraded.append('\n');
        }
        upgraded.append('\n').append(header(version)).append(added.get());
        Files.writeString(target, upgraded.toString(), StandardCharsets.UTF_8);
        log.info("added the settings new in " + version + " to " + resource);
        return true;
    }

    /** The comment written above an appended block, so the operator knows where it came from and why. */
    private static String header(String version) {
        return """
                # -----------------------------------------------------------------------------
                # Added by the update to %s. These settings are new in this version, so your
                # file did not have them yet. The values below are the shipped defaults, and
                # nothing above this line was touched. Edit them here, or move them up into the
                # matching block: HOCON merges repeated blocks, so both read the same.
                # -----------------------------------------------------------------------------
                """.formatted(version);
    }

    /** The bundled text of {@code resource}, or {@code null} when the jar is missing it. */
    private static @Nullable String bundled(String resource, Logger log) {
        try (InputStream in = DefaultResources.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                log.warning("bundled default resource is missing from the jar: " + resource);
                return null;
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            log.warning("could not read bundled default " + resource + ": " + failure.getMessage());
            return null;
        }
    }

    private static void write(Path target, String content) throws IOException {
        Files.createDirectories(Objects.requireNonNull(target.getParent(), "parent"));
        Files.writeString(target, content, StandardCharsets.UTF_8);
    }
}
