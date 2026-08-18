package com.uxplima.uxmessentials.skin.application;

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
 * The skin bounded context as a first-class {@link FeatureModule}: one built-in skin system, so a server (above
 * all a cracked one) needs neither SkinsRestorer nor BedrockSkinRestorer beside us. A player picks a skin by name,
 * by url or from a file the operator dropped on the server; a Bedrock player arrives wearing their Bedrock skin;
 * everyone else is dressed from a default pool instead of appearing as Steve.
 *
 * <p><b>Ships disabled by default.</b> On an online-mode server every player already wears their own skin, so the
 * module would buy nothing but extra traffic; the operator who needs it flips
 * {@code modules.skin.enabled = true}.
 *
 * <p>The {@code player_skins} table ships in the persistence baseline ({@code db/migration}, always applied by the
 * persistence layer), so the module declares no extra migration location of its own. The repository, the use cases,
 * the {@code /skin} command and the pre-login listener are constructed in the adapter wiring once the module has
 * started; the lifecycle flag here keeps {@code stop()} honest for the login path, which must leave a joining
 * player alone the moment the module is turned off.
 */
@NullMarked
public final class SkinModule implements FeatureModule {

    private static final ModuleId ID = ModuleId.of("skin");

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
        // The /skin command is Bukkit-facing and is registered by the adapter wiring.
        return List.of();
    }

    @Override
    public List<ListenerFactory> listeners() {
        // The pre-login listener is Bukkit-facing and is registered by the adapter wiring.
        return List.of();
    }

    @Override
    public List<MigrationSet> migrations() {
        // player_skins is part of the persistence baseline (db/migration), always applied by the persistence
        // layer, so the module owns no additional Flyway location.
        return List.of();
    }

    @Override
    public boolean enabled(ConfigStore config) {
        // The module ships DISABLED: an online-mode server gains nothing from it, and the servers that do need it
        // (cracked, or Bedrock-facing) turn it on deliberately.
        return config.getBoolean(configRoot() + ".enabled", false);
    }

    @Override
    public void start(ModuleContext ctx) {
        this.running = true;
    }

    @Override
    public void stop() {
        this.running = false;
    }

    /** True while the module is started; the login path observes this and dresses nobody once it is false. */
    public boolean isRunning() {
        return running;
    }
}
