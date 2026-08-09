package com.uxplima.uxmessentials.moderation.adapter.outbound.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.api.view.UxmSanction;
import com.uxplima.uxmessentials.api.view.UxmSanctionAction;
import com.uxplima.uxmessentials.api.view.UxmSanctionKind;
import com.uxplima.uxmessentials.api.view.UxmSanctionRecord;
import com.uxplima.uxmessentials.api.view.UxmWarn;
import com.uxplima.uxmessentials.moderation.application.port.ModerationRepository;
import com.uxplima.uxmessentials.moderation.application.port.SanctionHistory;
import com.uxplima.uxmessentials.moderation.domain.BanEntry;
import com.uxplima.uxmessentials.moderation.domain.IpBan;
import com.uxplima.uxmessentials.moderation.domain.Issuer;
import com.uxplima.uxmessentials.moderation.domain.JailEntry;
import com.uxplima.uxmessentials.moderation.domain.JailState;
import com.uxplima.uxmessentials.moderation.domain.ModerationProfile;
import com.uxplima.uxmessentials.moderation.domain.MuteEntry;
import com.uxplima.uxmessentials.moderation.domain.MuteState;
import com.uxplima.uxmessentials.moderation.domain.SanctionAction;
import com.uxplima.uxmessentials.moderation.domain.SanctionHistoryEntry;
import com.uxplima.uxmessentials.moderation.domain.SeenRecord;
import com.uxplima.uxmessentials.moderation.domain.TempbanState;
import com.uxplima.uxmessentials.moderation.domain.Warn;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.query.QueryDoubles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The published moderation query: it answers about the present moment, it answers for a player nobody has a
 * name for, and a punishment whose clock has run out is reported as absent rather than as an expired one.
 */
class ModerationQueriesTest {

    private static final UUID TARGET = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-08-09T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final Issuer STAFF = Issuer.stored(Optional.of(UUID.randomUUID()), "Mod");

    private FakeModerationRepository repository;
    private FakeHistory history;
    private QueryDoubles.InlineScheduler scheduler;

    @BeforeEach
    void setUp() {
        repository = new FakeModerationRepository();
        history = new FakeHistory();
        scheduler = new QueryDoubles.InlineScheduler();
    }

    @Test
    void everyReadRunsOffTheCallingThread() {
        queries().ban(TARGET).join();
        queries().mute(TARGET).join();
        queries().jail(TARGET).join();
        queries().warns(TARGET).join();
        queries().history(TARGET, 10).join();

        assertThat(scheduler.asyncCalls()).isEqualTo(5);
    }

    @Test
    void aCleanPlayerIsServingNothing() {
        assertThat(queries().ban(TARGET).join()).isEmpty();
        assertThat(queries().mute(TARGET).join()).isEmpty();
        assertThat(queries().jail(TARGET).join()).isEmpty();
        assertThat(queries().warns(TARGET).join()).isEmpty();
    }

    @Test
    void aStandingTempbanCarriesItsIssuerReasonAndExpiry() {
        repository.tempban = new TempbanState.Active(
                NOW.plus(Duration.ofDays(3)), STAFF, Optional.of("griefing"), NOW.minus(Duration.ofHours(1)));

        UxmSanction ban = queries().ban(TARGET).join().orElseThrow();

        assertThat(ban.kind()).isEqualTo(UxmSanctionKind.BAN);
        assertThat(ban.playerId()).isEqualTo(TARGET);
        assertThat(ban.issuer().name()).isEqualTo("Mod");
        assertThat(ban.issuer().isConsole()).isFalse();
        assertThat(ban.reason()).contains("griefing");
        assertThat(ban.expiresAt()).contains(NOW.plus(Duration.ofDays(3)));
        assertThat(ban.isPermanent()).isFalse();
    }

    @Test
    void aBanWhoseClockHasRunOutIsAbsentRatherThanExpired() {
        repository.tempban = new TempbanState.Active(
                NOW.minus(Duration.ofMinutes(1)), STAFF, Optional.empty(), NOW.minus(Duration.ofDays(1)));

        assertThat(queries().ban(TARGET).join())
                .as("a consumer asking whether a player is banned wants an answer, not a state machine")
                .isEmpty();
    }

    @Test
    void aPermanentMuteHasNoExpiry() {
        repository.mute = new MuteState.Permanent(STAFF, Optional.of("spam"), NOW.minus(Duration.ofDays(2)));

        UxmSanction mute = queries().mute(TARGET).join().orElseThrow();

        assertThat(mute.kind()).isEqualTo(UxmSanctionKind.MUTE);
        assertThat(mute.expiresAt()).isEmpty();
        assertThat(mute.isPermanent()).isTrue();
    }

    @Test
    void aTimedMuteCarriesItsExpiry() {
        repository.mute = new MuteState.Timed(
                NOW.plus(Duration.ofHours(2)), STAFF, Optional.empty(), NOW.minus(Duration.ofMinutes(30)));

        assertThat(queries().mute(TARGET).join().orElseThrow().expiresAt()).contains(NOW.plus(Duration.ofHours(2)));
    }

    @Test
    void anOnlineOnlyJailPublishesNoExpiryBecauseItHasNone() {
        repository.jail = new JailState.Active(
                "spawn",
                Optional.of(Duration.ofMinutes(30)),
                Optional.empty(),
                STAFF,
                Optional.of("cooling off"),
                NOW.minus(Duration.ofMinutes(5)));

        UxmSanction jail = queries().jail(TARGET).join().orElseThrow();

        assertThat(jail.kind()).isEqualTo(UxmSanctionKind.JAIL);
        assertThat(jail.expiresAt())
                .as("an online-only sentence counts down while the player is logged in, so no instant ends it")
                .isEmpty();
        assertThat(jail.reason()).contains("cooling off");
    }

    @Test
    void warnsAreTheOnesStillCounting() {
        repository.warns.add(new Warn(STAFF, Optional.of("first"), NOW.minus(Duration.ofDays(1)), Optional.empty()));

        List<UxmWarn> warns = queries().warns(TARGET).join();

        assertThat(warns).hasSize(1);
        assertThat(warns.getFirst().reason()).contains("first");
        assertThat(warns.getFirst().expiresAt()).isEmpty();
        assertThat(repository.warnsAskedAt)
                .as("the repository drops expired warns, so it has to be told when now is")
                .isEqualTo(NOW);
    }

    @Test
    void historyCarriesLiftedPunishmentsToo() {
        history.add(new SanctionHistoryEntry(
                SanctionAction.BAN,
                TARGET,
                STAFF,
                Optional.of("griefing"),
                NOW.minus(Duration.ofDays(5)),
                Optional.of(NOW.minus(Duration.ofDays(1))),
                Optional.empty()));
        history.add(new SanctionHistoryEntry(
                SanctionAction.UNBAN,
                TARGET,
                STAFF,
                Optional.empty(),
                NOW.minus(Duration.ofDays(1)),
                Optional.empty(),
                Optional.empty()));

        List<UxmSanctionRecord> lines = queries().history(TARGET, 10).join();

        assertThat(lines)
                .extracting(UxmSanctionRecord::action)
                .containsExactly(UxmSanctionAction.BAN, UxmSanctionAction.UNBAN);
        assertThat(queries().ban(TARGET).join())
                .as("a ban that was lifted is history and not a sanction")
                .isEmpty();
    }

    @Test
    void aHistoryLimitBelowOneIsRefusedBeforeAnythingIsScheduled() {
        ModerationQueries queries = queries();

        assertThatThrownBy(() -> queries.history(TARGET, 0).join()).isInstanceOf(IllegalArgumentException.class);
        assertThat(scheduler.asyncCalls()).isZero();
    }

    @Test
    void aPlayerNobodyHasANameForIsStillAnswered() {
        repository.mute = new MuteState.Permanent(STAFF, Optional.empty(), NOW);

        assertThat(queries().mute(UUID.randomUUID()).join()).isPresent();
    }

    private ModerationQueries queries() {
        return new ModerationQueries(repository, history, new QueryDoubles.MapLookup(), scheduler, CLOCK);
    }

    /** Holds one state per axis, which is all a query about one player ever reads. */
    private static final class FakeModerationRepository implements ModerationRepository {

        private final List<Warn> warns = new ArrayList<>();
        private MuteState mute = MuteState.none();
        private JailState jail = JailState.none();
        private TempbanState tempban = TempbanState.none();
        private Instant warnsAskedAt = Instant.EPOCH;

        @Override
        public ModerationProfile load(PlayerRef target) {
            return new ModerationProfile(target, mute, jail, tempban);
        }

        @Override
        public MuteState loadMute(PlayerRef target) {
            return mute;
        }

        @Override
        public JailState loadJail(PlayerRef target) {
            return jail;
        }

        @Override
        public TempbanState loadTempban(PlayerRef target) {
            return tempban;
        }

        @Override
        public List<BanEntry> activeBans(Instant now, int limit) {
            return List.of();
        }

        @Override
        public List<MuteEntry> activeMutes(Instant now, int limit) {
            return List.of();
        }

        @Override
        public List<JailEntry> activeJails(Instant now, int limit) {
            return List.of();
        }

        @Override
        public void saveMute(PlayerRef target, MuteState state) {
            throw new AssertionError("a query must never write");
        }

        @Override
        public void saveJail(PlayerRef target, JailState state) {
            throw new AssertionError("a query must never write");
        }

        @Override
        public void saveTempban(PlayerRef target, TempbanState state) {
            throw new AssertionError("a query must never write");
        }

        @Override
        public int appendWarn(PlayerRef target, Warn warn) {
            throw new AssertionError("a query must never write");
        }

        @Override
        public List<Warn> warns(PlayerRef target, Instant now) {
            warnsAskedAt = now;
            return List.copyOf(warns);
        }

        @Override
        public int clearWarns(PlayerRef target) {
            throw new AssertionError("a query must never write");
        }

        @Override
        public int clearWarnsByActor(PlayerRef target, PlayerRef actor) {
            throw new AssertionError("a query must never write");
        }

        @Override
        public void saveIpBan(IpBan ban) {
            throw new AssertionError("a query must never write");
        }

        @Override
        public boolean removeIpBan(String ip) {
            throw new AssertionError("a query must never write");
        }

        @Override
        public Optional<IpBan> activeIpBan(String ip, Instant now) {
            return Optional.empty();
        }

        @Override
        public void recordSeen(PlayerRef who, Optional<String> ip, Instant at) {
            throw new AssertionError("a query must never write");
        }

        @Override
        public void recordIpSeen(UUID uuid, String ip, Instant now) {
            throw new AssertionError("a query must never write");
        }

        @Override
        public Optional<SeenRecord> seen(PlayerRef who) {
            return Optional.empty();
        }

        @Override
        public Set<String> ipHistory(UUID uuid) {
            return Set.of();
        }

        @Override
        public List<UUID> altsByIp(String ip, UUID self) {
            return List.of();
        }

        @Override
        public List<UUID> altsByAnyIp(Set<String> ips, UUID self) {
            return List.of();
        }

        @Override
        public void ensureUserExists(PlayerRef target, Instant at) {
            throw new AssertionError("a query must never write");
        }

        @Override
        public boolean isLockedDown() {
            return false;
        }

        @Override
        public void setLockedDown(boolean enabled) {
            throw new AssertionError("a query must never write");
        }
    }

    private static final class FakeHistory implements SanctionHistory {

        private final List<SanctionHistoryEntry> entries = new ArrayList<>();

        void add(SanctionHistoryEntry entry) {
            entries.add(entry);
        }

        @Override
        public void append(SanctionHistoryEntry entry) {
            throw new AssertionError("a query must never write");
        }

        @Override
        public List<SanctionHistoryEntry> banHistory(UUID target, int limit) {
            return List.copyOf(entries);
        }

        @Override
        public List<SanctionHistoryEntry> muteHistory(UUID target, int limit) {
            return List.copyOf(entries);
        }

        @Override
        public List<SanctionHistoryEntry> recentForTarget(UUID target, int limit) {
            return List.copyOf(entries);
        }

        @Override
        public List<SanctionHistoryEntry> recentByActor(UUID actor, int limit) {
            return List.copyOf(entries);
        }

        @Override
        public List<SanctionHistoryEntry> allSince(Instant threshold, int limit) {
            return List.copyOf(entries);
        }
    }
}
