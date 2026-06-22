package com.uxplima.uxmessentials.shared.adapter.inbound.gui;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.message.GuiMessageKey;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.gui.Guis;
import com.uxplima.uxmlib.gui.PaginatedGui;
import com.uxplima.uxmlib.gui.anvil.AnvilInput;
import com.uxplima.uxmlib.gui.anvil.AnvilResult;
import com.uxplima.uxmlib.gui.item.GuiItem;
import com.uxplima.uxmlib.item.ItemBuilder;
import com.uxplima.uxmlib.item.SkullData;
import org.jspecify.annotations.NullMarked;

/**
 * A reusable target-picker menu: a paginated grid of the online players' heads, plus a fixed
 * "custom / offline name" button that opens a vanilla anvil so a staff member can type a name the
 * grid does not show (an offline target). Clicking a head, or submitting a name the supplied resolver
 * recognises, invokes the caller's {@code onPick} with the chosen {@link PlayerRef}; an unresolvable
 * typed name replies with the caller's unknown-player {@link MessageKey} and reopens the picker.
 *
 * <p>The view holds no feature logic. One instance is shared across callers — the framework
 * collaborators (text, scheduler, anvil, server) live on the instance, and the per-use parts (the
 * title, the pick callback, the offline-name resolver, and the unknown-player reply key) are passed to
 * {@link #open}. The moderation {@code /ban} and {@code /mute} GUI flows reuse it; the offline resolver
 * a caller passes is its own (moderation backs it with {@code BukkitTargetResolver}), so this class
 * never reaches for a context's lookup itself.
 *
 * <p>Folia: the online roster is enumerated on the global region thread (iterating
 * {@code Server.getOnlinePlayers()} off it is illegal) and snapshotted to plain {@link PlayerRef}s; the
 * menu is then built and opened on the viewer's own entity region thread, where its clicks also run. The
 * anvil resolver call is hopped to async because an offline-name resolution may block, then the result
 * is delivered back on the viewer's entity thread. Pagination flows through {@link PaginatedGui}, which
 * windows an arbitrarily long roster across pages, so a 500-player roster pages cleanly.
 */
@NullMarked
public final class PlayerPickerView {

    private static final int PICKER_ROWS = 6;
    private static final Material OFFLINE_BUTTON = Material.NAME_TAG;
    private static final int OFFLINE_BUTTON_SLOT = 49;
    private static final int PREV_SLOT = 45;
    private static final int NEXT_SLOT = 53;
    private static final Material NAV_ICON = Material.ARROW;
    private static final Material FILLER = Material.GRAY_STAINED_GLASS_PANE;

    private final GuiText guiText;
    private final Scheduler scheduler;
    private final AnvilInput anvil;
    private final Server server;
    private final Messages messages;
    private final MessageSink sink;

    public PlayerPickerView(
            GuiText guiText,
            Scheduler scheduler,
            AnvilInput anvil,
            Server server,
            Messages messages,
            MessageSink sink) {
        this.guiText = Objects.requireNonNull(guiText, "guiText");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.anvil = Objects.requireNonNull(anvil, "anvil");
        this.server = Objects.requireNonNull(server, "server");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.sink = Objects.requireNonNull(sink, "sink");
    }

    /**
     * Open the picker for {@code viewer}: enumerate the online roster on the global thread, then build and
     * open the head grid on the viewer's entity thread. A head click or a resolved offline name fires
     * {@code request.onPick}.
     */
    public void open(Player viewer, PlayerRef viewerRef, Request request) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(viewerRef, "viewerRef");
        Objects.requireNonNull(request, "request");
        scheduler.onGlobal(() -> {
            List<PlayerRef> roster =
                    server.getOnlinePlayers().stream().map(BukkitRefs::toRef).toList();
            scheduler.onEntity(viewerRef, () -> buildAndOpen(viewer, viewerRef, request, roster));
        });
    }

    private void buildAndOpen(Player viewer, PlayerRef viewerRef, Request request, List<PlayerRef> roster) {
        PaginatedGui gui = Guis.paginated()
                .title(guiText.text(viewerRef, request.title()))
                .rows(PICKER_ROWS)
                .contentSlots(contentSlots())
                .build();
        fill(gui);
        for (PlayerRef candidate : roster) {
            gui.addPageItem(GuiItem.button(
                    head(viewerRef, candidate), e -> request.onPick().accept(candidate)));
        }
        gui.set(PREV_SLOT, GuiItem.previousPage(gui, navIcon(viewerRef, GuiMessageKey.PLAYER_PICKER_PREV)));
        gui.set(NEXT_SLOT, GuiItem.nextPage(gui, navIcon(viewerRef, GuiMessageKey.PLAYER_PICKER_NEXT)));
        gui.set(
                OFFLINE_BUTTON_SLOT,
                GuiItem.button(offlineButton(viewerRef), e -> promptOffline(viewer, viewerRef, request)));
        gui.open(viewer);
    }

    /** Open the anvil for a typed name; a submission flows through {@link #resolveTyped}. */
    private void promptOffline(Player viewer, PlayerRef viewerRef, Request request) {
        scheduler.onEntity(
                viewerRef,
                () -> anvil.open(viewer, offlinePrompt(viewerRef), result -> {
                    if (result instanceof AnvilResult.Submitted submitted) {
                        resolveTyped(viewer, viewerRef, request, submitted.text());
                    } else {
                        open(viewer, viewerRef, request);
                    }
                }));
    }

    /**
     * Resolve the typed name through the caller's offline resolver off the tick thread (a profile lookup may
     * block), then act on the viewer's entity thread: an unresolved name replies with the unknown-player key
     * and reopens, a resolved name fires the pick callback. Package-private so the resolve branch is unit-tested
     * without driving a live anvil — the sync test scheduler runs callbacks inline.
     */
    void resolveTyped(Player viewer, PlayerRef viewerRef, Request request, String input) {
        String name = input.strip();
        scheduler.async(() -> {
            Optional<PlayerRef> resolved = request.offlineResolver().apply(name);
            scheduler.onEntity(viewerRef, () -> {
                if (resolved.isEmpty()) {
                    sink.deliver(
                            viewerRef, messages.resolve(viewerRef, request.unknownPlayerKey(), Map.of("player", name)));
                    open(viewer, viewerRef, request);
                    return;
                }
                request.onPick().accept(resolved.get());
            });
        });
    }

    private ItemStack head(PlayerRef viewer, PlayerRef candidate) {
        return ItemBuilder.of(Material.PLAYER_HEAD)
                .name(guiText.text(viewer, GuiMessageKey.PLAYER_PICKER_HEAD_NAME, Map.of("player", candidate.name())))
                .lore(List.of(guiText.text(viewer, GuiMessageKey.PLAYER_PICKER_HEAD_LORE)))
                .skull(SkullData.ofUuid(candidate.uuid()))
                .build();
    }

    private ItemStack offlineButton(PlayerRef viewer) {
        return ItemBuilder.of(OFFLINE_BUTTON)
                .name(guiText.text(viewer, GuiMessageKey.PLAYER_PICKER_CUSTOM))
                .lore(List.of(guiText.text(viewer, GuiMessageKey.PLAYER_PICKER_CUSTOM_LORE)))
                .build();
    }

    private ItemStack offlinePrompt(PlayerRef viewer) {
        return ItemBuilder.of(OFFLINE_BUTTON)
                .name(guiText.text(viewer, GuiMessageKey.PLAYER_PICKER_CUSTOM_PROMPT))
                .build();
    }

    private ItemStack navIcon(PlayerRef viewer, MessageKey key) {
        return ItemBuilder.of(NAV_ICON).name(guiText.text(viewer, key)).build();
    }

    private void fill(PaginatedGui gui) {
        ItemStack filler = ItemBuilder.of(FILLER).name(Component.empty()).build();
        List<Integer> content = contentSlots();
        for (int slot = 0; slot < PICKER_ROWS * 9; slot++) {
            if (!content.contains(slot)) {
                gui.set(slot, GuiItem.display(filler));
            }
        }
    }

    private static List<Integer> contentSlots() {
        // The five upper rows hold heads; the bottom row carries the nav arrows and the offline button.
        return java.util.stream.IntStream.range(0, (PICKER_ROWS - 1) * 9)
                .boxed()
                .toList();
    }

    /**
     * One picker invocation's caller-supplied parts, keeping {@link PlayerPickerView} generic: the menu title,
     * the callback fired with the chosen target, the resolver that turns a typed offline name into a
     * {@link PlayerRef}, and the reply key used when a typed name resolves to nothing.
     *
     * @param title the menu-title catalog key
     * @param onPick invoked with the chosen target (a clicked head, or a resolved typed name)
     * @param offlineResolver maps a typed name to a target, or empty when the name is unknown
     * @param unknownPlayerKey the reply key for an unresolvable typed name (filled with {@code {player}})
     */
    public record Request(
            MessageKey title,
            Consumer<PlayerRef> onPick,
            Function<String, Optional<PlayerRef>> offlineResolver,
            MessageKey unknownPlayerKey) {

        public Request {
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(onPick, "onPick");
            Objects.requireNonNull(offlineResolver, "offlineResolver");
            Objects.requireNonNull(unknownPlayerKey, "unknownPlayerKey");
        }
    }
}
