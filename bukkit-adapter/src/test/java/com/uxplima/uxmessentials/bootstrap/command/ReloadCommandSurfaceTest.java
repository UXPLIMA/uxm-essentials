package com.uxplima.uxmessentials.bootstrap.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.command.CommandSender;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.mojang.brigadier.tree.CommandNode;
import com.uxplima.uxmessentials.bootstrap.di.DefaultModuleRegistry;
import com.uxplima.uxmessentials.migration.adapter.MigrationImportService;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityListLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.ManagementGuiRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.ManagementHubView;
import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import com.uxplima.uxmessentials.shared.application.module.ModuleRegistry;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.application.reload.ReloadTask;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/**
 * Guards {@code /uxmess reload} against the regression it was written for: the subcommand once replied with a
 * success line while doing no work at all, so an operator's config edit silently never took effect. These tests
 * pin that running the literal actually runs the registered {@link ReloadTask}s, and that a scoped run narrows to
 * the named module while still re-reading the shared kernel sources.
 */
class ReloadCommandSurfaceTest {

    private static final ModuleId COMMUNICATION = ModuleId.of("communication");

    @Test
    void reloadIsWiredAsAChildOfTheUxmessRoot() {
        UxmessCommand command = command(List.of());

        CommandNode<CommandSourceStack> reload = command.build().getChild("reload");

        assertThat(reload)
                .as("/uxmess reload must be a child literal of the root")
                .isNotNull();
        assertThat(reload.getChild("module"))
                .as("/uxmess reload <module> must accept a module argument")
                .isNotNull();
    }

    @Test
    void reloadRunsEveryRegisteredTask() throws Exception {
        List<String> ran = new ArrayList<>();
        UxmessCommand command = command(List.of(
                ReloadTask.kernel("config", () -> ran.add("config"), "re-read"),
                ReloadTask.forModule(COMMUNICATION, () -> ran.add("communication"), "re-read")));

        int result = command.build().getChild("reload").getCommand().run(contextFor(sourceStack()));

        // The whole point: the reply must follow real work, not stand in for it.
        assertThat(ran).containsExactly("config", "communication");
        assertThat(result).isEqualTo(com.mojang.brigadier.Command.SINGLE_SUCCESS);
    }

    @Test
    void aThrowingTaskDoesNotPropagateThroughTheRun() throws Exception {
        List<String> ran = new ArrayList<>();
        UxmessCommand command = command(List.of(
                ReloadTask.kernel(
                        "config",
                        () -> {
                            throw new IllegalStateException("malformed HOCON");
                        },
                        "re-read"),
                ReloadTask.kernel("messages", () -> ran.add("messages"), "re-read")));

        // The InlineScheduler runs the async and onGlobal stages on this thread, so a leaked exception would
        // surface here. A bad config file must degrade to a FAIL line, never abort the run.
        int result = command.build().getChild("reload").getCommand().run(contextFor(sourceStack()));

        assertThat(ran).containsExactly("messages");
        assertThat(result).isEqualTo(com.mojang.brigadier.Command.SINGLE_SUCCESS);
    }

    @Test
    void overlappingReloadIsRejectedInsteadOfStartingASecondDiskPass() throws Exception {
        HoldingScheduler scheduler = new HoldingScheduler();
        AtomicInteger runs = new AtomicInteger();
        UxmessCommand command =
                command(List.of(ReloadTask.kernel("config", runs::incrementAndGet, "re-read")), scheduler);
        CommandNode<CommandSourceStack> reload = command.build().getChild("reload");

        reload.getCommand().run(contextFor(sourceStack()));
        reload.getCommand().run(contextFor(sourceStack()));

        assertThat(scheduler.pending()).hasSize(1);
        scheduler.runPending();
        assertThat(runs).hasValue(1);
    }

    @Test
    void aggregateRestartWarningParticipatesInTheSummaryStatus() throws Exception {
        ConfigStore config = Mockito.mock(ConfigStore.class);
        Mockito.when(config.scoped(Mockito.anyString())).thenReturn(config);
        Mockito.when(config.getBoolean(Mockito.anyString(), Mockito.anyBoolean()))
                .thenReturn(true);
        UxmessCommand command = command(List.of(), new InlineScheduler(), config);
        CommandSender sender = Mockito.mock(CommandSender.class);

        command.build().getChild("reload").getCommand().run(contextFor(sourceStack(sender)));

        ArgumentCaptor<Component> lines = ArgumentCaptor.forClass(Component.class);
        Mockito.verify(sender, Mockito.atLeastOnce()).sendMessage(lines.capture());
        List<String> plain = lines.getAllValues().stream()
                .map(PlainTextComponentSerializer.plainText()::serialize)
                .toList();
        assertThat(plain).anyMatch(line -> line.startsWith("[RESTART] module wiring:"));
        assertThat(plain).anyMatch(line -> line.contains("1 restart-required"));
    }

    private static UxmessCommand command(List<ReloadTask> tasks) {
        return command(tasks, new InlineScheduler());
    }

    private static UxmessCommand command(List<ReloadTask> tasks, Scheduler scheduler) {
        ConfigStore config = Mockito.mock(ConfigStore.class);
        Mockito.when(config.scoped(Mockito.anyString())).thenReturn(config);
        return command(tasks, scheduler, config);
    }

    private static UxmessCommand command(List<ReloadTask> tasks, Scheduler scheduler, ConfigStore config) {
        ModuleRegistry registry = new DefaultModuleRegistry();
        MigrationImportService service = Mockito.mock(MigrationImportService.class);
        return new UxmessCommand(
                registry,
                config,
                new MigrationImportNode(service),
                guiNode(),
                permissionsNode(),
                placeholdersNode(),
                scheduler,
                List.of(),
                tasks);
    }

    /** A permissions node pointed at a scratch folder: this guard never runs its export. */
    private static PermissionsSubcommand permissionsNode() {
        return new PermissionsSubcommand(Path.of("build", "tmp", "permissions-guard"), Mockito.mock(Logger.class));
    }

    /** A placeholders node pointed at a scratch folder: this guard never runs its export either. */
    private static PlaceholdersSubcommand placeholdersNode() {
        return new PlaceholdersSubcommand(Path.of("build", "tmp", "placeholders-guard"), Mockito.mock(Logger.class));
    }

    /** A minimal /uxmess gui node: this surface guard only runs the reload child, never opens the hub. */
    private static GuiSubcommand guiNode() {
        Scheduler scheduler = new InlineScheduler();
        Permissions permissions = Mockito.mock(Permissions.class);
        Messages messages = Mockito.mock(Messages.class);
        ManagementGuiRegistry guiRegistry = new ManagementGuiRegistry();
        GuiText guiText = new GuiText(messages);
        EntityListLayout layout = EntityListLayout.paginatedDefault(org.bukkit.Material.NETHER_STAR);
        com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus menus =
                com.uxplima.uxmessentials.shared.menu.TestMenuEngine.create(messages, scheduler)
                        .menus();
        ManagementHubView hub = new ManagementHubView(menus, guiText, scheduler, permissions, guiRegistry, layout);
        return new GuiSubcommand(guiRegistry, hub, permissions, messages);
    }

    @SuppressWarnings("unchecked") // Mockito.mock on a generic type needs the unchecked cast.
    private static com.mojang.brigadier.context.CommandContext<CommandSourceStack> contextFor(
            CommandSourceStack source) {
        com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx =
                Mockito.mock(com.mojang.brigadier.context.CommandContext.class);
        Mockito.when(ctx.getSource()).thenReturn(source);
        return ctx;
    }

    private static CommandSourceStack sourceStack() {
        return sourceStack(Mockito.mock(org.bukkit.command.CommandSender.class));
    }

    private static CommandSourceStack sourceStack(CommandSender sender) {
        CommandSourceStack source = Mockito.mock(CommandSourceStack.class);
        Mockito.when(source.getSender()).thenReturn(sender);
        return source;
    }

    /** Runs every scheduled stage inline so the off-tick reload run executes synchronously in the test thread. */
    private static final class InlineScheduler implements Scheduler {
        @Override
        public void onGlobal(Runnable task) {
            task.run();
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            task.run();
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            task.run();
        }

        @Override
        public void async(Runnable task) {
            task.run();
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            task.run();
        }
    }

    /** Holds async work so two command invocations overlap deterministically. */
    private static final class HoldingScheduler implements Scheduler {
        private final List<Runnable> pending = new ArrayList<>();

        List<Runnable> pending() {
            return List.copyOf(pending);
        }

        void runPending() {
            List<Runnable> tasks = List.copyOf(pending);
            pending.clear();
            tasks.forEach(Runnable::run);
        }

        @Override
        public void onGlobal(Runnable task) {
            task.run();
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            task.run();
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            task.run();
        }

        @Override
        public void async(Runnable task) {
            pending.add(task);
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            pending.add(task);
        }
    }
}
