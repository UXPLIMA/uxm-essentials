package com.uxplima.uxmessentials.api;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.bukkit.plugin.PluginManager;

import com.uxplima.uxmessentials.homes.domain.HomeSlot;
import com.uxplima.uxmessentials.homes.domain.event.HomeCreated;
import com.uxplima.uxmessentials.homes.domain.event.HomeCreating;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.BukkitDomainGate;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.BukkitEventBridge;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.EventBridgeRegistry;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.EventBridges;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.VetoRegistry;
import com.uxplima.uxmessentials.shared.application.port.DomainGate;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

/**
 * What the developer API costs the servers that do not use it, which is nearly all of them.
 *
 * <p>Every home set, every teleport, every economy transaction now publishes a fact and, at the vetoable points, asks
 * a question. On a server with no consumer plugin all of that has to come to nothing, or the API would be a tax the
 * majority pays for a minority's benefit. Both paths are written to check for listeners before they build anything,
 * so the claim to defend is that each is a map lookup and an array-length read: single-digit nanoseconds, no
 * allocation, nothing scheduled.
 *
 * <p>The plugin manager here is a proxy that throws. Nothing on the no-listener path may reach it, and if a future
 * change makes it, this benchmark fails rather than quietly reporting a bigger number.
 *
 * <p>Budget: both no-listener paths &le; 50 ns/op with nothing allocated and nothing scheduled. Measured on JDK 25:
 * publish 7.6 ns/op, veto 7.6 ns/op, against a 1.1 ns/op floor for the same call with no bridge behind it. Run it
 * with {@code ./gradlew :bukkit-adapter:jmh --args="DeveloperApiOverheadBenchmark"}.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class DeveloperApiOverheadBenchmark {

    private static final PlayerRef OWNER = new PlayerRef(UUID.randomUUID(), "Bench");
    private static final Position SOMEWHERE = new Position(new WorldRef(UUID.randomUUID(), "world"), 1, 64, 2, 0f, 0f);

    private BukkitEventBridge bridge;
    private DomainGate gate;
    private DomainGate allowAll;
    private HomeCreated fact;
    private HomeCreating proposal;

    @Setup
    public void setUp() {
        EventBridgeRegistry facts = new EventBridgeRegistry();
        EventBridges.installAll(facts);
        VetoRegistry vetoes = new VetoRegistry();
        EventBridges.installAllVetoes(vetoes);

        PluginManager plugins = forbiddenPluginManager();
        bridge = new BukkitEventBridge(facts, new ExplodingScheduler(), plugins, new SilentLogger());
        gate = new BukkitDomainGate(vetoes, plugins, new SilentLogger());
        allowAll = DomainGate.allowAll();

        fact = new HomeCreated(OWNER, HomeSlot.of(0), SOMEWHERE);
        proposal = new HomeCreating(OWNER, HomeSlot.of(0), SOMEWHERE);
    }

    /** Publishing a fact nobody listens for: the cost every server pays on every home, teleport and transaction. */
    @Benchmark
    public void publishWithNoListener() {
        bridge.accept(fact);
    }

    /** Asking a question nobody listens for, on the path that has a player waiting for the answer. */
    @Benchmark
    public boolean vetoWithNoListener() {
        return gate.allows(proposal);
    }

    /** The floor: what the same call costs with no bridge at all, so the two numbers can be read against each other. */
    @Benchmark
    public boolean baselineWithoutTheApi() {
        return allowAll.allows(proposal);
    }

    /** A plugin manager that fails loudly, since the whole point is that these paths never reach one. */
    private static PluginManager forbiddenPluginManager() {
        return (PluginManager) Proxy.newProxyInstance(
                DeveloperApiOverheadBenchmark.class.getClassLoader(),
                new Class<?>[] {PluginManager.class},
                (proxy, method, args) -> {
                    throw new AssertionError("the no-listener path reached the plugin manager: " + method.getName());
                });
    }

    /** Likewise for the scheduler: nothing may be scheduled when there is nobody to deliver to. */
    private static final class ExplodingScheduler implements Scheduler {
        @Override
        public void onGlobal(Runnable task) {
            throw new AssertionError("scheduled work with no listener registered");
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            throw new AssertionError("scheduled work with no listener registered");
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            throw new AssertionError("scheduled work with no listener registered");
        }

        @Override
        public void async(Runnable task) {
            throw new AssertionError("scheduled work with no listener registered");
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            throw new AssertionError("scheduled work with no listener registered");
        }
    }

    private static final class SilentLogger implements Logger {
        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }
}
