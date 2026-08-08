package com.uxplima.uxmessentials.discordlink.application;

import java.util.List;

import com.uxplima.uxmessentials.shared.application.module.CommandSpec;
import com.uxplima.uxmessentials.shared.application.module.FeatureModule;
import com.uxplima.uxmessentials.shared.application.module.ListenerFactory;
import com.uxplima.uxmessentials.shared.application.module.MigrationSet;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import org.jspecify.annotations.NullMarked;

/**
 * The discord-link bounded context as a first-class {@link FeatureModule}: it owns the one-time pending link
 * codes and the confirmed UUID&lt;-&gt;Discord bindings, the {@code /discordlink} and {@code /discordunlink}
 * commands, and the {@code ConfirmLink} use case the Discord bridge redeems a {@code /link} code through over
 * the {@code ServicesManager} seam. The module declares its command surface and enable gate here; {@code start}
 * arms the lifecycle bookkeeping, and the bukkit-side adapters (the Brigadier handlers and the jOOQ store over
 * {@code persistence.dsl()}) are constructed in the adapter wiring once the module has started.
 *
 * <p><b>Ships disabled by default.</b> Linking needs the separate Discord jar and a bot token, so on a server
 * with neither the commands would only ever report that the bridge is not configured.
 *
 * <p>The {@code discord_links} and {@code discord_link_pending} tables ship in the persistence V16 baseline
 * ({@code db/migration}), which the persistence layer always applies, so the module declares no extra migration
 * location of its own; a disabled module still leaves the baseline tables in place but wires nothing over them.
 */
@NullMarked
public final class DiscordlinkModule implements FeatureModule {

    private static final ModuleId ID = ModuleId.of("discordlink");

    private volatile boolean running;

    @Override
    public ModuleId id() {
        return ID;
    }

    @Override
    public String configRoot() {
        return ID.configRoot();
    }

    @Override
    public List<CommandSpec> commands() {
        return DiscordLinkCommandSurface.all();
    }

    @Override
    public List<ListenerFactory> listeners() {
        // The discord-link context registers no Bukkit listener; the inbound /link handler lives in the
        // separate Discord bridge jar, not as a host listener. A disabled module registers none either.
        return List.of();
    }

    @Override
    public List<MigrationSet> migrations() {
        // The discord_links / discord_link_pending tables are part of the persistence V16 baseline
        // (db/migration), always applied by the persistence layer, so the module owns no additional location.
        return List.of();
    }

    @Override
    public boolean enabled(ConfigStore config) {
        // The module ships DISABLED: linking needs the separate Discord jar and a bot token, so on a server that
        // has neither the commands would only ever answer that the bridge is not configured.
        return config.getBoolean(configRoot() + ".enabled", false);
    }

    @Override
    public void start(ModuleContext ctx) {
        this.running = true;
        // The use cases and the jOOQ store are constructed over ctx.kernel() and the persistence DSL in the
        // adapter wiring; the lifecycle flag is armed here so stop() is already honest.
    }

    @Override
    public void stop() {
        this.running = false;
    }

    /** True while the module is started. */
    public boolean isRunning() {
        return running;
    }
}
