package com.uxplima.uxmessentials.warps.adapter.inbound.gui;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.adapter.outbound.EngineLog;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.warps.application.port.WarpRepository;
import com.uxplima.uxmessentials.warps.domain.WarpsMessageKey;
import com.uxplima.uxmessentials.warps.domain.WelcomeMessage;
import com.uxplima.uxmlib.gui.input.InputRequest;
import com.uxplima.uxmlib.gui.input.TextInput;
import com.uxplima.uxmlib.menu.Menus;
import com.uxplima.uxmlib.menu.binding.MenuBindings;
import com.uxplima.uxmlib.menu.runtime.MenuActionContext;
import com.uxplima.uxmlib.menu.runtime.MenuContext;
import com.uxplima.uxmlib.menu.spec.MenuSpecs;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Registers the per-warp welcome-messages list editor with the menu engine and opens it. A three-row list panel for
 * one warp reached from the editor's welcome button: one entry per stored welcome message across the top two rows,
 * an add button, a back button to the editor, and a clear-all button on the bottom row. A left click on an entry
 * opens that message's own panel, a right click deletes it, and a shift click cycles its delivery type; the add
 * button writes a new message through the input seam; the clear button drops every message; the back button reopens
 * the warp editor. Each mutation saves the edited warp through the shared {@link EditableWarp} loader and re-opens
 * this list with the new subject so the operator sees the result.
 *
 * <p>The message panel offers every change the list makes, each as a left click: rewrite the text, cycle the delivery
 * type, delete the message, or go back to the list. A Bedrock player in a chest cannot send a shift click, so no
 * change to a message may need one.
 *
 * <p>A save writes the warp's row, so it runs on the async lane and never on the click's tick; the list is re-opened
 * on the viewer's entity thread once the write has finished, so the operator sees the saved result. Saves of one warp
 * run one after another, each on the list the one before it wrote, so two quick clicks or two operators editing the
 * same warp cannot overwrite each other. A click names its entry by index and content together, and finds it again
 * by content when an earlier save has moved it, so it never edits the wrong message.
 *
 * <p>The edited warp is handed in as the menu subject, its name, owner, and a welcome-message snapshot read off the
 * viewer's entity thread at open, so the entry list (the {@code warps:welcome} list source) and every entry line
 * fill from the {@code warp_welcome_*} placeholders without the renderer touching a port. The panel holds no new
 * domain logic: it runs the warp context's existing handlers through the engine, on the warp-browse list
 * pattern. The editor is injected after this view to break their re-open cycle. Every visible string resolves from
 * the warps catalog.
 */
@NullMarked
public final class WarpWelcomeMessagesView {

    /** The engine spec id this list registers and opens under. */
    public static final String SPEC_ID = "warp-welcome";

    private static final String SPEC_RESOURCE = "modules/warps/gui/warp-welcome.conf";

    /** The engine spec id the one-message panel registers and opens under. */
    public static final String ENTRY_SPEC_ID = "warp-welcome-entry";

    private static final String ENTRY_SPEC_RESOURCE = "modules/warps/gui/warp-welcome-entry.conf";
    private static final int ROWS = 3;

    /** The entry grid's capacity: the top two rows of the three-row chest, as the old fixed view drew. */
    private static final int ENTRY_LIMIT = 18;

    private final Menus menus;
    private final Scheduler scheduler;
    private final TextInput textInput;
    private final WarpEditorView editorView;
    private final EditableWarpLoader loader;

    /** The last save asked for on each warp, which the next save of that warp waits for. */
    private WarpWelcomeMessagesView(
            Menus menus,
            Scheduler scheduler,
            TextInput textInput,
            WarpEditorView editorView,
            EditableWarpLoader loader) {
        this.menus = Objects.requireNonNull(menus, "menus");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.textInput = Objects.requireNonNull(textInput, "textInput");
        this.editorView = Objects.requireNonNull(editorView, "editorView");
        this.loader = Objects.requireNonNull(loader, "loader");
    }

    /**
     * Build the welcome list over the warps wiring's collaborators. The editable-warp loader is built here from the
     * server-warp repository and the editor view (the same pair the editor loads through), so the warps wiring needs
     * only the public collaborators it already holds. Mirrors {@code WarpSoundMenu.create}.
     */
    public static WarpWelcomeMessagesView create(
            Menus menus,
            Scheduler scheduler,
            TextInput textInput,
            WarpRepository repository,
            WarpEditorView editorView) {
        EditableWarpLoader loader = new EditableWarpLoader(repository, editorView);
        return new WarpWelcomeMessagesView(menus, scheduler, textInput, editorView, loader);
    }

    /** Register the welcome list source, its entry placeholders, the action buttons, and the spec itself. */
    public void register(MenuBindings bindings, Path dataFolder, Logger log) {
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(log, "log");
        bindings.list("warps:welcome", ctx -> subject(ctx).entries());
        bindings.placeholder(
                "warp_welcome_material",
                ctx -> materialFor(shown(ctx).message().type()).name());
        bindings.placeholder(
                "warp_welcome_index", ctx -> Integer.toString(shown(ctx).index() + 1));
        bindings.placeholder("warp_welcome_text", ctx -> shown(ctx).message().message());
        bindings.placeholder("warp_welcome_type", ctx -> shown(ctx).message().type());
        // An operator's list file from before the panel binds its left click to welcome-edit, so that name opens it.
        bindings.action("warps:welcome-edit", this::openEntry);
        bindings.action("warps:welcome-remove", this::removeEntry);
        bindings.action("warps:welcome-cycle", this::cycleEntry);
        bindings.action("warps:welcome-add", this::add);
        bindings.action("warps:welcome-clear", this::clear);
        bindings.action("warps:welcome-back", this::back);
        bindings.action("warps:welcome-entry-text", this::panelText);
        bindings.action("warps:welcome-entry-cycle", this::panelCycle);
        bindings.action("warps:welcome-entry-remove", this::panelRemove);
        bindings.action("warps:welcome-entry-back", this::panelBack);
        menus.registerSpec(SPEC_ID, MenuSpecs.loadOrBundled(SPEC_RESOURCE, dataFolder, ROWS, EngineLog.of(log)));
        menus.registerSpec(
                ENTRY_SPEC_ID, MenuSpecs.loadOrBundled(ENTRY_SPEC_RESOURCE, dataFolder, ROWS, EngineLog.of(log)));
    }

    /**
     * Open the welcome list for the warp {@code warpName} ({@code warpOwner} null for a server warp). The messages
     * are read on the viewer's entity thread into a snapshot handed to the engine as the subject, so the engine
     * renders off that snapshot without a port read of its own. A warp that no longer exists closes the menu.
     */
    public void open(Player player, PlayerRef viewer, String warpName, @Nullable PlayerRef warpOwner) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(warpName, "warpName");
        scheduler.onEntity(viewer, () -> {
            EditableWarp warp = loader.load(warpName, warpOwner);
            if (warp == null) {
                player.closeInventory();
                return;
            }
            menus.open(player, SPEC_ID, snapshot(warpName, warpOwner, warp.welcomeMessages()));
        });
    }

    /** Build the menu subject: the warp's name and owner plus its welcome messages capped to the entry grid. */
    private static WelcomeList snapshot(String warpName, @Nullable PlayerRef warpOwner, List<WelcomeMessage> messages) {
        List<IndexedWelcome> entries = new ArrayList<>();
        for (int i = 0; i < Math.min(messages.size(), ENTRY_LIMIT); i++) {
            entries.add(new IndexedWelcome(i, messages.get(i)));
        }
        return new WelcomeList(warpName, warpOwner, List.copyOf(entries));
    }

    /** Left-click an entry: open the panel for that one message. */
    private void openEntry(MenuActionContext ctx) {
        openPanel(ctx.player(), BukkitRefs.toRef(ctx.viewer()), new WelcomeEntry(subject(ctx), entry(ctx)));
    }

    private void openPanel(Player player, PlayerRef viewer, WelcomeEntry panel) {
        scheduler.onEntity(viewer, () -> menus.open(player, ENTRY_SPEC_ID, panel));
    }

    /** Right-click an entry: drop it, then save and re-open. */
    private void removeEntry(MenuActionContext ctx) {
        saveThenOpen(ctx, subject(ctx), removing(entry(ctx)));
    }

    /** Shift-click an entry: cycle its delivery type, keeping its text, then save and re-open. */
    private void cycleEntry(MenuActionContext ctx) {
        saveThenOpen(ctx, subject(ctx), cycling(entry(ctx), new AtomicReference<>()));
    }

    /** The panel's text button: capture replacement text through the input seam; cancel shows the panel unchanged. */
    private void panelText(MenuActionContext ctx) {
        WelcomeEntry panel = ctx.subject(WelcomeEntry.class);
        Player player = ctx.player();
        PlayerRef viewer = BukkitRefs.toRef(ctx.viewer());
        player.closeInventory();
        textInput.prompt(
                player,
                InputRequest.of("warp.welcome", WarpsMessageKey.WARP_EDITOR_WELCOME_PROMPT.key()),
                input -> {
                    AtomicReference<@Nullable IndexedWelcome> saved = new AtomicReference<>();
                    saveThenPanel(player, viewer, panel.list(), rewriting(panel.entry(), input, saved), saved);
                },
                () -> openPanel(player, viewer, panel));
    }

    /** The panel's type button: move the message to the next delivery type, then show the panel on the result. */
    private void panelCycle(MenuActionContext ctx) {
        WelcomeEntry panel = ctx.subject(WelcomeEntry.class);
        AtomicReference<@Nullable IndexedWelcome> saved = new AtomicReference<>();
        saveThenPanel(ctx.player(), BukkitRefs.toRef(ctx.viewer()), panel.list(), cycling(panel.entry(), saved), saved);
    }

    /** The panel's delete button: drop the message, then go back to the list. */
    private void panelRemove(MenuActionContext ctx) {
        WelcomeEntry panel = ctx.subject(WelcomeEntry.class);
        saveThenOpen(ctx, panel.list(), removing(panel.entry()));
    }

    /** The panel's back button: the list again, read fresh. */
    private void panelBack(MenuActionContext ctx) {
        WelcomeList list = ctx.subject(WelcomeEntry.class).list();
        open(ctx.player(), BukkitRefs.toRef(ctx.viewer()), list.warpName(), list.warpOwner());
    }

    /** Replace the entry's text, keeping its delivery type, then save and re-open. Package-private for the test. */
    void applyEdit(Player player, PlayerRef viewer, WelcomeList list, IndexedWelcome target, String input) {
        saveThenOpen(player, viewer, list, rewriting(target, input, new AtomicReference<>()));
    }

    /** An edit that drops {@code target}, or saves nothing when it is gone. */
    private static Function<List<WelcomeMessage>, @Nullable List<WelcomeMessage>> removing(IndexedWelcome target) {
        return messages -> {
            int at = locate(messages, target);
            if (at < 0) {
                return null;
            }
            messages.remove(at);
            return messages;
        };
    }

    /** An edit that gives {@code target} new text, keeping its type, and records the message as saved. */
    private static Function<List<WelcomeMessage>, @Nullable List<WelcomeMessage>> rewriting(
            IndexedWelcome target, String text, AtomicReference<@Nullable IndexedWelcome> saved) {
        return messages -> {
            int at = locate(messages, target);
            if (at < 0) {
                return null;
            }
            WelcomeMessage changed = new WelcomeMessage(text, messages.get(at).type());
            messages.set(at, changed);
            saved.set(new IndexedWelcome(at, changed));
            return messages;
        };
    }

    /** An edit that moves {@code target} to the next delivery type, keeping its text, and records it as saved. */
    private static Function<List<WelcomeMessage>, @Nullable List<WelcomeMessage>> cycling(
            IndexedWelcome target, AtomicReference<@Nullable IndexedWelcome> saved) {
        return messages -> {
            int at = locate(messages, target);
            if (at < 0) {
                return null;
            }
            WelcomeMessage existing = messages.get(at);
            WelcomeMessage changed = new WelcomeMessage(existing.message(), nextType(existing.type()));
            messages.set(at, changed);
            saved.set(new IndexedWelcome(at, changed));
            return messages;
        };
    }

    /**
     * Save a panel edit, then show the panel again on the message as it was saved. When an earlier save removed the
     * message, nothing is saved and there is no panel to show, so the list opens instead.
     */
    private void saveThenPanel(
            Player player,
            PlayerRef viewer,
            WelcomeList list,
            Function<List<WelcomeMessage>, @Nullable List<WelcomeMessage>> edit,
            AtomicReference<@Nullable IndexedWelcome> saved) {
        save(player, viewer, list, edit, () -> {
            IndexedWelcome now = saved.get();
            if (now == null) {
                open(player, viewer, list.warpName(), list.warpOwner());
            } else {
                openPanel(player, viewer, new WelcomeEntry(list, now));
            }
        });
    }

    /**
     * Where {@code target} is in the live list: at its index while that still holds the same message, otherwise
     * wherever an earlier save moved it, or -1 when it is gone.
     */
    private static int locate(List<WelcomeMessage> messages, IndexedWelcome target) {
        int at = target.index();
        if (at >= 0 && at < messages.size() && messages.get(at).equals(target.message())) {
            return at;
        }
        return messages.indexOf(target.message());
    }

    /** Click add: capture a new message through the input seam; cancel re-opens the list unchanged. */
    private void add(MenuActionContext ctx) {
        WelcomeList list = subject(ctx);
        Player player = ctx.player();
        PlayerRef viewer = BukkitRefs.toRef(ctx.viewer());
        player.closeInventory();
        textInput.prompt(
                player,
                InputRequest.of("warp.welcome", WarpsMessageKey.WARP_EDITOR_WELCOME_PROMPT.key()),
                input -> applyAdd(player, viewer, list, input),
                () -> open(player, viewer, list.warpName(), list.warpOwner()));
    }

    /** Append a new CHAT-typed message, then save and re-open. Package-private for the golden test. */
    void applyAdd(Player player, PlayerRef viewer, WelcomeList list, String input) {
        saveThenOpen(player, viewer, list, messages -> {
            messages.add(new WelcomeMessage(input, "CHAT"));
            return messages;
        });
    }

    /** Click clear: drop every message, then save and re-open. */
    private void clear(MenuActionContext ctx) {
        saveThenOpen(ctx, subject(ctx), messages -> new ArrayList<>());
    }

    /** Click back: reopen the warp editor for this warp. */
    private void back(MenuActionContext ctx) {
        WelcomeList list = subject(ctx);
        editorView.open(ctx.player(), BukkitRefs.toRef(ctx.viewer()), list.warpName(), list.warpOwner());
    }

    private void saveThenOpen(
            MenuActionContext ctx,
            WelcomeList list,
            Function<List<WelcomeMessage>, @Nullable List<WelcomeMessage>> edit) {
        saveThenOpen(ctx.player(), BukkitRefs.toRef(ctx.viewer()), list, edit);
    }

    /**
     * Apply {@code edit} to the warp's live welcome messages and save them on the async lane, then re-open the list on
     * the viewer's thread. The edit gets a mutable copy and answers the list to save, or null to save nothing; a warp
     * that is gone saves nothing. The save goes through the editor's queue for the warp, so it waits for any other
     * edit of the same warp and reads what that one wrote. The list re-opens even when the save throws.
     */
    private void saveThenOpen(
            Player player,
            PlayerRef viewer,
            WelcomeList list,
            Function<List<WelcomeMessage>, @Nullable List<WelcomeMessage>> edit) {
        save(player, viewer, list, edit, () -> open(player, viewer, list.warpName(), list.warpOwner()));
    }

    /** As {@link #saveThenOpen}, running {@code then} on the viewer's thread once the save has finished. */
    private void save(
            Player player,
            PlayerRef viewer,
            WelcomeList list,
            Function<List<WelcomeMessage>, @Nullable List<WelcomeMessage>> edit,
            Runnable then) {
        editorView.edit(
                list.warpName(),
                list.warpOwner(),
                player,
                viewer,
                warp -> {
                    List<WelcomeMessage> next = edit.apply(new ArrayList<>(warp.welcomeMessages()));
                    if (next != null) {
                        warp.setWelcomeMessages(next);
                    }
                },
                then);
    }

    private static Material materialFor(String type) {
        return switch (type.toUpperCase(Locale.ROOT)) {
            case "CHAT" -> Material.PAPER;
            case "ACTION_BAR" -> Material.REPEATER;
            case "TITLE" -> Material.GOLDEN_HELMET;
            case "SUBTITLE" -> Material.IRON_HELMET;
            case "BOSS_BAR" -> Material.DRAGON_EGG;
            default -> Material.WRITABLE_BOOK;
        };
    }

    private static String nextType(String type) {
        return switch (type.toUpperCase(Locale.ROOT)) {
            case "CHAT" -> "ACTION_BAR";
            case "ACTION_BAR" -> "TITLE";
            case "TITLE" -> "SUBTITLE";
            case "SUBTITLE" -> "BOSS_BAR";
            default -> "CHAT";
        };
    }

    private WelcomeList subject(MenuContext ctx) {
        return ctx.subject(WelcomeList.class);
    }

    private WelcomeList subject(MenuActionContext ctx) {
        return ctx.subject(WelcomeList.class);
    }

    private IndexedWelcome entry(MenuActionContext ctx) {
        return ctx.entry(IndexedWelcome.class);
    }

    /** The message a line is drawn for: the list row it sits on, or the one message a panel shows. */
    private IndexedWelcome shown(MenuContext ctx) {
        if (ctx.entry().orElse(null) instanceof IndexedWelcome row) {
            return row;
        }
        return ctx.subject(WelcomeEntry.class).entry();
    }

    /**
     * The subject of an open welcome list: the warp's name, its owner ({@code null} for a server warp), and its
     * welcome messages capped to the entry grid, paired with their slot index. The list source and the entry
     * placeholders read this, so the menu carries no port read of its own.
     *
     * @param warpName the warp's name
     * @param warpOwner the player warp's owner, or {@code null} for a server warp
     * @param entries the indexed welcome messages, in order
     */
    public record WelcomeList(String warpName, @Nullable PlayerRef warpOwner, List<IndexedWelcome> entries) {

        public WelcomeList {
            Objects.requireNonNull(warpName, "warpName");
            entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
        }
    }

    /**
     * One welcome message paired with its slot index, so the entry's name line can show {@code Message #N} and an
     * action knows which message the click targeted.
     *
     * @param index the entry's zero-based position in the list
     * @param message the welcome message
     */
    public record IndexedWelcome(int index, WelcomeMessage message) {

        public IndexedWelcome {
            Objects.requireNonNull(message, "message");
        }
    }

    /**
     * The subject of an open message panel: the list it was opened from, which names the warp, and the one message
     * it shows.
     *
     * @param list the welcome list the panel belongs to
     * @param entry the message the panel shows
     */
    public record WelcomeEntry(WelcomeList list, IndexedWelcome entry) {

        public WelcomeEntry {
            Objects.requireNonNull(list, "list");
            Objects.requireNonNull(entry, "entry");
        }
    }
}
