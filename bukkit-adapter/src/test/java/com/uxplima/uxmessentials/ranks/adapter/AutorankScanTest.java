package com.uxplima.uxmessentials.ranks.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.ranks.adapter.outbound.AutorankScan;
import com.uxplima.uxmessentials.ranks.application.CurrentRank;
import com.uxplima.uxmessentials.ranks.application.RanksConfig.AutorankSettings;
import com.uxplima.uxmessentials.ranks.application.Rankup;
import com.uxplima.uxmessentials.ranks.application.port.PlayerRankRepository;
import com.uxplima.uxmessentials.ranks.application.port.RankActionRunner;
import com.uxplima.uxmessentials.ranks.application.port.RankEconomy;
import com.uxplima.uxmessentials.ranks.application.port.RankRequirementEvaluator;
import com.uxplima.uxmessentials.ranks.domain.PlayerRank;
import com.uxplima.uxmessentials.ranks.domain.Prestige;
import com.uxplima.uxmessentials.ranks.domain.Rank;
import com.uxplima.uxmessentials.ranks.domain.RankId;
import com.uxplima.uxmessentials.ranks.domain.RankLadder;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * {@link AutorankScan}: one scan pass promotes each online player whose next-rank requirements are already met and
 * leaves the ineligible where they are, reusing the {@link Rankup} pipeline per player on their own entity thread;
 * and a scan whose {@code autorank.enabled} is false schedules no repeating task. The charge-cost flag itself is
 * pinned at the use-case level ({@code RankupTest}); here the {@link Rankup} is driven with a real ladder and fake
 * ports so the scan's enumerate-then-hop behaviour is exercised end to end.
 */
class AutorankScanTest {

    private static final Rank FIRST = new Rank(RankId.of("first"), 10, "First", 0L, List.of(), List.of());
    // SECOND carries a requirement so the evaluator (allow/deny) actually gates promotion; a requirement-free rank
    // would be vacuously eligible and the evaluator never consulted.
    private static final Rank SECOND =
            new Rank(RankId.of("second"), 20, "Second", 0L, List.of("money 1000"), List.of());
    private static final RankLadder LADDER = RankLadder.of(List.of(FIRST, SECOND));

    private ServerMock server;
    private CapturingScheduler scheduler;
    private FakeRepository repo;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        scheduler = new CapturingScheduler(server);
        repo = new FakeRepository();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aScanPromotesAnEligibleOnlinePlayer() {
        PlayerMock player = server.addPlayer("Ada");
        AutorankScan scan = scan(allowAll(), AutorankSettings.from(FixedConfig.enabled()));

        scan.tick();

        assertThat(repo.rankOf(player.getUniqueId())).isEqualTo(RankId.of("second"));
    }

    @Test
    void aScanLeavesAnIneligiblePlayerAtTheirRank() {
        PlayerMock player = server.addPlayer("Bpl");
        AutorankScan scan = scan(denyAll(), AutorankSettings.from(FixedConfig.enabled()));

        scan.tick();

        assertThat(repo.rankOf(player.getUniqueId())).isEqualTo(RankId.of("first"));
    }

    @Test
    void startSchedulesNothingWhenAutorankIsDisabled() {
        AutorankScan scan = scan(allowAll(), AutorankSettings.from(FixedConfig.disabled()));

        AutoCloseable handle = scan.start();

        assertThat(scheduler.repeatCount).isZero();
        assertThat(handle).isNotNull();
    }

    @Test
    void startSchedulesTheRepeatingScanWhenAutorankIsEnabled() {
        server.addPlayer("Cly");
        AutorankScan scan = scan(allowAll(), AutorankSettings.from(FixedConfig.enabled()));

        scan.start();

        assertThat(scheduler.repeatCount).isEqualTo(1);
        assertThat(scheduler.period).isEqualTo(Duration.ofMinutes(5));
    }

    private AutorankScan scan(RankRequirementEvaluator requirements, AutorankSettings settings) {
        Rankup rankup = new Rankup(
                new CurrentRank(repo, LADDER),
                repo,
                LADDER,
                requirements,
                new NoopActionRunner(),
                Optional.<RankEconomy>empty());
        return new AutorankScan(server, scheduler, rankup, settings, new NoopLogger());
    }

    private static RankRequirementEvaluator allowAll() {
        return (who, requirement) -> true;
    }

    private static RankRequirementEvaluator denyAll() {
        return (who, requirement) -> false;
    }

    /** A repository defaulting an absent player to the first rank and recording every advance, keyed by uuid. */
    private static final class FakeRepository implements PlayerRankRepository {
        private final Map<UUID, PlayerRank> stored = new ConcurrentHashMap<>();

        @Override
        public Optional<PlayerRank> find(UUID playerId) {
            return Optional.ofNullable(stored.get(playerId));
        }

        @Override
        public void save(UUID playerId, RankId rankId, Prestige prestige) {
            stored.put(playerId, new PlayerRank(rankId, prestige));
        }

        RankId rankOf(UUID playerId) {
            return stored.getOrDefault(playerId, new PlayerRank(RankId.of("first"), Prestige.INITIAL))
                    .rankId();
        }
    }

    /** A scheduler that runs entity hops inline for an online player and captures the repeating-task registration. */
    private static final class CapturingScheduler implements Scheduler {
        private final ServerMock server;
        private int repeatCount;
        private Duration period = Duration.ZERO;

        CapturingScheduler(ServerMock server) {
            this.server = server;
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
            Player bukkit = server.getPlayer(player.uuid());
            if (bukkit != null && bukkit.isOnline()) {
                task.run();
            }
        }

        @Override
        public void async(Runnable task) {
            task.run();
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            task.run();
        }

        @Override
        public AutoCloseable repeatGlobal(Runnable task, Duration initialDelay, Duration period) {
            this.repeatCount++;
            this.period = period;
            return () -> {};
        }
    }

    /** A no-op action runner: the scan reuses the rankup pipeline, but the ladder here declares no actions. */
    private static final class NoopActionRunner implements RankActionRunner {
        @Override
        public void run(PlayerRef who, List<String> actionLines) {
            // nothing to run: the test ladder's ranks carry no actions
        }
    }

    /** Silences the operator logger; no scan path in these tests logs, so nothing is asserted on it. */
    private static final class NoopLogger implements Logger {
        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }

    /** A map-backed {@link com.uxplima.uxmessentials.shared.application.port.ConfigStore} for the autorank keys. */
    private record FixedConfig(Map<String, Object> values)
            implements com.uxplima.uxmessentials.shared.application.port.ConfigStore {

        static FixedConfig enabled() {
            return new FixedConfig(Map.of("autorank.enabled", true));
        }

        static FixedConfig disabled() {
            return new FixedConfig(Map.of("autorank.enabled", false));
        }

        @Override
        public boolean getBoolean(String path, boolean fallback) {
            return values.get(path) instanceof Boolean b ? b : fallback;
        }

        @Override
        public String getString(String path, String fallback) {
            return values.get(path) instanceof String s ? s : fallback;
        }

        @Override
        public int getInt(String path, int fallback) {
            return values.get(path) instanceof Integer i ? i : fallback;
        }

        @Override
        public long getLong(String path, long fallback) {
            return values.get(path) instanceof Number n ? n.longValue() : fallback;
        }

        @Override
        public double getDouble(String path, double fallback) {
            return values.get(path) instanceof Number n ? n.doubleValue() : fallback;
        }

        @Override
        public List<String> getStringList(String path, List<String> fallback) {
            return values.get(path) instanceof List<?> ? new ArrayList<>(fallback) : fallback;
        }
    }
}
