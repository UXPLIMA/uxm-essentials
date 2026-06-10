package com.uxplima.uxmessentials.bootstrap.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.tree.CommandNode;
import com.uxplima.uxmessentials.bootstrap.di.DefaultModuleRegistry;
import com.uxplima.uxmessentials.migration.adapter.MigrationImportService;
import com.uxplima.uxmessentials.shared.application.command.CommandId;
import com.uxplima.uxmessentials.shared.application.health.HealthCheck;
import com.uxplima.uxmessentials.shared.application.health.HealthResult;
import com.uxplima.uxmessentials.shared.application.module.ModuleRegistry;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Pins the {@code /uxmess doctor} subcommand onto the operator surface. {@code doctor} is a child of the
 * {@code /uxmess} root (not its own catalog literal), so it is not tracked by {@code CommandCatalogDriftTest};
 * this guard asserts the literal is wired under the root and — the point of the command — that its run is
 * resilient: a health check whose probe throws is still rendered (as a {@code FAIL} entry) rather than aborting
 * the run, because every check goes through {@link HealthCheck#safe()}.
 */
class DoctorCommandSurfaceTest {

    @Test
    void doctorIsWiredAsAChildOfTheUxmessRoot() {
        UxmessCommand command = command(List.of(okCheck()));

        CommandNode<CommandSourceStack> doctor = command.build().getChild("doctor");

        assertThat(doctor)
                .as("/uxmess doctor must be a child literal of the root")
                .isNotNull();
        assertThat(doctor.getName()).isEqualTo("doctor");
    }

    @Test
    void uxmessLiteralStaysAValidCommandId() {
        // doctor lives under the existing /uxmess catalog literal, which must stay a valid id.
        assertThat(new CommandId("uxmess").value()).isEqualTo("uxmess");
    }

    @Test
    void aThrowingCheckDoesNotPropagateThroughTheRun() throws Exception {
        HealthCheck explodes = new HealthCheck() {
            @Override
            public String name() {
                return "boom";
            }

            @Override
            public HealthResult check() {
                throw new IllegalStateException("probe failed");
            }
        };
        // The InlineScheduler runs the async + onGlobal stages synchronously, so the whole doctor run executes on
        // this thread; a leaked exception would surface here. It must not — every check is safe()-wrapped.
        UxmessCommand command = command(List.of(okCheck(), explodes));
        CommandSourceStack source = sourceStack();

        int result = command.build().getChild("doctor").getCommand().run(contextFor(source));

        assertThat(result).isEqualTo(com.mojang.brigadier.Command.SINGLE_SUCCESS);
    }

    private static UxmessCommand command(List<HealthCheck> checks) {
        ModuleRegistry registry = new DefaultModuleRegistry();
        ConfigStore config = Mockito.mock(ConfigStore.class);
        Mockito.when(config.scoped(Mockito.anyString())).thenReturn(config);
        MigrationImportService service = Mockito.mock(MigrationImportService.class);
        return new UxmessCommand(registry, config, new MigrationImportNode(service), new InlineScheduler(), checks);
    }

    private static HealthCheck okCheck() {
        return new HealthCheck() {
            @Override
            public String name() {
                return "ok";
            }

            @Override
            public HealthResult check() {
                return HealthResult.ok("up");
            }
        };
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
        CommandSourceStack source = Mockito.mock(CommandSourceStack.class);
        org.bukkit.command.CommandSender sender = Mockito.mock(org.bukkit.command.CommandSender.class);
        Mockito.when(source.getSender()).thenReturn(sender);
        return source;
    }

    /** Runs every scheduled stage inline so the off-tick doctor run executes synchronously in the test thread. */
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
}
