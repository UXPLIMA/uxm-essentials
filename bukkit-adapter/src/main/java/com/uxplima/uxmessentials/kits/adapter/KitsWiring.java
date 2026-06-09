package com.uxplima.uxmessentials.kits.adapter;

import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.Material;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.kits.adapter.inbound.command.KitCommands;
import com.uxplima.uxmessentials.kits.adapter.inbound.gui.KitCategoryManagerView;
import com.uxplima.uxmessentials.kits.adapter.inbound.gui.KitCategoryParentSelectorView;
import com.uxplima.uxmessentials.kits.adapter.inbound.gui.KitCategorySelectorView;
import com.uxplima.uxmessentials.kits.adapter.inbound.gui.KitCategorySettingsView;
import com.uxplima.uxmessentials.kits.adapter.inbound.gui.KitCreateChooserView;
import com.uxplima.uxmessentials.kits.adapter.inbound.gui.KitEditorListener;
import com.uxplima.uxmessentials.kits.adapter.inbound.gui.KitEditorView;
import com.uxplima.uxmessentials.kits.adapter.inbound.gui.KitManagerView;
import com.uxplima.uxmessentials.kits.adapter.inbound.gui.KitMenuView;
import com.uxplima.uxmessentials.kits.adapter.inbound.gui.KitPreviewListener;
import com.uxplima.uxmessentials.kits.adapter.inbound.gui.KitPreviewView;
import com.uxplima.uxmessentials.kits.adapter.inbound.gui.KitSettingsView;
import com.uxplima.uxmessentials.kits.adapter.inbound.listener.ChatPromptListener;
import com.uxplima.uxmessentials.kits.adapter.inbound.listener.KitClaimListener;
import com.uxplima.uxmessentials.kits.adapter.inbound.listener.KitsJoinListener;
import com.uxplima.uxmessentials.kits.adapter.outbound.BukkitKitGranter;
import com.uxplima.uxmessentials.kits.adapter.outbound.ConfigurateKitCategoryRepository;
import com.uxplima.uxmessentials.kits.adapter.outbound.ConfigurateKitRepository;
import com.uxplima.uxmessentials.kits.adapter.outbound.PdcKitClaims;
import com.uxplima.uxmessentials.kits.application.ClaimKit;
import com.uxplima.uxmessentials.kits.application.CreateKit;
import com.uxplima.uxmessentials.kits.application.DelKit;
import com.uxplima.uxmessentials.kits.application.KitAccess;
import com.uxplima.uxmessentials.kits.application.KitEditor;
import com.uxplima.uxmessentials.kits.application.KitNotifier;
import com.uxplima.uxmessentials.kits.application.KitReset;
import com.uxplima.uxmessentials.kits.application.ListKits;
import com.uxplima.uxmessentials.kits.application.ShowKit;
import com.uxplima.uxmessentials.kits.application.port.KitCategoryRepository;
import com.uxplima.uxmessentials.kits.application.port.KitClaimStore;
import com.uxplima.uxmessentials.kits.application.port.KitEconomy;
import com.uxplima.uxmessentials.kits.application.port.KitGranter;
import com.uxplima.uxmessentials.kits.application.port.KitRepository;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.ListDisplayMode;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayouts;
import com.uxplima.uxmessentials.shared.adapter.outbound.event.InProcessDomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import org.jspecify.annotations.NullMarked;

/**
 * Constructs the kits context's adapters and use cases over the injected kernel ports, the per-kit files
 * under {@code modules/kits/kits/}, and the PDC claim store, and produces the Brigadier command list the
 * plugin registers. This is the one place the kits context is wired — nothing else news up its classes.
 *
 * <p>The repository is the Configurate adapter over the per-kit files (read-on-load, write-through on
 * authoring; a legacy {@code kits.conf} monolith is split into per-kit files on first load). The claim store
 * and the granter are PDC- and inventory-backed Bukkit adapters; the shared
 * {@code Cooldowns} and {@code Permissions} kernel ports cover the cooldown and permission gates. The per-kit
 * cost soft-couples to the economy context: the {@link KitEconomy} seam is injected as an {@link Optional},
 * {@link Optional#empty()} when economy is disabled, so a priced kit's cost is recorded but not charged until
 * that bridge is wired.
 */
@NullMarked
public final class KitsWiring {

    private static final String LEGACY_KITS_FILE = "kits.conf";
    private static final String SHOWKIT_DISPLAY_KEY = "showkit-display";

    private KitsWiring() {}

    /** Build the kits adapters and use cases with no economy bridge (a recorded kit cost is not charged). */
    public static Wired wire(Plugin plugin, ModuleContext ctx, GuiLayouts guiLayouts) {
        return wire(plugin, ctx, Optional.empty(), guiLayouts);
    }

    /**
     * Build the kits context, charging a recorded per-kit cost through {@code economy} when present. The
     * economy context lands before kits in the registry, so its {@link KitEconomy} bridge is captured during
     * economy wiring and handed in here; when it is empty (economy disabled), a priced kit's cost is recorded
     * but not charged — the soft coupling the kits context owns.
     */
    public static Wired wire(Plugin plugin, ModuleContext ctx, Optional<KitEconomy> economy, GuiLayouts guiLayouts) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(economy, "economy");
        Objects.requireNonNull(guiLayouts, "guiLayouts");
        KernelPorts kernel = ctx.kernel();
        Path dataFolder = plugin.getDataFolder().toPath();
        Path kitsDir = dataFolder.resolve("modules").resolve("kits").resolve("kits");
        Path legacy = dataFolder.resolve(LEGACY_KITS_FILE);
        KitRepository repository = ConfigurateKitRepository.load(kitsDir, legacy, kernel.log());
        Path categoriesDir = dataFolder.resolve("modules").resolve("kits").resolve("categories");
        KitCategoryRepository categoryRepository = ConfigurateKitCategoryRepository.load(categoriesDir, kernel.log());
        KitClaimStore claims = new PdcKitClaims(plugin);
        KitGranter granter = new BukkitKitGranter(kernel.log());
        KitNotifier notifier = new KitNotifier(kernel.messages(), kernel.messageSink());
        GuiLayout menuLayout = guiLayouts.load("kits", "kits-menu", GuiLayout.paginatedDefault(Material.CHEST));
        GuiLayout managerLayout = guiLayouts.load(
                "kits",
                "kits-manager",
                new GuiLayout(6, Material.GRAY_STAINED_GLASS_PANE, Material.ARROW, 49, 53, List.of()));
        GuiLayout settingsLayout = guiLayouts.load(
                "kits",
                "kits-settings",
                new GuiLayout(
                        3,
                        Material.GRAY_STAINED_GLASS_PANE,
                        Material.ARROW,
                        26,
                        22,
                        List.of(0, 2, 4, 6, 8, 10, 12, 14, 16, 22, 18, 20, 24)));
        GuiLayout previewLayout = guiLayouts.load(
                "kits",
                "kits-preview",
                new GuiLayout(6, Material.GRAY_STAINED_GLASS_PANE, Material.ARROW, 0, 1, List.of()));

        KitManagerView kitManagerView =
                new KitManagerView(kernel.messages(), repository, kernel.scheduler(), managerLayout);
        KitCategoryManagerView categoryManagerView =
                new KitCategoryManagerView(kernel.messages(), categoryRepository, kernel.scheduler());
        KitCategorySettingsView categorySettingsView =
                new KitCategorySettingsView(kernel.messages(), kernel.scheduler());
        KitCategorySelectorView categorySelectorView =
                new KitCategorySelectorView(kernel.messages(), categoryRepository, kernel.scheduler());
        KitCategoryParentSelectorView categoryParentSelectorView =
                new KitCategoryParentSelectorView(kernel.messages(), categoryRepository, kernel.scheduler());
        KitCreateChooserView kitCreateChooserView = new KitCreateChooserView(kernel.messages(), kernel.scheduler());

        KitAccess access = new KitAccess(kernel.permissions(), kernel.cooldowns(), claims, economy);
        KitServices services = assemble(
                kernel,
                repository,
                categoryRepository,
                access,
                claims,
                granter,
                notifier,
                economy,
                menuLayout,
                previewLayout,
                kitManagerView,
                categoryManagerView,
                categorySettingsView,
                categorySelectorView,
                categoryParentSelectorView,
                kitCreateChooserView);

        List<CommandRegistration> commands = KitCommands.all(
                services,
                kernel.messages(),
                () -> ListDisplayMode.from(ctx.config()),
                () -> ListDisplayMode.from(ctx.config(), SHOWKIT_DISPLAY_KEY));

        ChatPromptListener promptListener = new ChatPromptListener(kernel.messages());
        KitSettingsView settingsView = new KitSettingsView(kernel.messages(), kernel.scheduler(), settingsLayout);

        List<Listener> listeners = List.of(
                new KitPreviewListener(),
                new KitEditorListener(
                        services.kitEditorView(),
                        services,
                        repository,
                        categoryRepository,
                        settingsView,
                        promptListener,
                        kernel.messages()),
                promptListener,
                new KitsJoinListener(repository, granter, access));

        KitClaimListener claimListener = new KitClaimListener(plugin, repository, kernel.scheduler(), kernel.log());
        if (kernel.events() instanceof InProcessDomainEventPublisher publisher) {
            publisher.subscribe(event -> {
                if (event instanceof com.uxplima.uxmessentials.kits.domain.event.KitClaimed claimed) {
                    claimListener.onClaim(claimed);
                }
            });
        }

        return new Wired(commands, listeners, services.kitEditorView(), repository);
    }

    private static KitServices assemble(
            KernelPorts kernel,
            KitRepository repository,
            KitCategoryRepository categoryRepository,
            KitAccess access,
            KitClaimStore claims,
            KitGranter granter,
            KitNotifier notifier,
            Optional<KitEconomy> economy,
            GuiLayout menuLayout,
            GuiLayout previewLayout,
            KitManagerView kitManagerView,
            KitCategoryManagerView categoryManagerView,
            KitCategorySettingsView categorySettingsView,
            KitCategorySelectorView categorySelectorView,
            KitCategoryParentSelectorView categoryParentSelectorView,
            KitCreateChooserView kitCreateChooserView) {
        Clock clock = Clock.systemUTC();
        ClaimKit claimKit = new ClaimKit(repository, access, granter, notifier, kernel.events(), clock, economy);
        KitMenuView kitMenu = new KitMenuView(
                kernel.messages(), kernel.scheduler(), claimKit, categoryRepository, access, menuLayout);
        KitPreviewView kitPreview = new KitPreviewView(kernel.messages(), kernel.scheduler(), previewLayout);
        KitEditor kitEditor = new KitEditor(repository, notifier);
        KitEditorView kitEditorView = new KitEditorView(kernel.messages(), kitEditor, kernel.scheduler());
        return new KitServices(
                claimKit,
                new ListKits(repository, kernel.permissions(), claims, notifier),
                new ShowKit(repository, notifier),
                new CreateKit(repository, notifier),
                new DelKit(repository, notifier),
                kitEditor,
                new KitReset(repository, claims, notifier),
                kitMenu,
                kitPreview,
                kitEditorView,
                kitManagerView,
                kernel.playerLookup(),
                categoryManagerView,
                categorySettingsView,
                categorySelectorView,
                categoryParentSelectorView,
                kitCreateChooserView);
    }

    /**
     * Everything the kits module contributes once wired: the Brigadier commands, the Bukkit listeners (the
     * read-only {@code /showkit} preview guard and the {@code /kiteditor} window's save-on-close handler), the
     * {@link KitEditorView} held so {@link #stop()} can flush every still-open editor on disable, plus the
     * {@link KitRepository} the {@code kit_cooldown_<id>} placeholder resolves a kit's cooldown tier against.
     * The kit catalog is config-backed, so the only durable-while-open state is the set of open editor windows.
     *
     * @param commands the Brigadier command registrations to publish
     * @param listeners the Bukkit listeners to register
     * @param kitEditorView the editor window, held so {@code stop()} flushes every still-open edit
     * @param repository the kit catalog the cooldown placeholder reads
     */
    public record Wired(
            List<CommandRegistration> commands,
            List<Listener> listeners,
            KitEditorView kitEditorView,
            KitRepository repository) {

        public Wired {
            commands = List.copyOf(commands);
            listeners = List.copyOf(listeners);
            Objects.requireNonNull(kitEditorView, "kitEditorView");
            Objects.requireNonNull(repository, "repository");
        }

        /** Save every still-open {@code /kiteditor} window back to its kit. Called on module stop. */
        public void stop() {
            kitEditorView.flushAll();
        }
    }
}
