package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.ActionRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.ConditionRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.ListSourceRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ConfirmRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.EditorRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ListViewRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.SelectorRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.ActionArguments;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.ConfirmState;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.EditorRefresh;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.EditorState;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.LastMenu;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.ListViewState;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuActionContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuHolder;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuRefresh;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.SelectorState;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ClickKind;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuItemSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.Ref;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.RefreshSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.ChildClickHandler;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.ConfirmOpener;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.SelectorButton;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.SelectorOpener;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.Nullable;

/**
 * The one entry point a feature uses to open a registered menu for a viewer. A feature registers its specs once at
 * wiring time, then calls {@link #open} to show one to a player. The façade first resolves every list source the
 * spec names off any tick thread — a list source may read a database, which must never block the viewer's region
 * thread on Folia — then hops onto the viewer's entity thread (where the live inventory may legally be touched),
 * builds the {@link MenuHolder} that owns every per-open piece of state, caches the resolved lists on it, renders
 * the spec into a fresh inventory the holder backs, and arms the refresh task. Pagination and refresh re-render
 * from the holder's cache, so a page flip never re-queries. The click listener recovers all of this from the
 * window alone, so no player-keyed side map is needed.
 */
public final class Menus {

    /** Operator diagnostics for a menu that names an inventory type the server rejects — logged, then a chest opens. */
    private static final Logger LOG = Logger.getLogger(Menus.class.getName());

    private final MenuRenderer renderer;
    private final Scheduler scheduler;
    private final ListSourceRegistry lists;

    /**
     * The editor renderer, present only on an engine wired for typed property editors. A list/spec-only engine (most
     * test fixtures) leaves it null and never calls {@link #openEditor}; opening an editor on such an engine is a
     * wiring error that fails loudly rather than half-rendering.
     */
    @Nullable private final EditorRenderer editorRenderer;

    /**
     * The action registry an open runs a spec's {@code open-actions} through, and the condition registry it gates a
     * spec's {@code open-requirement} on. Both are null on an engine wired without them — every list/spec-only test
     * fixture — in which case an open neither gates nor fires open-actions, byte-identical to before this seam
     * existed. Only production wiring, which has the fully populated registries, passes them; a spec with no
     * open-requirement/open-actions is unaffected either way.
     */
    @Nullable private final ActionRegistry openActionRegistry;

    @Nullable private final ConditionRegistry openConditionRegistry;

    /**
     * The per-player reopen tracker {@code /menu last} reads. Null on every engine wired without it — every
     * list/spec-only test fixture — in which case an open records nothing, byte-identical to before this seam
     * existed. Only production wiring, which builds one tracker and shares it with the {@code /menu} command,
     * passes it; and even then only a subject-less open (a custom menu) is remembered, in {@link #rememberLastOpen}.
     */
    @Nullable private final LastMenu lastMenu;

    /** Paints the two-button confirm window; stateless, so one instance serves every confirm open. */
    private final ConfirmRenderer confirmRenderer = new ConfirmRenderer();

    /** Paints a selector child window; stateless, so one instance serves every picker open. */
    private final SelectorRenderer selectorRenderer = new SelectorRenderer();

    /** Paints a paginated entity list; stateless, so one instance serves every list open. */
    private final ListViewRenderer listViewRenderer = new ListViewRenderer();

    /** The opener a property's click hook calls to show its picker as an engine child window; wraps this façade. */
    private final SelectorOpener selectorOpener = this::openSelector;

    /** The opener a property's click hook calls to gate a removal behind an engine confirm child; wraps this façade. */
    private final ConfirmOpener confirmOpener = this::confirm;

    private final Map<String, MenuSpec> specs = new ConcurrentHashMap<>();

    public Menus(MenuRenderer renderer, Scheduler scheduler, ListSourceRegistry lists) {
        this(renderer, scheduler, lists, null);
    }

    public Menus(
            MenuRenderer renderer,
            Scheduler scheduler,
            ListSourceRegistry lists,
            @Nullable EditorRenderer editorRenderer) {
        this(renderer, scheduler, lists, editorRenderer, null, null);
    }

    /**
     * The action/condition-aware constructor: the same engine plus the registries an open needs to run a spec's
     * {@code open-actions} and gate on its {@code open-requirement}. Delegates to the canonical constructor with a
     * {@code null} reopen tracker, so every existing {@code new Menus(...)} call-site (almost all test fixtures)
     * compiles unchanged and records no reopen target — byte-identical to before that seam existed.
     */
    public Menus(
            MenuRenderer renderer,
            Scheduler scheduler,
            ListSourceRegistry lists,
            @Nullable EditorRenderer editorRenderer,
            @Nullable ActionRegistry openActionRegistry,
            @Nullable ConditionRegistry openConditionRegistry) {
        this(renderer, scheduler, lists, editorRenderer, openActionRegistry, openConditionRegistry, null);
    }

    /**
     * The canonical constructor production wiring uses: the action/condition-aware engine plus the reopen tracker
     * {@code /menu last} reads. Every other constructor delegates here with {@code null} for the parameters it does
     * not carry, so the roughly ninety existing {@code new Menus(...)} call-sites compile unchanged and open exactly
     * as before — with null registries an open skips the requirement gate and runs no open-actions, and with a null
     * tracker it records no reopen target. Only production wiring, which has the fully populated registries and the
     * shared tracker, passes them.
     */
    public Menus(
            MenuRenderer renderer,
            Scheduler scheduler,
            ListSourceRegistry lists,
            @Nullable EditorRenderer editorRenderer,
            @Nullable ActionRegistry openActionRegistry,
            @Nullable ConditionRegistry openConditionRegistry,
            @Nullable LastMenu lastMenu) {
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.lists = Objects.requireNonNull(lists, "lists");
        this.editorRenderer = editorRenderer;
        this.openActionRegistry = openActionRegistry;
        this.openConditionRegistry = openConditionRegistry;
        this.lastMenu = lastMenu;
    }

    /** Registers a parsed spec under its id; a feature does this once at wiring time. */
    public void registerSpec(String id, MenuSpec spec) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(spec, "spec");
        specs.put(id, spec);
    }

    /**
     * Open the spec registered under {@code specId} for {@code viewer}, carrying {@code subject} as the domain
     * object the menu is about (or null for a subject-less menu). An unknown spec id is a coding error in the
     * caller's wiring, so it fails loudly here rather than opening an empty window a player would meet.
     */
    public void open(PlayerRef viewer, String specId, @Nullable Object subject) {
        open(viewer, specId, subject, 0);
    }

    /**
     * Open the spec registered under {@code specId} for {@code viewer} at {@code page}, the same as
     * {@link #open(PlayerRef, String, Object)} but starting on a chosen page rather than page zero — what an
     * {@code open:<menu> [page]} action reaches for. A negative page is clamped to zero. An unknown spec id is a
     * caller wiring error, so it fails loudly here rather than opening an empty window.
     */
    public void open(PlayerRef viewer, String specId, @Nullable Object subject, int page) {
        open(viewer, specId, subject, page, Map.of());
    }

    /**
     * Open the spec registered under {@code specId} for {@code viewer} at {@code page}, carrying the typed command
     * {@code arguments} the menu was opened with — an operator {@code command {}} block's {@code %argument_<name>%}
     * values, keyed by argument name. The arguments ride the {@link MenuContext} to the renderer so a title, item
     * name or lore can expand them. A negative page is clamped to zero; an unknown spec id fails loudly here.
     */
    public void open(
            PlayerRef viewer, String specId, @Nullable Object subject, int page, Map<String, String> arguments) {
        openInternal(viewer, specId, subject, page, arguments, viewer, true);
    }

    /**
     * Open the spec for {@code viewer}, the player who <em>sees</em> the menu, while attributing the open to
     * {@code executor}, the player who <em>triggered</em> it — the overload {@code /menu open <name> <target>} reaches
     * for. The viewer is the target, so {@code %player%} and every player-scoped placeholder still resolve against the
     * target as with any open; the executor rides the {@link MenuContext} so the menu the target sees can expand
     * {@code %executor%} to the opener, distinct from {@code %player%}. An ordinary self-open passes the viewer as the
     * executor (the other overloads do this for you), so the two read the same. A negative page is clamped to zero; an
     * unknown spec id fails loudly here.
     */
    public void open(
            PlayerRef viewer,
            String specId,
            @Nullable Object subject,
            int page,
            Map<String, String> arguments,
            PlayerRef executor) {
        openInternal(viewer, specId, subject, page, arguments, executor, true);
    }

    /**
     * The shared open body every public {@link #open} overload and the internal {@link #reopen} route through. It
     * resolves the spec, clamps the page, resolves list sources off the tick thread, then shows the window on the
     * viewer's entity thread. {@code executor} is who triggered the open — the viewer itself for a self-open, the
     * opener for an open-for-another — carried through so {@code %executor%} can name it distinctly from the viewer's
     * {@code %player%}. {@code record} decides whether the open joins the viewer's {@code /menu last} / back history: a
     * fresh open records (subject permitting), a back-step or reopen-last replays an already-recorded open and must not
     * push it again — otherwise stepping back would immediately re-stack what it just popped.
     */
    private void openInternal(
            PlayerRef viewer,
            String specId,
            @Nullable Object subject,
            int page,
            Map<String, String> arguments,
            PlayerRef executor,
            boolean record) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(specId, "specId");
        Objects.requireNonNull(arguments, "arguments");
        Objects.requireNonNull(executor, "executor");
        MenuSpec spec = specs.get(specId);
        if (spec == null) {
            throw new IllegalArgumentException("no menu spec registered under id: " + specId);
        }
        int startPage = Math.max(0, page);
        Map<String, String> args = Map.copyOf(arguments);
        MenuContext ctx = MenuContext.of(viewer, subject, startPage, args)
                .withLocalPlaceholders(spec.placeholders())
                .withExecutor(executor);
        scheduler.async(() -> {
            Map<String, List<?>> resolved = resolveLists(spec, ctx);
            scheduler.onEntity(
                    viewer,
                    () -> openResolved(viewer, specId, spec, subject, resolved, startPage, args, executor, record));
        });
    }

    /**
     * Reopen the previous menu {@code viewer} had open — the target a {@code back} button steps to. It hops to the
     * viewer's entity thread and, if nothing remains beneath the current open (they are at the root, or the engine was
     * wired without a history), closes the window instead. A previous open whose spec is no longer registered (a
     * since-dropped menu) is treated as nothing-below, so a stale entry closes cleanly rather than raising the loud
     * unknown-spec failure a blind reopen would. The reopen itself replays the recorded open without re-recording it,
     * so stepping back never grows the history.
     */
    public void back(PlayerRef viewer) {
        Objects.requireNonNull(viewer, "viewer");
        if (lastMenu == null) {
            closeFor(viewer);
            return;
        }
        lastMenu.back(viewer.uuid())
                .filter(prev -> specs.containsKey(prev.menuId()))
                .ifPresentOrElse(prev -> reopen(viewer, prev), () -> closeFor(viewer));
    }

    /**
     * Reopen the menu {@code viewer} currently has on top of their history — what {@code /menu last} runs. Returns
     * {@code false} (so the caller can show its own feedback) when there is nothing to reopen: the engine carries no
     * history, none has been recorded, or the recorded spec is no longer registered. A successful reopen replays the
     * recorded open without re-recording it, so calling it repeatedly never stacks duplicates.
     */
    public boolean reopenLast(PlayerRef viewer) {
        Objects.requireNonNull(viewer, "viewer");
        if (lastMenu == null) {
            return false;
        }
        Optional<LastMenu.LastOpen> last = lastMenu.get(viewer.uuid()).filter(open -> specs.containsKey(open.menuId()));
        last.ifPresent(open -> reopen(viewer, open));
        return last.isPresent();
    }

    /**
     * Replay a recorded open — same page and typed arguments, always subject-less — without recording it again. The
     * back history does not track who originally triggered the open, so the reopen is attributed to the viewer (the
     * player stepping back is looking at their own menu); a menu that leaned on {@code %executor%} would read the
     * viewer here rather than the long-gone original opener.
     */
    private void reopen(PlayerRef viewer, LastMenu.LastOpen open) {
        openInternal(viewer, open.menuId(), null, open.page(), open.arguments(), viewer, false);
    }

    /** Close whatever window {@code viewer} has open, on their entity thread — the back-step to nothing / null-history path. */
    private void closeFor(PlayerRef viewer) {
        scheduler.onEntity(viewer, () -> {
            Player live = Bukkit.getPlayer(viewer.uuid());
            if (live != null && live.isOnline()) {
                live.closeInventory();
            }
        });
    }

    /**
     * The menu {@code viewer} currently has open, as a plain {@link OpenMenuInfo} value, or empty when they are in
     * no engine menu — what the outbound {@code menu_*} placeholder source reads. It is a best-effort live read: it
     * resolves the online player and inspects the holder backing their open top inventory, which is the engine's
     * single source of truth for an open menu (no player-keyed side map is kept, so nothing can leak and there is
     * nothing else to consult). The read is authoritative when the placeholder resolves on the viewer's own
     * region/main context; a cross-region read on Folia would touch another region's player and is caught and
     * degraded to empty, so a stray off-region request reads "not in a menu" rather than throwing.
     */
    public Optional<OpenMenuInfo> currentMenu(UUID viewer) {
        Objects.requireNonNull(viewer, "viewer");
        try {
            Player live = Bukkit.getPlayer(viewer);
            if (live == null) {
                return Optional.empty();
            }
            InventoryHolder holder = live.getOpenInventory().getTopInventory().getHolder();
            return holder instanceof MenuHolder menu ? Optional.of(openMenuInfo(menu)) : Optional.empty();
        } catch (RuntimeException offRegion) {
            return Optional.empty();
        }
    }

    /** Read the open menu's id, its 1-based page (the context page is 0-based), row count and typed arguments. */
    private static OpenMenuInfo openMenuInfo(MenuHolder holder) {
        return new OpenMenuInfo(
                holder.specId(),
                holder.ctx().page() + 1,
                holder.spec().rows(),
                holder.ctx().arguments());
    }

    /**
     * The id of the most-recently-opened menu in {@code viewer}'s history — the {@code /menu last} target — which
     * persists after that menu closes, unlike {@link #currentMenu}. Read from the thread-safe {@link LastMenu}, so
     * it needs no Bukkit read and answers the same on any thread. Empty when the engine was wired without a history
     * tracker (every list/spec-only fixture) or the viewer has opened no custom menu yet.
     */
    public Optional<String> lastMenuId(UUID viewer) {
        Objects.requireNonNull(viewer, "viewer");
        if (lastMenu == null) {
            return Optional.empty();
        }
        return lastMenu.get(viewer).map(LastMenu.LastOpen::menuId);
    }

    /**
     * Open a typed property editor for {@code viewer} editing {@code subject}, as a holder-backed engine menu. It
     * builds the same {@link MenuHolder} every other menu uses — recognised and torn down by the one listener and
     * one {@code closeMenu} — but tags it with an {@link EditorState} so the listener routes its clicks through the
     * editor's property/button slots rather than a spec's. The window is shown on the viewer's entity thread, where
     * touching the live inventory is legal; unlike a list menu it queries no list source, so there is no off-thread
     * resolve step. An engine wired without an editor renderer cannot open an editor — that is a wiring error, so it
     * fails loudly here.
     */
    public void openEditor(PlayerRef viewer, EditorSpec spec, @Nullable Object subject) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(spec, "spec");
        if (editorRenderer == null) {
            throw new IllegalStateException("this Menus engine was wired without editor support");
        }
        scheduler.onEntity(viewer, () -> openEditorResolved(viewer, spec, subject));
    }

    /** On the viewer's entity thread: build the editor holder + window, render the editor, show it. No refresh. */
    private void openEditorResolved(PlayerRef viewer, EditorSpec spec, @Nullable Object subject) {
        Player live = Bukkit.getPlayer(viewer.uuid());
        if (live == null || !live.isOnline()) {
            return;
        }
        MenuContext ctx = MenuContext.of(viewer, subject, 0);
        MenuHolder holder = new MenuHolder(editorSpecId(spec), editorMenuSpec(spec), ctx);
        EditorState state = new EditorState(spec, subject);
        holder.attachEditor(state);
        Inventory inv = Bukkit.createInventory(holder, spec.layout().rows() * 9, spec.title(viewer, subject));
        holder.attach(inv);
        requireEditorRenderer().populate(inv, spec, state, live, viewer);
        live.openInventory(inv);
    }

    /**
     * Re-render an open editor in place — the {@code reopen} target a property's click hook runs after its setter.
     * It hops to the viewer's entity thread, confirms the live top inventory is still this holder's editor window,
     * clears the editor's slot routing, and repaints the same inventory from the live subject so the changed value
     * shows. No second {@code openInventory} and no new holder: the window the viewer is looking at is reused, so the
     * one listener and one teardown keep owning it.
     */
    public void reRenderEditor(MenuHolder holder) {
        Objects.requireNonNull(holder, "holder");
        EditorRefresh.reRender(holder, requireEditorRenderer(), scheduler);
    }

    /**
     * Open a paginated entity list for {@code viewer} as a holder-backed engine menu — the engine's replacement for the
     * bespoke {@code EntityListView}'s uxmLib {@code PaginatedGui}. It builds the same {@link MenuHolder} every other
     * menu uses — recognised and torn down by the one listener and one {@code closeMenu} — but tags it with a
     * {@link ListViewState} so the listener routes its clicks through the list's entity/nav/create/action slots rather
     * than a spec's. The window is shown on the viewer's entity thread, where touching the live inventory is legal; the
     * entity supplier was already resolved off-thread by the caller, so there is no off-thread resolve step here and the
     * imperative icon renderer reads only the snapshot. A page flip re-paginates the same holder (the listener's list
     * branch), so a list arms no refresh timer and stays leak-balanced.
     */
    public void openList(PlayerRef viewer, ListSpec spec) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(spec, "spec");
        scheduler.onEntity(viewer, () -> openListResolved(viewer, spec));
    }

    /** On the viewer's entity thread: build the list holder + window, render page zero, show it. No refresh. */
    private void openListResolved(PlayerRef viewer, ListSpec spec) {
        Player live = Bukkit.getPlayer(viewer.uuid());
        if (live == null || !live.isOnline()) {
            return;
        }
        MenuContext ctx = MenuContext.of(viewer, null, 0);
        MenuHolder holder = new MenuHolder("list:" + spec.getClass().getSimpleName(), listMenuSpec(spec), ctx);
        ListViewState state = new ListViewState(spec);
        holder.attachListView(state);
        Inventory inv = Bukkit.createInventory(holder, spec.rows() * 9, spec.title());
        holder.attach(inv);
        int clamped = listViewRenderer.populate(inv, spec, state, live, viewer, 0);
        holder.setCtx(ctx.withPage(clamped));
        live.openInventory(inv);
    }

    /** The minimal {@link MenuSpec} a list holder carries: the row count, refresh off, no items — clicks ride state. */
    private static MenuSpec listMenuSpec(ListSpec spec) {
        return new MenuSpec("", spec.rows(), new RefreshSpec(false, 0), List.of(), List.of(), List.of(), Map.of());
    }

    /**
     * Open a two-button confirm window for {@code viewer} — the engine's replacement for uxmLib's {@code ConfirmMenu}.
     * It builds the same {@link MenuHolder} every other menu uses, so the one listener routes its clicks and the one
     * {@code closeMenu} tears it down: clicking the yes button runs {@code onYes} exactly once, clicking no runs
     * {@code onNo} exactly once, and either click closes the window first. Closing the window (or quitting) without a
     * click runs neither. The window is shown on the viewer's entity thread, where touching the live inventory is
     * legal; the supplied runnables run on that same thread when their button is clicked, mirroring the editor and
     * spec-action click hops. The {@code title} is a {@link Component} the caller already resolved from a
     * {@code MessageKey}, so the window carries no inline user-facing literal.
     */
    public void confirm(PlayerRef viewer, Component title, Runnable onYes, Runnable onNo) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(onYes, "onYes");
        Objects.requireNonNull(onNo, "onNo");
        scheduler.onEntity(viewer, () -> openConfirmResolved(viewer, title, onYes, onNo));
    }

    /** On the viewer's entity thread: build the confirm holder + window, paint the two buttons, show it. No refresh. */
    private void openConfirmResolved(PlayerRef viewer, Component title, Runnable onYes, Runnable onNo) {
        Player live = Bukkit.getPlayer(viewer.uuid());
        if (live == null || !live.isOnline()) {
            return;
        }
        MenuContext ctx = MenuContext.of(viewer, null, 0);
        MenuHolder holder = new MenuHolder("confirm", confirmMenuSpec(), ctx);
        holder.attachConfirm(new ConfirmState(ConfirmRenderer.YES_SLOT, ConfirmRenderer.NO_SLOT, onYes, onNo));
        Inventory inv = Bukkit.createInventory(holder, ConfirmRenderer.ROWS * 9, title);
        holder.attach(inv);
        confirmRenderer.populate(inv);
        live.openInventory(inv);
    }

    /** The minimal {@link MenuSpec} a confirm holder carries: three rows, refresh off, no items — clicks ride state. */
    private static MenuSpec confirmMenuSpec() {
        return new MenuSpec(
                "", ConfirmRenderer.ROWS, new RefreshSpec(false, 0), List.of(), List.of(), List.of(), Map.of());
    }

    /**
     * The opener a property hands its picker to: opening a selector through it shows a {@link MenuHolder} child window
     * the one listener routes and the one {@code closeMenu} tears down. Threaded into the editor {@code ClickContext}
     * so an {@link com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.EnumProperty} (and, as they migrate,
     * the list/colour pickers) opens an engine child rather than a uxmLib {@code SimpleGui} on the engine runtime.
     */
    public SelectorOpener selectorOpener() {
        return selectorOpener;
    }

    /**
     * The opener a property hands a destructive step to: it gates the step behind a {@link MenuHolder} confirm child
     * the one listener routes and the one {@code closeMenu} tears down. Threaded into the editor {@code ClickContext}
     * alongside {@link #selectorOpener()} so a {@link com.uxplima.uxmessentials.shared.adapter.inbound.gui.property
     * .ListProperty}'s remove gesture opens an engine confirm rather than a uxmLib {@code ConfirmMenu} on the engine
     * runtime.
     */
    public ConfirmOpener confirmOpener() {
        return confirmOpener;
    }

    /**
     * Open a selector child window for {@code viewer} — a flat picker of option buttons, the engine's replacement for
     * a property's uxmLib {@code SimpleGui} selector. It builds the same {@link MenuHolder} every other menu uses, so
     * the one listener routes its clicks and the one {@code closeMenu} tears it down: clicking an option button runs
     * its choose action exactly once, and either that or closing the window (or quitting) ends the picker. The window
     * is shown on the viewer's entity thread, where touching the live inventory is legal; each button's choose action
     * runs on that same thread when clicked, mirroring the editor and confirm hops. The {@code title} is a
     * {@link Component} the caller resolved from a {@code MessageKey}, so the window carries no inline literal.
     */
    public void openSelector(
            PlayerRef viewer, Component title, int rows, Material filler, List<SelectorButton> buttons) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(filler, "filler");
        Objects.requireNonNull(buttons, "buttons");
        if (rows < 1 || rows > 6) {
            throw new IllegalArgumentException("rows must be 1..6, was " + rows);
        }
        List<SelectorButton> copy = List.copyOf(buttons);
        scheduler.onEntity(viewer, () -> openSelectorResolved(viewer, title, rows, filler, copy));
    }

    /** On the viewer's entity thread: build the selector holder + window, paint the option buttons, show it. */
    private void openSelectorResolved(
            PlayerRef viewer, Component title, int rows, Material filler, List<SelectorButton> buttons) {
        Player live = Bukkit.getPlayer(viewer.uuid());
        if (live == null || !live.isOnline()) {
            return;
        }
        MenuContext ctx = MenuContext.of(viewer, null, 0);
        MenuHolder holder = new MenuHolder("selector", selectorMenuSpec(rows), ctx);
        Map<Integer, ChildClickHandler> choices = new HashMap<>();
        for (SelectorButton button : buttons) {
            choices.put(button.slot(), button.onClick());
        }
        holder.attachSelector(new SelectorState(choices));
        Inventory inv = Bukkit.createInventory(holder, rows * 9, title);
        holder.attach(inv);
        selectorRenderer.populate(inv, filler, buttons);
        live.openInventory(inv);
    }

    /** The minimal {@link MenuSpec} a selector holder carries: the row count, refresh off, no items — clicks ride state. */
    private static MenuSpec selectorMenuSpec(int rows) {
        return new MenuSpec("", rows, new RefreshSpec(false, 0), List.of(), List.of(), List.of(), Map.of());
    }

    private EditorRenderer requireEditorRenderer() {
        if (editorRenderer == null) {
            throw new IllegalStateException("this Menus engine was wired without editor support");
        }
        return editorRenderer;
    }

    /** A stable holder id for an editor open; editors are code-built and not registered, so the type name suffices. */
    private static String editorSpecId(EditorSpec spec) {
        return "editor:" + spec.getClass().getSimpleName();
    }

    /**
     * A minimal {@link MenuSpec} the editor holder carries so {@link MenuRefresh} and the holder's accessors have
     * something coherent to read: the editor's row count, refresh disabled (an editor is repainted on a click, never
     * on a timer), and no items (an editor's buttons live on its {@link EditorState}, not in a spec). The editor
     * render path never reads this spec's items, so an empty item map is exactly right.
     */
    private static MenuSpec editorMenuSpec(EditorSpec spec) {
        return new MenuSpec(
                "", spec.layout().rows(), new RefreshSpec(false, 0), List.of(), List.of(), List.of(), Map.of());
    }

    /**
     * Resolve every list source the spec names, keyed by source id. Runs off the viewer's region thread because a
     * source may read a database; an unregistered source resolves to an empty list so a wiring gap renders an empty
     * grid rather than failing the open. This is the only place a source is queried for one open.
     */
    private Map<String, List<?>> resolveLists(MenuSpec spec, MenuContext ctx) {
        Map<String, List<?>> resolved = new HashMap<>();
        for (MenuItemSpec item : spec.items().values()) {
            item.list().ifPresent(listSpec -> {
                String sourceId = listSpec.source().id();
                resolved.put(
                        sourceId, lists.get(sourceId).map(fn -> fn.apply(ctx)).orElse(List.of()));
            });
        }
        return resolved;
    }

    /** On the viewer's entity thread: build the holder-backed window, cache the lists, render, show, arm refresh. */
    private void openResolved(
            PlayerRef viewer,
            String specId,
            MenuSpec spec,
            @Nullable Object subject,
            Map<String, List<?>> resolved,
            int page,
            Map<String, String> arguments,
            PlayerRef executor,
            boolean record) {
        Player live = Bukkit.getPlayer(viewer.uuid());
        if (live == null || !live.isOnline()) {
            return;
        }
        // Attach the spec's own placeholders {} block so the renderer resolves its %name% tokens local-first, and the
        // executor so %executor% names the opener; the holder carries this ctx, so a refresh/re-render reads it back
        // and keeps both the local map and the executor through the redraw.
        MenuContext ctx = MenuContext.of(viewer, subject, page, arguments)
                .withLocalPlaceholders(spec.placeholders())
                .withExecutor(executor);
        if (!gateOpen(spec, ctx)) {
            return;
        }
        MenuHolder holder = new MenuHolder(specId, spec, ctx);
        holder.setResolvedLists(resolved);
        Inventory inv = createWindow(holder, spec, renderer.title(spec, ctx));
        holder.attach(inv);
        renderer.populate(inv, spec, ctx, holder::recordSlot, holder.resolvedLists());
        if (spec.bottomInventory()) {
            paintBottom(holder, spec, ctx, live);
        }
        live.openInventory(inv);
        if (record) {
            rememberLastOpen(viewer, specId, subject, page, arguments);
        }
        runOpenActions(spec, live, ctx);
        MenuRefresh.start(holder, scheduler, () -> reRender(holder));
    }

    /**
     * Remember this open as the viewer's {@code /menu last} target — but only a subject-less one, a disk-loaded
     * custom menu. A feature menu carries a live domain subject (a warp, a home owner) that must never be reopened
     * blind, and it has its own command, so those are deliberately not recorded. An engine wired without a tracker
     * (every list/spec-only fixture) records nothing, so an open there stays byte-identical to before this seam.
     */
    private void rememberLastOpen(
            PlayerRef viewer, String specId, @Nullable Object subject, int page, Map<String, String> arguments) {
        if (lastMenu != null && subject == null) {
            lastMenu.record(viewer.uuid(), new LastMenu.LastOpen(specId, page, arguments));
        }
    }

    /**
     * Build the window a spec opens into: its declared non-chest {@link InventoryType} when it names one the server
     * accepts, else the default {@code rows}-based chest. A non-chest shape is best-effort — some types reject a
     * custom holder or title on some servers — so a thrown build is caught, logged once, and downgraded to the chest,
     * meaning a bad {@code inventory-type} never leaves the viewer with a blank or missing window.
     */
    private Inventory createWindow(MenuHolder holder, MenuSpec spec, Component title) {
        Optional<InventoryType> type = spec.inventoryType().flatMap(Menus::resolveInventoryType);
        if (type.isEmpty()) {
            return Bukkit.createInventory(holder, spec.rows() * 9, title);
        }
        try {
            return Bukkit.createInventory(holder, type.get(), title);
        } catch (RuntimeException rejected) {
            LOG.warning("menu inventory type '" + spec.inventoryType().orElse("")
                    + "' could not be created, falling back to a chest: " + rejected.getMessage());
            return Bukkit.createInventory(holder, spec.rows() * 9, title);
        }
    }

    /**
     * Map an operator-friendly inventory-type token to the Bukkit {@link InventoryType} that shapes the window.
     * {@code chest}, a blank token, or any name not listed here resolves to empty, i.e. the default {@code rows}-based
     * chest — an unknown type is a soft miss, not a failure. A couple of obvious aliases are accepted so a spec author
     * can write the block name they know ({@code shulker}/{@code shulker_box}, {@code ender}/{@code ender_chest},
     * {@code workbench}/{@code crafting}).
     */
    private static Optional<InventoryType> resolveInventoryType(String name) {
        return switch (name.strip().toLowerCase(Locale.ROOT)) {
            case "hopper" -> Optional.of(InventoryType.HOPPER);
            case "dropper" -> Optional.of(InventoryType.DROPPER);
            case "dispenser" -> Optional.of(InventoryType.DISPENSER);
            case "furnace" -> Optional.of(InventoryType.FURNACE);
            case "anvil" -> Optional.of(InventoryType.ANVIL);
            case "brewing", "brewing_stand" -> Optional.of(InventoryType.BREWING);
            case "beacon" -> Optional.of(InventoryType.BEACON);
            case "shulker", "shulker_box" -> Optional.of(InventoryType.SHULKER_BOX);
            case "barrel" -> Optional.of(InventoryType.BARREL);
            case "lectern" -> Optional.of(InventoryType.LECTERN);
            case "loom" -> Optional.of(InventoryType.LOOM);
            case "ender", "ender_chest", "enderchest" -> Optional.of(InventoryType.ENDER_CHEST);
            case "workbench", "crafting", "crafting_table" -> Optional.of(InventoryType.WORKBENCH);
            default -> Optional.empty();
        };
    }

    /**
     * Whether this menu may open for {@code viewer} given its {@code open-requirement}. The gate is open — the open
     * proceeds — when the engine was wired without a condition registry (every list/spec-only test engine) or the
     * spec names no requirement, so an engine that predates this seam behaves byte-identically. Otherwise every
     * requirement ref is an AND gate: each is resolved against the condition registry (the same registry-aware split
     * the click path uses, so a valued token like {@code has-money:100} reaches its handler with {@code value=100}),
     * has its {@code %argument_<name>%} tokens expanded from the arguments the menu was opened with (so a gate can read
     * a typed open-command's argument, e.g. {@code expr:%argument_amount% > 0}), and must test true. An unregistered or
     * false condition fails the gate closed, so a wiring gap keeps the window
     * shut rather than showing it — a deny message is a later, DeluxeMenus-style concern, not this simple gate.
     */
    private boolean gateOpen(MenuSpec spec, MenuContext ctx) {
        ConditionRegistry conditions = openConditionRegistry;
        if (conditions == null || spec.openRequirement().isEmpty()) {
            return true;
        }
        for (Ref ref : spec.openRequirement()) {
            Ref eff = ref.resolve(conditions::has);
            boolean pass = conditions
                    .get(eff.id())
                    .map(p -> p.test(ctx, ActionArguments.resolve(eff.args(), ctx.arguments())))
                    .orElse(false);
            if (!pass) {
                return false;
            }
        }
        return true;
    }

    /**
     * Run the spec's {@code open-actions} in order, now that the window is open on the viewer's entity thread — where
     * touching the live inventory is legal. Skipped when the engine was wired without an action registry (a
     * list/spec-only test engine), so an engine that predates this seam runs nothing extra. Each ref is resolved
     * against the action registry — the same registry-aware split the click path takes — and dispatched through a
     * {@link MenuActionContext} carrying {@link ClickKind#LEFT} as the neutral kind (no gesture fired on an open) and
     * the four-argument, no-control constructor, so a {@code refresh} written as an open-action is a harmless no-op
     * rather than a null-control failure. An action's {@code %argument_<name>%} tokens are expanded from the
     * arguments the menu was opened with, matching the click and render paths. Open-actions fire simply here; the
     * per-action delay and chance modifiers a click action honours are a later concern.
     */
    private void runOpenActions(MenuSpec spec, Player live, MenuContext ctx) {
        ActionRegistry actions = openActionRegistry;
        if (actions == null) {
            return;
        }
        for (Ref ref : spec.openActions()) {
            Ref eff = ref.resolve(actions::has);
            actions.get(eff.id())
                    .ifPresent(handler -> handler.accept(new MenuActionContext(
                            ctx, live, ClickKind.LEFT, ActionArguments.resolve(eff.args(), ctx.arguments()))));
        }
    }

    /** Redraw an open menu in place on its viewer's thread, but only if that window is still this holder's. */
    private void reRender(MenuHolder holder) {
        scheduler.onEntity(holder.ctx().viewer(), () -> {
            Player p = Bukkit.getPlayer(holder.ctx().viewer().uuid());
            if (p == null) {
                return;
            }
            if (!(p.getOpenInventory().getTopInventory().getHolder() instanceof MenuHolder h) || h != holder) {
                return;
            }
            holder.clearClickMap();
            renderer.populate(
                    holder.getInventory(), holder.spec(), holder.ctx(), holder::recordSlot, holder.resolvedLists());
            if (holder.spec().bottomInventory()) {
                // Re-paint the bottom too, but do not re-snapshot — the viewer's real items were captured on open and
                // are held on the holder until close; populateBottom clears and redraws only the menu tiles.
                renderer.populateBottom(
                        p.getInventory(), holder.spec(), holder.ctx(), holder::recordSlot, holder.resolvedLists());
            }
        });
    }

    /**
     * The open-time half of a bottom-inventory menu: snapshot the viewer's real 36 bottom slots onto the holder, then
     * paint the menu's bottom items into them. The snapshot is what the close restores (and what a death drops in
     * place of the menu tiles), so it is taken before {@code populateBottom} clears and repaints the canvas. Runs on
     * the viewer's own entity thread — where touching the live inventory is legal — and only for a menu whose spec
     * sets the flag; an ordinary menu never reaches here and never touches the player inventory.
     */
    private void paintBottom(MenuHolder holder, MenuSpec spec, MenuContext ctx, Player live) {
        holder.setBottomSnapshot(live.getInventory().getStorageContents());
        renderer.populateBottom(live.getInventory(), spec, ctx, holder::recordSlot, holder.resolvedLists());
    }

    /**
     * Close every menu this engine owns, cancelling its refresh task first so no timer survives the disable. The
     * online-roster sweep runs on the global region thread (the only thread on which the roster is coherent on
     * Folia), matching the scoreboard/tablist tear-down pattern. The click listener is uninstalled by the bootstrap
     * wiring, not here, so a closed window cannot be re-clicked.
     */
    public void shutdown() {
        scheduler.onGlobal(() -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                InventoryHolder open =
                        player.getOpenInventory().getTopInventory().getHolder();
                if (open instanceof MenuHolder holder) {
                    holder.cancelRefresh();
                    player.closeInventory();
                }
            }
        });
    }
}
