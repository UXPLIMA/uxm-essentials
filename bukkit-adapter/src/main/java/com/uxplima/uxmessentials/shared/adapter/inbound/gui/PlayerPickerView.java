package com.uxplima.uxmessentials.shared.adapter.inbound.gui;

import java.util.ArrayList;
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

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.InputRequest;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.EntityListSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.message.GuiMessageKey;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.item.ItemBuilder;
import com.uxplima.uxmlib.item.SkullData;
import org.jspecify.annotations.NullMarked;

/**
 * A reusable target-picker menu: a paginated grid of the online players' heads, plus a fixed "custom / offline name"
 * button that opens an anvil so a staff member can type a name the grid does not show (an offline target). Clicking a
 * head, or submitting a name the supplied resolver recognises, invokes the caller's {@code onPick} with the chosen
 * {@link PlayerRef}; an unresolvable typed name replies with the caller's unknown-player {@link MessageKey} and reopens
 * the picker.
 *
 * <p>The view holds no feature logic. One instance is shared across callers — the framework collaborators (the menu
 * engine, text, scheduler, anvil, server) live on the instance, and the per-use parts (the title, the pick callback,
 * the offline-name resolver, and the unknown-player reply key) are passed to {@link #open}. The moderation
 * {@code /ban} and {@code /mute} GUI flows reuse it; the offline resolver a caller passes is its own (moderation backs
 * it with {@code PlayerLookupTargetResolver}), so this class never reaches for a context's lookup itself.
 *
 * <p>A caller may also supply optional {@link Request#footerButtons() footer buttons} — extra bottom-row actions that
 * are not a player pick (the jail hub uses two: a "jails" manager and a "jailed players" list). They default to empty,
 * so the sanction callers that only pick a target are unaffected; a supplied button renders in a free bottom-row slot
 * and fires its own callback with the live viewer.
 *
 * <p>It draws through the menu engine's paginated-list runtime ({@link Menus#openList}) over an {@link EntityListSpec}: a
 * holder-backed engine list routed and torn down by the one menu listener and one {@code closeMenu}, with paging
 * re-paginating the same holder so a 500-player roster pages cleanly. The heads are the listed entities (their
 * {@code onSelect} the pick callback), and the offline-name button plus any footer buttons are the spec's fixed
 * {@link EntityListSpec.ExtraButton extra buttons}.
 *
 * <p>Folia: the online roster is enumerated on the global region thread (iterating
 * {@code Server.getOnlinePlayers()} off it is illegal) and snapshotted to plain {@link PlayerRef}s; the engine then
 * builds and opens the menu on the viewer's own entity region thread, where its clicks also run. The anvil resolver
 * call is hopped to async because an offline-name resolution may block, then the result is delivered back on the
 * viewer's entity thread.
 */
@NullMarked
public final class PlayerPickerView {

    private static final int PICKER_ROWS = 6;
    private static final String INPUT_KEY = "picker.player-name";
    private static final Material OFFLINE_BUTTON = Material.NAME_TAG;
    private static final int OFFLINE_BUTTON_SLOT = 49;
    private static final int PREV_SLOT = 45;
    private static final int NEXT_SLOT = 53;
    private static final Material NAV_ICON = Material.ARROW;
    private static final Material FILLER = Material.GRAY_STAINED_GLASS_PANE;
    private static final List<Integer> CONTENT_SLOTS = contentSlots();

    // The bottom row's free slots, flanking the offline-name button, where optional footer buttons land.
    private static final int[] FOOTER_SLOTS = {47, 51};

    private final Menus menus;
    private final GuiText guiText;
    private final Scheduler scheduler;
    private final TextInput textInput;
    private final Server server;
    private final Messages messages;
    private final MessageSink sink;

    public PlayerPickerView(
            Menus menus,
            GuiText guiText,
            Scheduler scheduler,
            TextInput textInput,
            Server server,
            Messages messages,
            MessageSink sink) {
        this.menus = Objects.requireNonNull(menus, "menus");
        this.guiText = Objects.requireNonNull(guiText, "guiText");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.textInput = Objects.requireNonNull(textInput, "textInput");
        this.server = Objects.requireNonNull(server, "server");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.sink = Objects.requireNonNull(sink, "sink");
    }

    /**
     * Open the picker for {@code viewer}: enumerate the online roster on the global thread, then open the head grid
     * through the engine on the viewer's entity thread. A head click or a resolved offline name fires
     * {@code request.onPick}.
     */
    public void open(Player viewer, PlayerRef viewerRef, Request request) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(viewerRef, "viewerRef");
        Objects.requireNonNull(request, "request");
        scheduler.onGlobal(() -> {
            // Route the roster through the viewer's canSee graph so a vanished player the viewer may not see is
            // never offered as a pick target; the viewer always sees themselves (canSee-self is true).
            List<PlayerRef> roster = server.getOnlinePlayers().stream()
                    .filter(viewer::canSee)
                    .map(BukkitRefs::toRef)
                    .toList();
            menus.openList(viewerRef, spec(viewerRef, request, roster));
        });
    }

    /**
     * Build the engine {@link EntityListSpec} for one open: the roster as the listed entities, the per-head icon renderer,
     * an {@code onSelect} that runs the caller's {@code onPick}, and the offline-name button plus any footer buttons
     * as the spec's fixed extra buttons. The geometry — six rows, content slots 0..44, prev/next at 45/53, the offline
     * button at 49, footer buttons at 47/51, gray-glass filler — matches the old view slot-for-slot.
     */
    private EntityListSpec spec(PlayerRef viewerRef, Request request, List<PlayerRef> roster) {
        return EntityListSpec.builder()
                .title(guiText.text(viewerRef, request.title()))
                .rows(PICKER_ROWS)
                .contentSlots(CONTENT_SLOTS)
                .navigation(PREV_SLOT, NEXT_SLOT, NAV_ICON)
                .navNames(
                        guiText.text(viewerRef, GuiMessageKey.PLAYER_PICKER_PREV),
                        guiText.text(viewerRef, GuiMessageKey.PLAYER_PICKER_NEXT))
                .filler(FILLER)
                .entities(() -> List.<Object>copyOf(roster))
                .iconRenderer((v, entity) -> head(v, (PlayerRef) entity))
                .onSelect((player, entity) -> request.onPick().accept((PlayerRef) entity))
                .extraButtons(extraButtons(viewerRef, request))
                .build();
    }

    /** The offline-name anvil button plus the caller's optional footer buttons, each firing with the live viewer. */
    private List<EntityListSpec.ExtraButton> extraButtons(PlayerRef viewerRef, Request request) {
        List<EntityListSpec.ExtraButton> buttons = new ArrayList<>();
        buttons.add(new EntityListSpec.ExtraButton(
                OFFLINE_BUTTON_SLOT, offlineButton(viewerRef), p -> promptOffline(p, viewerRef, request)));
        List<FooterButton> footers = request.footerButtons();
        for (int i = 0; i < footers.size() && i < FOOTER_SLOTS.length; i++) {
            FooterButton footer = footers.get(i);
            buttons.add(
                    new EntityListSpec.ExtraButton(FOOTER_SLOTS[i], footerIcon(viewerRef, footer), footer.onClick()));
        }
        return buttons;
    }

    private ItemStack footerIcon(PlayerRef viewer, FooterButton footer) {
        return ItemBuilder.of(footer.icon())
                .name(guiText.text(viewer, footer.label()))
                .lore(List.of(guiText.text(viewer, footer.lore())))
                .build();
    }

    /** Open the input prompt for a typed name; a submission flows through {@link #resolveTyped}. */
    private void promptOffline(Player viewer, PlayerRef viewerRef, Request request) {
        textInput.prompt(
                viewer,
                viewerRef,
                InputRequest.of(INPUT_KEY, GuiMessageKey.PLAYER_PICKER_CUSTOM_PROMPT),
                text -> resolveTyped(viewer, viewerRef, request, text),
                () -> open(viewer, viewerRef, request));
    }

    /**
     * Resolve the typed name through the caller's offline resolver off the tick thread (a profile lookup may block),
     * then act on the viewer's entity thread: an unresolved name replies with the unknown-player key and reopens, a
     * resolved name fires the pick callback. Package-private so the resolve branch is unit-tested without driving a
     * live anvil — the sync test scheduler runs callbacks inline.
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

    private static List<Integer> contentSlots() {
        // The five upper rows hold heads; the bottom row carries the nav arrows and the offline button.
        return java.util.stream.IntStream.range(0, (PICKER_ROWS - 1) * 9)
                .boxed()
                .toList();
    }

    /**
     * One picker invocation's caller-supplied parts, keeping {@link PlayerPickerView} generic: the menu title, the
     * callback fired with the chosen target, the resolver that turns a typed offline name into a {@link PlayerRef}, the
     * reply key used when a typed name resolves to nothing, and any extra footer buttons.
     *
     * <p>The four-argument constructor is the common case (the sanction flows that only pick a target); it defaults
     * {@link #footerButtons} to empty so those callers are unchanged. The jail hub uses the full constructor to add its
     * [Jails] and [Jailed players] actions.
     *
     * @param title the menu-title catalog key
     * @param onPick invoked with the chosen target (a clicked head, or a resolved typed name)
     * @param offlineResolver maps a typed name to a target, or empty when the name is unknown
     * @param unknownPlayerKey the reply key for an unresolvable typed name (filled with {@code {player}})
     * @param footerButtons extra bottom-row buttons that are not a player pick (empty for the sanction callers)
     */
    public record Request(
            MessageKey title,
            Consumer<PlayerRef> onPick,
            Function<String, Optional<PlayerRef>> offlineResolver,
            MessageKey unknownPlayerKey,
            List<FooterButton> footerButtons) {

        public Request {
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(onPick, "onPick");
            Objects.requireNonNull(offlineResolver, "offlineResolver");
            Objects.requireNonNull(unknownPlayerKey, "unknownPlayerKey");
            footerButtons = List.copyOf(Objects.requireNonNull(footerButtons, "footerButtons"));
        }

        /** The common case: a picker with no extra footer buttons (just the target heads and the offline anvil). */
        public Request(
                MessageKey title,
                Consumer<PlayerRef> onPick,
                Function<String, Optional<PlayerRef>> offlineResolver,
                MessageKey unknownPlayerKey) {
            this(title, onPick, offlineResolver, unknownPlayerKey, List.of());
        }
    }

    /**
     * An optional bottom-row picker button that performs an action other than picking a player — its name and lore
     * catalog keys, its icon material, and the callback fired (with the live viewer) when it is clicked. The jail hub
     * adds two: a jails manager and a jailed-players list.
     *
     * @param label the button-name catalog key
     * @param lore the button-lore catalog key
     * @param icon the button material
     * @param onClick invoked with the viewer when the button is clicked
     */
    public record FooterButton(MessageKey label, MessageKey lore, Material icon, Consumer<Player> onClick) {

        public FooterButton {
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(lore, "lore");
            Objects.requireNonNull(icon, "icon");
            Objects.requireNonNull(onClick, "onClick");
        }
    }
}
