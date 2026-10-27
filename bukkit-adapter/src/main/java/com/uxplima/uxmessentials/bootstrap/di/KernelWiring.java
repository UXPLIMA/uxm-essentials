package com.uxplima.uxmessentials.bootstrap.di;

import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.BukkitDomainGate;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.EventBridges;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.VetoRegistry;
import com.uxplima.uxmessentials.shared.adapter.outbound.config.ConfigurateConfigStore;
import com.uxplima.uxmessentials.shared.adapter.outbound.cooldown.PdcCooldowns;
import com.uxplima.uxmessentials.shared.adapter.outbound.event.InProcessDomainEventPublisher;
import com.uxplima.uxmessentials.shared.adapter.outbound.hud.HudText;
import com.uxplima.uxmessentials.shared.adapter.outbound.log.Slf4jLogger;
import com.uxplima.uxmessentials.shared.adapter.outbound.lookup.BukkitPlayerLocator;
import com.uxplima.uxmessentials.shared.adapter.outbound.lookup.BukkitPlayerLookup;
import com.uxplima.uxmessentials.shared.adapter.outbound.lookup.BukkitWorldLookup;
import com.uxplima.uxmessentials.shared.adapter.outbound.lookup.CachingPlayerNameIndex;
import com.uxplima.uxmessentials.shared.adapter.outbound.lookup.IndexedPlayerLookup;
import com.uxplima.uxmessentials.shared.adapter.outbound.message.AdventureTranslations;
import com.uxplima.uxmessentials.shared.adapter.outbound.message.CatalogMessages;
import com.uxplima.uxmessentials.shared.adapter.outbound.message.HoconLocaleCatalog;
import com.uxplima.uxmessentials.shared.adapter.outbound.message.LocaleResolver;
import com.uxplima.uxmessentials.shared.adapter.outbound.message.SharedLocaleStore;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.PlaceholderApiSupport;
import com.uxplima.uxmessentials.shared.adapter.outbound.permission.BukkitPermissions;
import com.uxplima.uxmessentials.shared.adapter.outbound.permission.LuckPermsAccess;
import com.uxplima.uxmessentials.shared.adapter.outbound.permission.LuckPermsMetaSource;
import com.uxplima.uxmessentials.shared.adapter.outbound.permission.MetaSource;
import com.uxplima.uxmessentials.shared.adapter.outbound.scheduler.FoliaScheduler;
import com.uxplima.uxmessentials.shared.adapter.outbound.sink.BukkitMessageSink;
import com.uxplima.uxmessentials.shared.adapter.outbound.skin.HttpClientFetcher;
import com.uxplima.uxmessentials.shared.adapter.outbound.skin.MojangSkins;
import com.uxplima.uxmessentials.shared.adapter.outbound.warmup.SchedulerWarmups;
import com.uxplima.uxmessentials.shared.application.message.CatalogLines;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.MessageKeyCatalog;
import com.uxplima.uxmessentials.shared.application.module.FeatureModule;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.shared.application.module.MigrationSet;
import com.uxplima.uxmessentials.shared.application.module.ModuleRegistry;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.LocaleCatalog;
import com.uxplima.uxmessentials.shared.application.port.LocaleStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmlib.text.language.FilePlayerLanguages;
import com.uxplima.uxmlib.text.language.LanguageSettings;
import com.uxplima.uxmlib.text.language.SharedLanguage;
import com.uxplima.uxmlib.text.style.Theme;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import org.jspecify.annotations.NullMarked;

/**
 * Constructs the shared-kernel adapter implementations and bundles them into a {@link KernelPorts}.
 *
 * <p>This is the one place that news up the Bukkit/Paper outbound adapters for the shared cross-cutting
 * ports; the {@link Plugin} handle stays inside bootstrap (the adapters take the {@code Plugin}
 * interface, never {@code JavaPlugin}). LuckPerms is a soft dependency, the {@code MetaSource}
 * defaults to {@link MetaSource#none()} and only binds to the LuckPerms-backed source when LuckPerms is
 * installed, so the {@code net.luckperms} symbols are never loaded on a server without it.
 */
@NullMarked
final class KernelWiring {

    /** The shared chat prefix catalog key, injected into the sink's {@code <prefix>} tag. */
    private static final MessageKey PREFIX_KEY = () -> "prefix";

    /** The config path for the server-default locale used when a viewer has no override and no client. */
    private static final String DEFAULT_LOCALE_PATH = "messages.default-locale";

    private static final String LANGUAGE_DEFAULT_PATH = "language.default";

    /** The default the shipped config.conf writes, which an update appends to an older file. */
    private static final String SHIPPED_DEFAULT = "en";

    private static final String LANGUAGE_FOLLOW_CLIENT_PATH = "language.follow-client";

    private static final String LANGUAGE_FORCE_PATH = "language.force";

    private static final String SKIN_LOOKUP_PATH = "skins.mojang-lookup";

    private KernelWiring() {}

    /**
     * The wired shared kernel: the {@link KernelPorts} every module consumes, plus the i18n collaborators
     * the bootstrap command surface and the inbound command boundary need, the per-player override
     * {@link LocaleStore} for {@code /lang}, the {@link LocaleCatalog} for locale validation, and the
     * {@link LocaleResolver} the boundary binding folds the captured client locale into.
     *
     * @param ports the shared cross-cutting outbound ports
     * @param localeStore the persisted {@code /lang} override store
     * @param catalog the loaded message catalog
     * @param resolver the per-viewer locale resolver
     * @param serverDefault the configured server-default locale (client fallback at the boundary)
     * @param nameIndex the player-name index behind the lookup port, still to be backed by the database
     */
    record Kernel(
            KernelPorts ports,
            SharedLocaleStore localeStore,
            LocaleCatalog catalog,
            LocaleResolver resolver,
            java.util.Locale serverDefault,
            CachingPlayerNameIndex nameIndex) {
        Kernel {
            Objects.requireNonNull(ports, "ports");
            Objects.requireNonNull(localeStore, "localeStore");
            Objects.requireNonNull(catalog, "catalog");
            Objects.requireNonNull(resolver, "resolver");
            Objects.requireNonNull(serverDefault, "serverDefault");
            Objects.requireNonNull(nameIndex, "nameIndex");
        }
    }

    /** The HOCON config backing the {@link ConfigStore}; the root file plus the per-module layout. */
    static ConfigStore loadConfig(Plugin plugin, Logger log) {
        Path dataFolder = plugin.getDataFolder().toPath();
        return ConfigurateConfigStore.loadLayout(dataFolder, log);
    }

    /** A {@link Logger} over the plugin's SLF4J logger. */
    static Logger logger(Plugin plugin) {
        return new Slf4jLogger(plugin.getSLF4JLogger());
    }

    /**
     * Open the persistence layer: resolve the backend from the {@code storage} config subtree, build the
     * Hikari data source (single-writer WAL for the embedded SQLite default), and apply the Flyway
     * migrations the enabled modules contribute plus the V1 baseline. The returned handle exposes the
     * shared {@code DSLContext} the context repositories bind to, and is closed last on disable.
     */
    static Persistence openPersistence(Plugin plugin, ConfigStore config, Logger log, ModuleRegistry registry) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(log, "log");
        Objects.requireNonNull(registry, "registry");
        Path dataFolder = plugin.getDataFolder().toPath();
        return Persistence.open(config, dataFolder, migrationLocations(config, registry), log);
    }

    private static List<String> migrationLocations(ConfigStore config, ModuleRegistry registry) {
        // Always include the V1 baseline; each enabled module then contributes its own location as it
        // declares one. A disabled module appears in neither list, so its tables stay absent.
        List<String> locations = new ArrayList<>();
        locations.add("db/migration");
        for (FeatureModule module : registry.enabledModules(config)) {
            for (MigrationSet migration : module.migrations()) {
                if (!locations.contains(migration.location())) {
                    locations.add(migration.location());
                }
            }
        }
        return locations;
    }

    /** Build every shared outbound port plus the i18n collaborators, and bundle them for wiring. */
    static Kernel wire(Plugin plugin, ConfigStore config, Logger log, Supplier<Theme> theme) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(log, "log");
        Objects.requireNonNull(theme, "theme");

        Scheduler scheduler = new FoliaScheduler(plugin);
        Permissions permissions = new BukkitPermissions(metaSource(log));
        Path messagesDir = plugin.getDataFolder().toPath().resolve("messages");
        LocaleCatalog catalog = new HoconLocaleCatalog(log, messagesDir, theme);
        String prefix = catalog.template(java.util.Locale.ENGLISH, PREFIX_KEY);

        SharedLocaleStore localeStore = new SharedLocaleStore(
                SharedLanguage.onServer(),
                new org.bukkit.NamespacedKey(plugin, "lang-override"),
                FilePlayerLanguages.loadedFrom(plugin.getDataFolder().toPath().resolve("languages.conf")),
                scheduler::async);
        LanguageSettings language = languageSettings(config, log);
        java.util.Locale serverDefault = language.defaultLocale();
        LocaleResolver resolver = new LocaleResolver(
                localeStore, new com.uxplima.uxmessentials.shared.adapter.outbound.message.ClientLocales(), language);
        // The translatable-key path: register every catalog key with Adventure's
        // GlobalTranslator so a Component.translatable("uxmessentials.<key>") renders per the receiving
        // connection's locale. The MessageKey-resolve path stays the primary surface.
        new AdventureTranslations(catalog, log).register(MessageKeyCatalog.all());

        // The plugin's own name-to-account index. Paper only consults the server's name cache on an online-mode
        // server, so without this an offline-mode server cannot resolve a name typed in a different case at all.
        // It starts empty and memory-only; PluginModule backs it with the database once persistence is open.
        CachingPlayerNameIndex nameIndex = new CachingPlayerNameIndex(scheduler, log);

        // One skin fetch for every context that dresses something in a player's skin. It asks Mojang directly
        // rather than through Bukkit's profile completion, which consults the session service only on an
        // online-mode server and hands back a profile with no textures on a cracked one.
        // What the rest of the server is allowed to refuse. Built here rather than alongside the notification bridge
        // because a use case asks the gate through the kernel, so it has to exist before any module is wired.
        VetoRegistry vetoes = new VetoRegistry();
        EventBridges.installAllVetoes(vetoes);

        MojangSkins mojangSkins =
                new MojangSkins(scheduler, log, new HttpClientFetcher(log), config.getBoolean(SKIN_LOOKUP_PATH, true));

        CatalogMessages messages = new CatalogMessages(catalog, resolver);
        // An operator's @key line, on a hologram, a board, a menu or a click action, reads from this same catalogue
        // in the viewer's language. The catalogue re-reads itself on reload, so this is set once.
        HudText.useCatalogLines(new CatalogLines(messages, catalog::declares));
        // The same resolver answers which language a viewer reads, so a hologram drawn per viewer is drawn again
        // when that answer changes.
        HudText.useLanguages(
                viewer -> resolver.resolve(new com.uxplima.uxmessentials.shared.domain.PlayerRef(viewer, "")));

        KernelPorts ports = new KernelPorts(
                scheduler,
                permissions,
                new PdcCooldowns(plugin, permissions, Clock.systemUTC()),
                new SchedulerWarmups(scheduler, permissions),
                messages,
                new BukkitMessageSink(scheduler, prefix, messageBridgeFactory()),
                new IndexedPlayerLookup(new BukkitPlayerLookup(), nameIndex),
                new BukkitWorldLookup(),
                new BukkitPlayerLocator(),
                new InProcessDomainEventPublisher(log),
                log,
                mojangSkins,
                new BukkitDomainGate(vetoes, plugin.getServer().getPluginManager(), log));
        return new Kernel(ports, localeStore, catalog, resolver, serverDefault, nameIndex);
    }

    /**
     * The {@code language} block, the one every plugin of ours reads.
     *
     * <p>A file written before the block existed names its default as {@code messages.default-locale}, and an
     * update appends the block to it with the shipped {@code default = "en"}. Reading the block first would turn
     * a German server English on the upgrade, so the old key answers while the block still holds the shipped
     * default. An operator who writes their own default in the block is read, and the old key they left behind
     * is named so it can go.
     */
    static LanguageSettings languageSettings(ConfigStore config, Logger log) {
        String legacy = config.getString(DEFAULT_LOCALE_PATH, "").strip();
        String written = config.getString(LANGUAGE_DEFAULT_PATH, "").strip();
        boolean shipped = written.isEmpty() || written.equalsIgnoreCase(SHIPPED_DEFAULT);
        if (!legacy.isEmpty() && !shipped && !legacy.equalsIgnoreCase(written)) {
            log.warn(
                    "config.conf names a default language twice: language.default = {} is read, and the old"
                            + " messages.default-locale = {} is not. Remove the old line.",
                    written,
                    legacy);
        }
        String tag = shipped && !legacy.isEmpty() ? legacy : written.isEmpty() ? SHIPPED_DEFAULT : written;
        boolean followClient = config.getBoolean(LANGUAGE_FOLLOW_CLIENT_PATH, true);
        String forcedTag = config.getString(LANGUAGE_FORCE_PATH, "").strip();
        java.util.Locale forced = forcedTag.isEmpty() ? null : java.util.Locale.forLanguageTag(forcedTag);
        boolean forcesOne = forced != null && !forced.getLanguage().isEmpty();
        return new LanguageSettings(parsedOr(tag), followClient, forcesOne ? forced : null);
    }

    private static java.util.Locale parsedOr(String tag) {
        java.util.Locale parsed = java.util.Locale.forLanguageTag(tag.strip());
        return parsed.getLanguage().isEmpty() ? java.util.Locale.ENGLISH : parsed;
    }

    private static java.util.function.Function<java.util.UUID, java.util.function.UnaryOperator<String>>
            messageBridgeFactory() {
        // The PlaceholderAPI message bridge: when the plugin is present, a per-viewer pre-parse transform
        // expands %papi% placeholders in operator-authored catalog content before MiniMessage parses; when
        // absent, the factory hands back the identity transform and the sink is unchanged. The me.clip symbols
        // are reached only inside PlaceholderApiSupport, past its plugin-present guard.
        if (!PlaceholderApiSupport.isPresent()) {
            return BukkitMessageSink.NO_PRE_PARSE;
        }
        return PlaceholderApiSupport::messageBridge;
    }

    private static MetaSource metaSource(Logger log) {
        if (!LuckPermsAccess.isPresent(Bukkit.getServer())) {
            return MetaSource.none();
        }
        // Loading LuckPermsMetaSource (and thus the net.luckperms symbols) only happens past the
        // plugin-present guard, so a server without LuckPerms never resolves those classes.
        LuckPerms luckPerms = LuckPermsProvider.get();
        return new LuckPermsMetaSource(luckPerms, log);
    }
}
