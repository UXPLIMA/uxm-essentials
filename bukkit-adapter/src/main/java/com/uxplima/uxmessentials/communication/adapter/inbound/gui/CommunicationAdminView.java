package com.uxplima.uxmessentials.communication.adapter.inbound.gui;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.communication.adapter.ChatLock;
import com.uxplima.uxmessentials.communication.adapter.outbound.BukkitAnnouncerBroadcaster;
import com.uxplima.uxmessentials.communication.application.CommunicationMessageKey;
import com.uxplima.uxmessentials.communication.application.CommunicationNotifier;
import com.uxplima.uxmessentials.communication.domain.Announcement;
import com.uxplima.uxmessentials.communication.domain.AnnouncerConfig;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityEditorLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityListLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityListView;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayouts;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.SettingsPanelView;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.ActionProperty;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.EditableProperty;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.ToggleProperty;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.gui.ConfirmMenu;
import com.uxplima.uxmlib.gui.anvil.AnvilInput;
import com.uxplima.uxmlib.gui.anvil.AnvilResult;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;

/**
 * The communication admin panel ({@code /communication gui}, and the communication entry on the {@code /uxmess gui}
 * hub): a {@link SettingsPanelView} of admin buttons, each wired to the same surface the communication commands use.
 * The chat-lock toggle flips the live {@link ChatLock} the {@code /togglechat} command does; the clearchat button
 * runs the {@code /clearchat} fan-out behind a confirm; the broadcast button captures a line through a vanilla anvil
 * and sends it through the same {@link BukkitAnnouncerBroadcaster} as {@code /broadcast}; the announcer button opens
 * a read-only list of the configured announcements (the {@code /announce list} surface).
 *
 * <p>The panel holds no domain logic of its own. Only the surfaces the commands actually expose are placed: there
 * is no per-announcement toggle (the announcer is config-driven, with no global on/off the use case exposes), so the
 * announcer view is read-only — exactly what {@code /announce list} shows. Every visible string is a
 * {@link CommunicationMessageKey}; the clearchat fan-out and the anvil hop run on the kernel {@link Scheduler}.
 */
@NullMarked
public final class CommunicationAdminView {

    private static final String MODULE = "communication";
    private static final String PANEL_LAYOUT = "communication-admin";
    private static final String ANNOUNCER_LAYOUT = "announcer-list";
    private static final String CLEARCHAT_EXEMPT = "uxmessentials.communication.clearchat.exempt";
    private static final int CLEARCHAT_BLANK_LINES = 100;

    private final SettingsPanelView panel;
    private final EntityListView<Announcement> announcerView;
    private final BukkitAnnouncerBroadcaster broadcaster;
    private final String broadcastPrefix;

    public CommunicationAdminView(
            GuiText guiText,
            Scheduler scheduler,
            GuiLayouts guiLayouts,
            Messages messages,
            ChatLock chatLock,
            BukkitAnnouncerBroadcaster broadcaster,
            String broadcastPrefix,
            Supplier<AnnouncerConfig> announcerConfig,
            CommunicationNotifier notifier,
            MessageSink sink,
            AnvilInput anvil) {
        Objects.requireNonNull(guiText, "guiText");
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(guiLayouts, "guiLayouts");
        Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(chatLock, "chatLock");
        Objects.requireNonNull(broadcaster, "broadcaster");
        Objects.requireNonNull(broadcastPrefix, "broadcastPrefix");
        Objects.requireNonNull(announcerConfig, "announcerConfig");
        Objects.requireNonNull(notifier, "notifier");
        Objects.requireNonNull(sink, "sink");
        Objects.requireNonNull(anvil, "anvil");
        this.broadcaster = broadcaster;
        this.broadcastPrefix = broadcastPrefix;

        EntityListLayout announcerLayout =
                guiLayouts.loadEntityList(MODULE, ANNOUNCER_LAYOUT, EntityListLayout.paginatedDefault(Material.PAPER));
        this.announcerView = EntityListView.<Announcement>builder()
                .guiText(guiText)
                .scheduler(scheduler)
                .layout(announcerLayout)
                .title(CommunicationMessageKey.GUI_ANNOUNCER_TITLE)
                .navNames(CommunicationMessageKey.GUI_ANNOUNCER_PREV, CommunicationMessageKey.GUI_ANNOUNCER_NEXT)
                .entities(() -> announcerConfig.get().announcements())
                .iconRenderer((viewer, announcement) -> announcerIcon(guiText, viewer, announcement))
                .onSelect((player, announcement) -> {})
                .build();

        EntityEditorLayout panelLayout = guiLayouts.loadEntityEditor(
                MODULE, PANEL_LAYOUT, EntityEditorLayout.codeDefault(List.of(10, 12, 14, 16), 22));
        this.panel = SettingsPanelView.builder()
                .guiText(guiText)
                .scheduler(scheduler)
                .layout(panelLayout)
                .title(CommunicationMessageKey.GUI_PANEL_TITLE)
                .valueLore(CommunicationMessageKey.GUI_PANEL_VALUE_LORE)
                .backName(CommunicationMessageKey.GUI_PANEL_BACK)
                .settings(viewer -> buttons(scheduler, messages, chatLock, notifier, sink, anvil, guiText))
                .onBack((player, who) -> player.closeInventory())
                .build();
    }

    /** Open the panel for {@code player}, on the viewer's entity thread. */
    public void open(Player player, PlayerRef viewer) {
        panel.open(player, viewer);
    }

    /** The read-only announcer list, exposed so the panel test can assert its rendered entries. */
    public EntityListView<Announcement> announcerView() {
        return announcerView;
    }

    /** The settings panel, exposed package-private so the test can assert the button↔slot mapping. */
    SettingsPanelView panel() {
        return panel;
    }

    private List<EditableProperty> buttons(
            Scheduler scheduler,
            Messages messages,
            ChatLock chatLock,
            CommunicationNotifier notifier,
            MessageSink sink,
            AnvilInput anvil,
            GuiText guiText) {
        ToggleProperty<Boolean> lock = ToggleProperty.ofBoolean(
                CommunicationMessageKey.GUI_CHAT_LOCK,
                Material.IRON_DOOR,
                chatLock::isLocked,
                (who, locked) -> lockState(messages, who, locked),
                locked -> {
                    if (chatLock.isLocked() != locked) {
                        chatLock.toggle();
                    }
                },
                scheduler);
        java.util.function.Function<PlayerRef, String> hint =
                who -> messages.resolve(who, CommunicationMessageKey.GUI_PANEL_ACTION_HINT, Map.of());
        ActionProperty clearchat = new ActionProperty(
                CommunicationMessageKey.GUI_CLEARCHAT,
                Material.LAVA_BUCKET,
                hint,
                (player, reopen) -> confirmClearChat(guiText, scheduler, notifier, sink, player, reopen));
        ActionProperty broadcast = new ActionProperty(
                CommunicationMessageKey.GUI_BROADCAST,
                Material.BELL,
                hint,
                (player, reopen) -> promptBroadcast(guiText, scheduler, anvil, player, reopen));
        ActionProperty announcer = new ActionProperty(
                CommunicationMessageKey.GUI_ANNOUNCER,
                Material.WRITABLE_BOOK,
                hint,
                (player, reopen) -> announcerView.open(player, BukkitRefs.toRef(player)));
        return List.of(lock, clearchat, broadcast, announcer);
    }

    private void confirmClearChat(
            GuiText guiText,
            Scheduler scheduler,
            CommunicationNotifier notifier,
            MessageSink sink,
            Player player,
            Runnable reopen) {
        PlayerRef viewer = BukkitRefs.toRef(player);
        Component title = guiText.text(viewer, CommunicationMessageKey.GUI_CLEARCHAT_CONFIRM);
        ConfirmMenu.of(title, () -> doClearChat(scheduler, notifier, sink, player), reopen)
                .open(player);
    }

    /**
     * Flush online players' chat exactly as {@code /clearchat} does — a screenful of blank lines (skipping the
     * exempt) through the {@link MessageSink}, then the cleared/by notices through the {@link CommunicationNotifier},
     * each hopping to the viewer's region thread inside the sink. Package-private so the panel test can drive it.
     */
    void doClearChat(Scheduler scheduler, CommunicationNotifier notifier, MessageSink sink, Player actor) {
        scheduler.onGlobal(() -> {
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                if (!viewer.hasPermission(CLEARCHAT_EXEMPT)) {
                    PlayerRef ref = BukkitRefs.toRef(viewer);
                    for (int line = 0; line < CLEARCHAT_BLANK_LINES; line++) {
                        sink.deliver(ref, "");
                    }
                    notifier.send(ref, CommunicationMessageKey.CLEARCHAT_CLEARED);
                }
            }
            notifier.send(
                    BukkitRefs.toRef(actor), CommunicationMessageKey.CLEARCHAT_BY, Map.of("player", actor.getName()));
        });
    }

    private void promptBroadcast(
            GuiText guiText, Scheduler scheduler, AnvilInput anvil, Player player, Runnable reopen) {
        PlayerRef viewer = BukkitRefs.toRef(player);
        var prompt = ItemBuilder.of(Material.BELL)
                .name(guiText.text(viewer, CommunicationMessageKey.GUI_BROADCAST_PROMPT))
                .build();
        scheduler.onEntity(viewer, () -> anvil.open(player, prompt, result -> onBroadcastInput(result, reopen)));
    }

    private void onBroadcastInput(AnvilResult result, Runnable reopen) {
        if (result instanceof AnvilResult.Submitted submitted) {
            submitBroadcast(submitted.text());
        }
        reopen.run();
    }

    /**
     * Send the typed line through the broadcaster (mirroring {@code /broadcast}); a blank line is a no-op.
     * Package-private so the panel test can drive the broadcast seam without opening a live anvil.
     */
    void submitBroadcast(String raw) {
        Objects.requireNonNull(raw, "raw");
        if (!raw.isBlank()) {
            broadcaster.broadcast(broadcastPrefix + raw.strip());
        }
    }

    private static String lockState(Messages messages, PlayerRef viewer, boolean locked) {
        return messages.resolve(
                viewer,
                locked ? CommunicationMessageKey.GUI_VALUE_LOCKED : CommunicationMessageKey.GUI_VALUE_UNLOCKED,
                Map.of());
    }

    private static org.bukkit.inventory.ItemStack announcerIcon(
            GuiText guiText, PlayerRef viewer, Announcement announcement) {
        Map<String, String> placeholders = Map.of(
                "id", announcement.id(),
                "lines", Integer.toString(announcement.lines().size()),
                "channels", channels(announcement));
        return ItemBuilder.of(Material.PAPER)
                .name(guiText.text(viewer, CommunicationMessageKey.GUI_ANNOUNCER_ENTRY_NAME, placeholders))
                .lore(guiText.text(viewer, CommunicationMessageKey.GUI_ANNOUNCER_ENTRY_LORE, placeholders))
                .build();
    }

    private static String channels(Announcement announcement) {
        return announcement.channels().stream()
                .map(Enum::name)
                .sorted()
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
    }
}
