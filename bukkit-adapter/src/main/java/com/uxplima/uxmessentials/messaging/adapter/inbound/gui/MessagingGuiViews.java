package com.uxplima.uxmessentials.messaging.adapter.inbound.gui;

import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.messaging.adapter.MessagingServices;
import com.uxplima.uxmessentials.messaging.application.port.IgnoreStore;
import com.uxplima.uxmessentials.messaging.application.port.MailRepository;
import com.uxplima.uxmessentials.messaging.application.port.MessageToggleStore;
import com.uxplima.uxmessentials.messaging.application.port.SocialSpyStore;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityListLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayouts;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.gui.anvil.AnvilInput;
import org.jspecify.annotations.NullMarked;

/**
 * Assembles the messaging module's three player-facing GUIs — the settings panel, the ignore-list manager, and
 * the mailbox — over the existing use cases and stores, and hands the openers to the commands and the
 * {@code /uxmess gui} hub. Each view reads the raw store the corresponding command reads (so the rendered state
 * matches in-game state) and writes through the same use case (so notifiers and persistence stay consistent).
 * The views are constructed once here and reused for every viewer.
 *
 * <p>The four stores are passed in individually rather than as the wiring's package-private {@code Stores}
 * record, so this assembler stays decoupled from the wiring's internal shape.
 */
@NullMarked
public final class MessagingGuiViews {

    private static final String MODULE = "messaging";

    private final MessagingSettingsView settingsView;
    private final IgnoreListView ignoreView;
    private final MailboxView mailboxView;

    private MessagingGuiViews(MessagingSettingsView settingsView, IgnoreListView ignoreView, MailboxView mailboxView) {
        this.settingsView = settingsView;
        this.ignoreView = ignoreView;
        this.mailboxView = mailboxView;
    }

    /** Build the three views over the messaging use cases, the raw stores, and the module's GUI layouts. */
    public static MessagingGuiViews create(
            GuiText guiText,
            Scheduler scheduler,
            Messages messages,
            Permissions permissions,
            MessagingServices services,
            MessageToggleStore toggles,
            SocialSpyStore socialSpy,
            IgnoreStore ignores,
            MailRepository mail,
            PlayerLookup players,
            AnvilInput anvil,
            GuiLayouts layouts) {
        Objects.requireNonNull(guiText, "guiText");
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(permissions, "permissions");
        Objects.requireNonNull(services, "services");
        Objects.requireNonNull(toggles, "toggles");
        Objects.requireNonNull(socialSpy, "socialSpy");
        Objects.requireNonNull(ignores, "ignores");
        Objects.requireNonNull(mail, "mail");
        Objects.requireNonNull(players, "players");
        Objects.requireNonNull(anvil, "anvil");
        Objects.requireNonNull(layouts, "layouts");

        MessagingSettingsView settingsView =
                new MessagingSettingsView(guiText, scheduler, layouts, messages, toggles, socialSpy, permissions);

        EntityListLayout ignoreLayout = layouts.loadEntityList(
                MODULE, "ignore-list", EntityListLayout.withCreate(Material.PLAYER_HEAD, 49, Material.LIME_DYE));
        IgnoreListView ignoreView = new IgnoreListView(
                guiText, scheduler, ignores, services.ignore(), services.unignore(), players, anvil, ignoreLayout);

        EntityListLayout mailLayout = layouts.loadEntityList(
                MODULE, "mailbox", EntityListLayout.withCreate(Material.PAPER, 49, Material.LAVA_BUCKET));
        MailboxView mailboxView = new MailboxView(guiText, scheduler, mail, services.clearMail(), mailLayout);

        return new MessagingGuiViews(settingsView, ignoreView, mailboxView);
    }

    /** Open the messaging settings panel for {@code viewer}. */
    public void openSettings(Player player, PlayerRef viewer) {
        settingsView.open(player, viewer);
    }

    /** Open the ignore-list manager for {@code viewer}. */
    public void openIgnore(Player player, PlayerRef viewer) {
        ignoreView.open(player, viewer);
    }

    /** Open the mailbox for {@code viewer}. */
    public void openMailbox(Player player, PlayerRef viewer) {
        mailboxView.open(player, viewer);
    }

    /** The settings panel, for tests. */
    public MessagingSettingsView settingsView() {
        return settingsView;
    }

    /** The ignore-list manager, for tests. */
    public IgnoreListView ignoreView() {
        return ignoreView;
    }

    /** The mailbox, for tests. */
    public MailboxView mailboxView() {
        return mailboxView;
    }
}
