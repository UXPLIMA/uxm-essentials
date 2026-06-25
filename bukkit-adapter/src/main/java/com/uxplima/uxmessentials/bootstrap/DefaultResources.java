package com.uxplima.uxmessentials.bootstrap;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

import org.jspecify.annotations.NullMarked;

/**
 * Writes the bundled default config files into the plugin data folder on first run.
 *
 * <p>Every operator-facing file ships as a jar resource and is copied out verbatim the first time the
 * plugin enables, so a fresh install lands an editable root {@code config.conf}, the per-module tree
 * under {@code modules/}, and the message catalogs next to the database rather than an empty folder. A
 * file the operator already has is left untouched — the copy happens only when the target is absent, so
 * edits and reloads survive every restart and upgrade. The config tree itself is still read with per-key
 * fallbacks, so a partially-trimmed file keeps working; these copies exist to give the operator
 * something to edit.
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
            "modules/economy/gui/bank-actions.conf",
            "modules/economy/gui/transaction-logs.conf",
            "modules/economy/gui/exchange.conf",
            "modules/economy/gui/loan-dashboard.conf",
            "modules/kits/config.conf",
            "modules/playerstate/config.conf",
            "modules/messaging/config.conf",
            "modules/presence/config.conf",
            "modules/moderation/config.conf",
            "modules/itemworld/config.conf",
            "modules/vaults/config.conf",
            "modules/communication/config.conf",
            "modules/communication/join-quit.conf",
            "modules/communication/announcer.conf",
            "modules/communication/advancements.conf",
            "modules/communication/info-pages.conf",
            "modules/vote/config.conf",
            "modules/holograms/config.conf",
            "modules/playerwarps/config.conf",
            "modules/scoreboard/config.conf",
            "modules/tablist/config.conf",
            "modules/nametags/config.conf",
            "modules/staff/config.conf",
            "modules/npc/config.conf",
            "modules/custommenus/config.conf",
            "modules/migration/config.conf",
            "modules/kits/gui/kits-menu.conf",
            "modules/kits/gui/kits-manager.conf",
            "modules/kits/gui/kits-settings.conf",
            "modules/kits/gui/kits-preview.conf",
            "modules/warps/gui/warps-editor.conf",
            "modules/warps/gui/warps-sound-selector.conf",
            "modules/warps/gui/warps-particle-selector.conf",
            "modules/warps/gui/warps-welcome.conf",
            "modules/homes/gui/home-list.conf",
            "modules/homes/gui/home-actions.conf",
            "modules/homes/gui/icon-selector.conf",
            "modules/homes/gui/invites-menu.conf",
            "modules/itemworld/gui/disposal.conf",
            "modules/itemworld/gui/itemworld-hub.conf",
            "modules/moderation/gui/punishments-list.conf",
            "modules/moderation/gui/punishment-detail.conf",
            "modules/moderation/gui/player-history.conf",
            "modules/holograms/gui/hologram-editor.conf",
            "modules/npc/gui/npc-editor.conf",
            "modules/playerwarps/gui/pwarp-editor.conf",
            "modules/teleport/gui/teleport-settings.conf",
            "modules/presence/gui/presence-settings.conf",
            "modules/messaging/gui/messaging-settings.conf",
            "modules/communication/gui/communication-admin.conf",
            "modules/communication/gui/announcer-list.conf",
            "modules/communication/gui/announcement-editor-list.conf",
            "modules/communication/gui/announcement-editor.conf",
            "modules/discordlink/gui/discord-status.conf",
            "modules/scoreboard/gui/scoreboard-settings.conf",
            "modules/management/gui/hub.conf",
            "modules/management/gui/colour-picker.conf",
            "modules/menu/specs/warp-sounds.conf",
            "modules/menu/specs/vault-selector.conf",
            "modules/menu/specs/communication-admin.conf",
            "modules/menu/specs/communication-announcer.conf",
            "modules/menu/specs/messaging-ignore.conf",
            "modules/menu/specs/messaging-mailbox.conf",
            "modules/menu/specs/messaging-mail-detail.conf",
            "modules/menu/specs/itemworld-recipe.conf",
            "modules/menu/specs/itemworld-recipe-none.conf",
            "modules/menu/specs/itemworld-entitycount.conf",
            "modules/menu/specs/itemworld-entitycount-empty.conf",
            "modules/menu/specs/itemworld-hub.conf",
            "modules/menu/specs/staff-navigator.conf",
            "modules/menu/specs/staff-list.conf",
            "modules/menu/specs/staff-examine.conf",
            "modules/menu/specs/hologram-list.conf",
            "modules/menu/specs/playerwarp-list.conf",
            "modules/menu/specs/npc-list.conf",
            "modules/menu/specs/home-icon.conf",
            "modules/menu/specs/home-invites.conf",
            "modules/menu/specs/home-action.conf",
            "modules/menu/specs/home-list.conf",
            "modules/menu/specs/kit-manager.conf",
            "modules/menu/specs/kit-browse.conf",
            "modules/menu/specs/warp-manager.conf",
            "modules/menu/specs/warp-browse.conf",
            "modules/menu/specs/world-list.conf",
            "modules/menu/specs/moderation-active.conf",
            "modules/menu/specs/moderation-history.conf",
            "modules/menu/specs/moderation-jailed.conf",
            "modules/menu/specs/economy-baltop.conf",
            "modules/menu/specs/economy-transactions.conf",
            "modules/menu/specs/economy-banks.conf",
            "modules/menu/specs/economy-bank-members.conf",
            "modules/menu/specs/economy-wallet.conf",
            "modules/menu/specs/economy-pay-confirm.conf",
            "modules/menu/specs/economy-target.conf",
            "modules/menu/specs/economy-bulk.conf",
            "menus/example.conf",
            "input/config.conf",
            "messages/messages_en.conf",
            "messages/messages_tr.conf");

    private DefaultResources() {}

    /** The bundled default resource paths, exposed package-private for the first-run coverage drift guard. */
    static List<String> files() {
        return FILES;
    }

    /** Copy each bundled default into {@code dataFolder}, skipping any the operator already has. */
    static void writeInto(Path dataFolder, Logger log) {
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(log, "log");
        for (String resource : FILES) {
            writeIfMissing(dataFolder.resolve(resource), resource, log);
        }
    }

    private static void writeIfMissing(Path target, String resource, Logger log) {
        if (Files.exists(target)) {
            return;
        }
        try (InputStream in = DefaultResources.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                log.warning("bundled default resource is missing from the jar: " + resource);
                return;
            }
            Files.createDirectories(Objects.requireNonNull(target.getParent(), "parent"));
            Files.copy(in, target);
            log.info("wrote default " + resource);
        } catch (IOException failure) {
            log.warning("could not write default " + resource + ": " + failure.getMessage());
        }
    }
}
