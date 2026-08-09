package com.uxplima.uxmessentials.shared.adapter.inbound.api;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.api.action.UxmActions;
import com.uxplima.uxmessentials.api.bukkit.UxmEssentialsApi;
import com.uxplima.uxmessentials.api.bukkit.menu.MenuApi;
import com.uxplima.uxmessentials.api.query.UxmEconomyQuery;
import com.uxplima.uxmessentials.api.query.UxmHomesQuery;
import com.uxplima.uxmessentials.api.query.UxmKitsQuery;
import com.uxplima.uxmessentials.api.query.UxmMessagingQuery;
import com.uxplima.uxmessentials.api.query.UxmModerationQuery;
import com.uxplima.uxmessentials.api.query.UxmPlayerStateQuery;
import com.uxplima.uxmessentials.api.query.UxmPlayerWarpsQuery;
import com.uxplima.uxmessentials.api.query.UxmPlaytimeQuery;
import com.uxplima.uxmessentials.api.query.UxmPresenceQuery;
import com.uxplima.uxmessentials.api.query.UxmTeleportQuery;
import com.uxplima.uxmessentials.api.query.UxmVanishQuery;
import com.uxplima.uxmessentials.api.query.UxmVaultsQuery;
import com.uxplima.uxmessentials.api.query.UxmVoteQuery;
import com.uxplima.uxmessentials.api.query.UxmWarpsQuery;
import com.uxplima.uxmessentials.api.query.UxmWorldsQuery;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.ActionContexts;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.QueryContexts;
import com.uxplima.uxmessentials.shared.application.module.ModuleRegistry;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import org.jspecify.annotations.NullMarked;

/**
 * The running plugin behind {@link UxmEssentialsApi}.
 *
 * <p>The module question is answered against the live configuration rather than a snapshot taken at wiring time: the
 * config is swapped atomically on reload, so a consumer asking after an operator has turned a module off gets the
 * new answer. That is why the configuration arrives as a supplier and not as a value.
 */
@NullMarked
public final class UxmEssentialsApiImpl implements UxmEssentialsApi {

    private final String version;
    private final ModuleRegistry modules;
    private final Supplier<ConfigStore> config;
    private final MenuApi menus;
    private final QueryContexts queries;
    private final ActionContexts actions;

    public UxmEssentialsApiImpl(String version, ModuleRegistry modules, Supplier<ConfigStore> config, MenuApi menus) {
        this(version, modules, config, menus, QueryContexts.empty(), ActionContexts.empty());
    }

    public UxmEssentialsApiImpl(
            String version,
            ModuleRegistry modules,
            Supplier<ConfigStore> config,
            MenuApi menus,
            QueryContexts queries) {
        this(version, modules, config, menus, queries, ActionContexts.empty());
    }

    public UxmEssentialsApiImpl(
            String version,
            ModuleRegistry modules,
            Supplier<ConfigStore> config,
            MenuApi menus,
            QueryContexts queries,
            ActionContexts actions) {
        this.version = Objects.requireNonNull(version, "version");
        this.modules = Objects.requireNonNull(modules, "modules");
        this.config = Objects.requireNonNull(config, "config");
        this.menus = Objects.requireNonNull(menus, "menus");
        this.queries = Objects.requireNonNull(queries, "queries");
        this.actions = Objects.requireNonNull(actions, "actions");
    }

    @Override
    public String version() {
        return version;
    }

    @Override
    public boolean isModuleEnabled(String moduleId) {
        Objects.requireNonNull(moduleId, "moduleId");
        ConfigStore live = config.get();
        return modules.all().stream()
                .filter(module -> module.id().value().equals(moduleId))
                .anyMatch(module -> module.enabled(live));
    }

    @Override
    public MenuApi menus() {
        return menus;
    }

    @Override
    public UxmActions actions(Plugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        return new UxmActionsImpl(plugin.getName(), actions);
    }

    @Override
    public UxmActions actions(Plugin plugin, String actingFor) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(actingFor, "actingFor");
        String behalf = actingFor.trim();
        if (behalf.isEmpty()) {
            throw new IllegalArgumentException("actingFor must not be blank");
        }
        return new UxmActionsImpl(plugin.getName() + "/" + behalf, actions);
    }

    @Override
    public Optional<UxmHomesQuery> homes() {
        return queries.find(UxmHomesQuery.class);
    }

    @Override
    public Optional<UxmWarpsQuery> warps() {
        return queries.find(UxmWarpsQuery.class);
    }

    @Override
    public Optional<UxmPlayerWarpsQuery> playerWarps() {
        return queries.find(UxmPlayerWarpsQuery.class);
    }

    @Override
    public Optional<UxmEconomyQuery> economy() {
        return queries.find(UxmEconomyQuery.class);
    }

    @Override
    public Optional<UxmKitsQuery> kits() {
        return queries.find(UxmKitsQuery.class);
    }

    @Override
    public Optional<UxmVaultsQuery> vaults() {
        return queries.find(UxmVaultsQuery.class);
    }

    @Override
    public Optional<UxmModerationQuery> moderation() {
        return queries.find(UxmModerationQuery.class);
    }

    @Override
    public Optional<UxmPresenceQuery> presence() {
        return queries.find(UxmPresenceQuery.class);
    }

    @Override
    public Optional<UxmVanishQuery> vanish() {
        return queries.find(UxmVanishQuery.class);
    }

    @Override
    public Optional<UxmPlaytimeQuery> playtime() {
        return queries.find(UxmPlaytimeQuery.class);
    }

    @Override
    public Optional<UxmPlayerStateQuery> playerState() {
        return queries.find(UxmPlayerStateQuery.class);
    }

    @Override
    public Optional<UxmWorldsQuery> worlds() {
        return queries.find(UxmWorldsQuery.class);
    }

    @Override
    public Optional<UxmTeleportQuery> teleport() {
        return queries.find(UxmTeleportQuery.class);
    }

    @Override
    public Optional<UxmVoteQuery> vote() {
        return queries.find(UxmVoteQuery.class);
    }

    @Override
    public Optional<UxmMessagingQuery> messaging() {
        return queries.find(UxmMessagingQuery.class);
    }
}
