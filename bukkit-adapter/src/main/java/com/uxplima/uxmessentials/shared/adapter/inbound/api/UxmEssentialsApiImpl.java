package com.uxplima.uxmessentials.shared.adapter.inbound.api;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import com.uxplima.uxmessentials.api.bukkit.UxmEssentialsApi;
import com.uxplima.uxmessentials.api.bukkit.menu.MenuApi;
import com.uxplima.uxmessentials.api.query.UxmHomesQuery;
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

    public UxmEssentialsApiImpl(String version, ModuleRegistry modules, Supplier<ConfigStore> config, MenuApi menus) {
        this(version, modules, config, menus, QueryContexts.empty());
    }

    public UxmEssentialsApiImpl(
            String version,
            ModuleRegistry modules,
            Supplier<ConfigStore> config,
            MenuApi menus,
            QueryContexts queries) {
        this.version = Objects.requireNonNull(version, "version");
        this.modules = Objects.requireNonNull(modules, "modules");
        this.config = Objects.requireNonNull(config, "config");
        this.menus = Objects.requireNonNull(menus, "menus");
        this.queries = Objects.requireNonNull(queries, "queries");
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
    public Optional<UxmHomesQuery> homes() {
        return queries.find(UxmHomesQuery.class);
    }
}
