package com.uxplima.uxmessentials.customcommands.application;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import com.uxplima.uxmessentials.shared.application.module.BrigadierCommand;
import com.uxplima.uxmessentials.shared.application.module.CommandSpec;
import com.uxplima.uxmessentials.shared.application.module.FeatureModule;
import com.uxplima.uxmessentials.shared.application.module.ListenerFactory;
import com.uxplima.uxmessentials.shared.application.module.MigrationSet;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import org.jspecify.annotations.NullMarked;

/**
 * The customcommands bounded context as a first-class {@link FeatureModule}: the operator's own commands, declared
 * in {@code commands/custom/*.conf} and running the same action vocabulary a menu item runs. When enabled, every
 * definition on disk becomes a real Brigadier command and {@code /customcmd} manages them; when disabled, nothing
 * loads, no command word is claimed and the module holds no state.
 *
 * <p><b>Ships enabled but inert.</b> A fresh install carries one sample definition and nothing else, so the module
 * changes nothing until an operator writes a file. The gate therefore defaults to {@code true}; an operator flips
 * {@code modules.customcommands.enabled = false} to turn the whole surface off.
 *
 * <p>The real nodes are built in the adapter wiring, because a definition's command node is assembled from a file
 * the kernel cannot see. The {@link CommandSpec} published here is the catalog descriptor for {@code /customcmd}
 * itself; the per-definition commands are added during wiring, ahead of the catalog resolution, so they inherit the
 * same rename, alias and per-locale alias handling a built-in command gets.
 */
@NullMarked
public final class CustomCommandsModule implements FeatureModule {

    private static final ModuleId ID = ModuleId.of("customcommands");
    private static final Duration DRAIN_TIMEOUT = Duration.ofSeconds(5);

    private final AtomicInteger inFlight = new AtomicInteger();
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
        // The real /customcmd node is built in the adapter wiring over the loaded catalog; this descriptor names the
        // literal and its base node so the command catalog and the feature-module drift guard see the surface.
        return List.of(spec(
                "customcmd",
                "uxmessentials.customcommand.admin",
                descriptor("customcmd", "Manage operator-defined custom commands")));
    }

    @Override
    public List<ListenerFactory> listeners() {
        // The one listener this context needs (movement cancelling a command warmup) is installed by the adapter
        // wiring alongside the commands, because it holds the same warmup tracker the use case writes to.
        return List.of();
    }

    @Override
    public List<MigrationSet> migrations() {
        // Nothing is persisted: a definition is an operator-authored .conf file, so the module owns no Flyway
        // location.
        return List.of();
    }

    @Override
    public boolean enabled(ConfigStore config) {
        return config.getBoolean(configRoot() + ".enabled", true);
    }

    @Override
    public void start(ModuleContext ctx) {
        this.running = true;
    }

    @Override
    public void stop() {
        this.running = false;
        awaitDrain();
    }

    /** True while the module is started. */
    public boolean isRunning() {
        return running;
    }

    private void awaitDrain() {
        long deadline = System.nanoTime() + DRAIN_TIMEOUT.toNanos();
        while (inFlight.get() > 0 && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
    }

    private static CommandSpec spec(String literal, String permission, BrigadierCommand command) {
        Function<ModuleContext, BrigadierCommand> factory = ctx -> command;
        return new CommandSpec(literal, permission, factory);
    }

    private static BrigadierCommand descriptor(String literal, String description) {
        return new CustomCommandDescriptor(literal, description);
    }

    /** Platform-neutral handle naming the {@code /customcmd} literal; the adapter builds the real node. */
    private record CustomCommandDescriptor(String literal, String description) implements BrigadierCommand {}
}
