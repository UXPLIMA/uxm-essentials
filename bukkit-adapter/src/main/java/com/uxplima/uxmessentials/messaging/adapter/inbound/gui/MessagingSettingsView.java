package com.uxplima.uxmessentials.messaging.adapter.inbound.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.messaging.application.MessagingMessageKey;
import com.uxplima.uxmessentials.messaging.application.port.MessageToggleStore;
import com.uxplima.uxmessentials.messaging.application.port.SocialSpyStore;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityEditorLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayouts;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.SettingsPanelView;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.EditableProperty;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.ToggleProperty;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The per-player messaging settings panel ({@code /msgsettings}, and the messaging entry on the
 * {@code /uxmess gui} hub): a player flips their own message-accept switch (the {@code /msgtoggle} state), and a
 * staff member additionally flips their own social-spy switch (the {@code /socialspy} ALL flag). Each toggle
 * reads and writes the same store the corresponding command does, so opening the panel always shows the live
 * state and a click is the same mutation the command makes.
 *
 * <p>The social-spy toggle is staff-only: it is built into the panel only for a viewer who holds the social-spy
 * permission, so a player without it sees one toggle and the second property slot stays empty. The panel holds
 * no logic of its own — the settings are re-read fresh on every open, closing over the viewer.
 */
@NullMarked
public final class MessagingSettingsView {

    private static final String MODULE = "messaging";
    private static final String LAYOUT = "messaging-settings";

    /** The staff node that gates the social-spy command, reused to gate the panel's social-spy toggle. */
    private static final String SOCIALSPY_PERMISSION = "uxmessentials.msg.socialspy";

    private final SettingsPanelView panel;

    public MessagingSettingsView(
            GuiText guiText,
            Scheduler scheduler,
            GuiLayouts guiLayouts,
            Messages messages,
            MessageToggleStore toggles,
            SocialSpyStore socialSpy,
            Permissions permissions) {
        Objects.requireNonNull(guiText, "guiText");
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(guiLayouts, "guiLayouts");
        Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(toggles, "toggles");
        Objects.requireNonNull(socialSpy, "socialSpy");
        Objects.requireNonNull(permissions, "permissions");
        EntityEditorLayout layout =
                guiLayouts.loadEntityEditor(MODULE, LAYOUT, EntityEditorLayout.codeDefault(List.of(11, 15), 22));
        this.panel = SettingsPanelView.builder()
                .guiText(guiText)
                .scheduler(scheduler)
                .layout(layout)
                .title(MessagingMessageKey.GUI_SETTINGS_TITLE)
                .valueLore(MessagingMessageKey.GUI_SETTINGS_VALUE_LORE)
                .backName(MessagingMessageKey.GUI_SETTINGS_BACK)
                .settings(viewer -> settings(messages, scheduler, toggles, socialSpy, permissions, viewer))
                .onBack((player, viewer) -> player.closeInventory())
                .build();
    }

    /** Open the panel for {@code player}, on the viewer's entity thread. */
    public void open(Player player, PlayerRef viewer) {
        panel.open(player, viewer);
    }

    /** The backing panel, exposed so a test can assert the slot↔toggle mapping without opening a live menu. */
    public SettingsPanelView panel() {
        return panel;
    }

    private static List<EditableProperty> settings(
            Messages messages,
            Scheduler scheduler,
            MessageToggleStore toggles,
            SocialSpyStore socialSpy,
            Permissions permissions,
            PlayerRef viewer) {
        List<EditableProperty> properties = new ArrayList<>(2);
        properties.add(ToggleProperty.ofBoolean(
                MessagingMessageKey.GUI_SETTINGS_ACCEPT,
                Material.WRITABLE_BOOK,
                () -> toggles.acceptsMessages(viewer),
                (who, on) -> onOff(messages, who, on),
                on -> {
                    // The store flip toggles the current value; only flip when the target differs from now, so a
                    // re-click that lands on the same state is a no-op rather than a double-toggle.
                    if (toggles.acceptsMessages(viewer) != on) {
                        toggles.toggle(viewer);
                    }
                },
                scheduler));
        // Social spy is a staff capability; only staff who can already run /socialspy get the toggle.
        if (permissions.has(viewer, SOCIALSPY_PERMISSION)) {
            properties.add(ToggleProperty.ofBoolean(
                    MessagingMessageKey.GUI_SETTINGS_SOCIALSPY,
                    Material.ENDER_EYE,
                    () -> socialSpy.isSpying(viewer),
                    (who, on) -> onOff(messages, who, on),
                    on -> {
                        if (socialSpy.isSpying(viewer) != on) {
                            socialSpy.toggle(viewer);
                        }
                    },
                    scheduler));
        }
        return List.copyOf(properties);
    }

    private static String onOff(Messages messages, PlayerRef viewer, boolean on) {
        return messages.resolve(
                viewer,
                on ? MessagingMessageKey.GUI_SETTINGS_VALUE_ON : MessagingMessageKey.GUI_SETTINGS_VALUE_OFF,
                Map.of());
    }
}
