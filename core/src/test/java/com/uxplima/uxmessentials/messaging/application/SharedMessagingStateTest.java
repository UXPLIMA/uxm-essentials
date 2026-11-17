package com.uxplima.uxmessentials.messaging.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import com.uxplima.uxmessentials.messaging.application.port.MessageToggleStore;
import com.uxplima.uxmessentials.messaging.application.port.ReplyPartnerStore;
import com.uxplima.uxmessentials.messaging.application.port.ReplyRoutingStore;
import com.uxplima.uxmessentials.messaging.application.port.SpyTargetStore;
import com.uxplima.uxmessentials.messaging.domain.LastConversation;
import com.uxplima.uxmessentials.shared.application.permission.SocialPermissions;
import com.uxplima.uxmessentials.shared.application.port.PlayerSwitchStore;
import com.uxplima.uxmessentials.shared.application.port.PlayerSwitchStore.Switch;
import com.uxplima.uxmessentials.shared.application.settings.HeldSettings;
import com.uxplima.uxmessentials.shared.application.settings.PlayerSwitches;
import com.uxplima.uxmessentials.shared.application.settings.SettingsSessions;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.testing.FakePermissions;
import com.uxplima.uxmessentials.testing.MutableClock;
import com.uxplima.uxmessentials.testing.OnlineLookup;
import com.uxplima.uxmessentials.testing.QueuedScheduler;
import com.uxplima.uxmessentials.testing.QuietLog;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * The messaging settings a player sets on one backend ({@code /msgtoggle}, {@code /rtoggle}, {@code /socialspy}, the
 * {@code /reply} partner) are the ones every other backend of the network reads, through one shared database.
 */
class SharedMessagingStateTest {

    private static final PlayerRef ALEX = new PlayerRef(UUID.randomUUID(), "Alex");
    private static final PlayerRef BOB = new PlayerRef(UUID.randomUUID(), "Bob");
    private static final PlayerRef MOD = new PlayerRef(UUID.randomUUID(), "Mod");
    private static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");

    private final MutableClock clock = new MutableClock(NOW);
    private final Database database = new Database();
    private final Backend survival = new Backend();
    private final Backend lobby = new Backend();

    @Test
    void aPlayerWhoTurnedMessagesAndRepliesOffKeepsThemOffOnTheNextBackend() {
        survival.login(ALEX);
        survival.messages.toggle(ALEX);
        survival.replies.toggle(ALEX);
        survival.scheduler.runAll();

        survival.logout(ALEX);
        lobby.login(ALEX);

        assertThat(lobby.messages.acceptsMessages(ALEX)).isFalse();
        assertThat(lobby.replies.acceptsReplies(ALEX)).isFalse();
    }

    @Test
    void aPlayerWhoNeverTouchedTheSwitchesTakesMessagesAndReplies() {
        lobby.login(BOB);

        assertThat(lobby.messages.acceptsMessages(BOB)).isTrue();
        assertThat(lobby.replies.acceptsReplies(BOB)).isTrue();
    }

    @Test
    void theReplyPartnerFollowsThePlayerToTheNextBackend() {
        survival.login(ALEX);
        LastConversation withBob = LastConversation.with(BOB, NOW);
        survival.conversations.remember(ALEX, withBob);
        survival.scheduler.runAll();

        survival.logout(ALEX);
        lobby.login(ALEX);

        assertThat(lobby.conversations.latest(ALEX)).contains(withBob);
        assertThat(survival.announced).contains(ALEX.uuid());
    }

    @Test
    void aSpyKeepsSpyingAndWatchingOnTheNextBackend() {
        survival.login(MOD);
        survival.spies.toggle(MOD);
        survival.spies.toggleTarget(MOD, BOB);
        survival.scheduler.runAll();

        survival.logout(MOD);
        lobby.login(MOD);
        lobby.permissions.grant(MOD, SocialPermissions.MSG_SOCIALSPY);

        assertThat(lobby.spies.isSpying(MOD)).isTrue();
        assertThat(lobby.spies.observersOf(ALEX, BOB)).containsExactly(MOD);
        lobby.spies.toggle(MOD);
        assertThat(lobby.spies.observersOf(ALEX, BOB)).containsExactly(MOD);
        assertThat(lobby.spies.observersOf(ALEX, ALEX)).isEmpty();
    }

    @Test
    void aSpyWhoLostTheSocialSpyNodeIsShownNothing() {
        survival.login(MOD);
        survival.spies.toggle(MOD);

        assertThat(survival.spies.isSpying(MOD)).isTrue();
        assertThat(survival.spies.observersOf(ALEX, BOB)).isEmpty();
        assertThat(survival.spies.activeSpies()).isEmpty();
    }

    @Test
    void aDuplicateLoginKeepsTheSettingsOfTheNewSession() {
        survival.login(ALEX);
        survival.messages.toggle(ALEX);
        survival.spies.toggleTarget(ALEX, BOB);
        survival.scheduler.runAll();
        survival.logout(ALEX);

        // The new connection is read, then the old session quits, then the new session joins.
        survival.sessions.loggingIn(ALEX.uuid());
        survival.sessions.quit(ALEX.uuid());
        survival.sessions.joined(ALEX.uuid());
        survival.scheduler.runAll();
        survival.sessions.closed(ALEX.uuid(), true);

        assertThat(survival.messages.acceptsMessages(ALEX)).isFalse();
        assertThat(survival.spies.ready(ALEX)).isTrue();
        survival.spies.toggleTarget(ALEX, MOD);
        survival.scheduler.runAll();
        assertThat(database.targets.get(ALEX.uuid())).containsExactlyInAnyOrder(BOB.uuid(), MOD.uuid());
    }

    @Test
    void aChangeToSettingsThatWereNotReadIsRefusedAndWritesNothing() {
        survival.login(MOD);
        survival.spies.toggleTarget(MOD, BOB);
        survival.scheduler.runAll();
        survival.logout(MOD);
        database.unreadable = true;
        survival.login(MOD);
        database.unreadable = false;

        assertThat(survival.spies.ready(MOD)).isFalse();
        assertThat(survival.messages.ready(MOD)).isFalse();
        survival.spies.toggleTarget(MOD, ALEX);
        survival.messages.toggle(MOD);
        survival.switches.adopt(MOD.uuid(), MessagingSwitches.REPLIES, false);
        survival.scheduler.runAll();

        assertThat(database.targets.get(MOD.uuid())).containsExactly(BOB.uuid());
        assertThat(database.switches.getOrDefault(MOD.uuid(), Map.of())).isEmpty();
    }

    @Test
    void twoBackendsWatchingForOneSpyKeepEachOthersPlayers() {
        survival.login(MOD);
        lobby.login(MOD);

        survival.spies.toggleTarget(MOD, ALEX);
        lobby.spies.toggleTarget(MOD, BOB);
        survival.scheduler.runAll();
        lobby.scheduler.runAll();

        assertThat(database.targets.get(MOD.uuid())).containsExactlyInAnyOrder(ALEX.uuid(), BOB.uuid());
    }

    @Test
    void aChangeWrittenAfterTheNextBackendReadTheRowReachesItWhenAnnounced() {
        survival.login(ALEX);
        // The proxy lets the next backend read the player's row before the old backend's write has landed.
        lobby.login(ALEX);
        survival.messages.toggle(ALEX);
        survival.scheduler.runAll();

        assertThat(lobby.messages.acceptsMessages(ALEX)).isTrue();
        survival.announced.forEach(lobby.held::refresh);
        lobby.scheduler.runAll();

        assertThat(lobby.messages.acceptsMessages(ALEX)).isFalse();
    }

    @Test
    void aChangeAnnouncedWhileTheLoginReadRunsIsReadAgain() {
        survival.login(ALEX);
        // While the next backend reads the row, the old one writes the change and announces it.
        database.duringRead = player -> {
            database.duringRead = null;
            survival.messages.toggle(ALEX);
            survival.scheduler.runAll();
            survival.announced.forEach(lobby.held::refresh);
        };

        lobby.login(ALEX);

        assertThat(lobby.messages.acceptsMessages(ALEX)).isFalse();
    }

    @Test
    void anOlderSettingWrittenLateDoesNotReplaceANewerOne() {
        survival.login(ALEX);
        lobby.login(ALEX);
        survival.messages.toggle(ALEX);
        clock.advance(Duration.ofSeconds(1));
        lobby.messages.toggle(ALEX);
        lobby.messages.toggle(ALEX);
        lobby.scheduler.runAll();
        survival.scheduler.runAll();

        assertThat(database.storedOn(ALEX.uuid(), MessagingSwitches.MESSAGES)).contains(true);
    }

    @Test
    void aReadThatLandsBeforeTheWriteKeepsTheChangeMadeHere() {
        survival.login(ALEX);
        survival.messages.toggle(ALEX);

        survival.held.refresh(ALEX.uuid());
        survival.scheduler.lastFirst().runNext();

        assertThat(survival.messages.acceptsMessages(ALEX)).isFalse();
        survival.scheduler.runAll();
        assertThat(database.storedOn(ALEX.uuid(), MessagingSwitches.MESSAGES)).contains(false);
    }

    @Test
    void quickChangesLandInTheOrderTheyWereMade() {
        survival.login(ALEX);

        survival.messages.toggle(ALEX);
        survival.messages.toggle(ALEX);
        survival.messages.toggle(ALEX);
        survival.scheduler.runAll();

        assertThat(database.storedOn(ALEX.uuid(), MessagingSwitches.MESSAGES)).contains(false);
        survival.logout(ALEX);
        lobby.login(ALEX);
        assertThat(lobby.messages.acceptsMessages(ALEX)).isFalse();
    }

    @Test
    void aPlayerWhoComesBackBeforeTheirChangeIsWrittenStillHasIt() {
        survival.login(ALEX);
        survival.messages.toggle(ALEX);
        survival.logout(ALEX);

        survival.login(ALEX);

        assertThat(survival.messages.acceptsMessages(ALEX)).isFalse();
    }

    @Test
    void aSettingKeptByAnEarlierVersionIsTakenOnlyWhenNoSharedOneExists() {
        survival.login(ALEX);
        survival.switches.adopt(ALEX.uuid(), MessagingSwitches.MESSAGES, false);
        survival.login(BOB);
        survival.messages.toggle(BOB);
        survival.messages.toggle(BOB);
        survival.switches.adopt(BOB.uuid(), MessagingSwitches.MESSAGES, false);
        survival.scheduler.runAll();

        assertThat(survival.messages.acceptsMessages(ALEX)).isFalse();
        assertThat(survival.messages.acceptsMessages(BOB)).isTrue();
        assertThat(survival.switches.stored(ALEX.uuid(), MessagingSwitches.MESSAGES))
                .isTrue();
    }

    /** The shared database every backend reads and writes. */
    private static final class Database {

        final Map<UUID, Map<String, Switch>> switches = new HashMap<>();
        final Map<UUID, Set<UUID>> targets = new HashMap<>();
        final Map<UUID, LastConversation> partners = new HashMap<>();
        boolean unreadable;

        @Nullable Consumer<UUID> duringRead;

        Optional<Boolean> storedOn(UUID player, String name) {
            return Optional.ofNullable(switches.getOrDefault(player, Map.of()).get(name))
                    .map(Switch::on);
        }

        void reading(UUID player) {
            if (unreadable) {
                throw new IllegalStateException("database down");
            }
            Consumer<UUID> hook = duringRead;
            if (hook != null) {
                hook.accept(player);
            }
        }

        final PlayerSwitchStore switchRows = new PlayerSwitchStore() {
            @Override
            public Map<String, Switch> load(UUID player, Set<String> names) {
                Map<String, Switch> owned = new HashMap<>(switches.getOrDefault(player, Map.of()));
                reading(player);
                owned.keySet().retainAll(names);
                return owned;
            }

            @Override
            public void save(UUID player, Map<String, Switch> changed) {
                Map<String, Switch> rows = switches.computeIfAbsent(player, id -> new HashMap<>());
                changed.forEach((name, set) -> {
                    Switch stored = rows.get(name);
                    if (stored == null || stored.changedAtMillis() <= set.changedAtMillis()) {
                        rows.put(name, set);
                    }
                });
            }
        };

        final SpyTargetStore targetRows = new SpyTargetStore() {
            @Override
            public Set<UUID> load(UUID spy) {
                Set<UUID> watched = Set.copyOf(targets.getOrDefault(spy, Set.of()));
                reading(spy);
                return watched;
            }

            @Override
            public void change(UUID spy, Set<UUID> added, Set<UUID> removed) {
                Set<UUID> rows = targets.computeIfAbsent(spy, id -> new HashSet<>());
                rows.removeAll(removed);
                rows.addAll(added);
            }
        };

        final ReplyPartnerStore partnerRows = new ReplyPartnerStore() {
            @Override
            public Optional<LastConversation> load(UUID owner) {
                Optional<LastConversation> last = Optional.ofNullable(partners.get(owner));
                reading(owner);
                return last;
            }

            @Override
            public void save(UUID owner, LastConversation conversation) {
                partners.put(owner, conversation);
            }
        };
    }

    /** One backend: its own memory of the players on it, over the shared database. */
    private final class Backend {

        final QueuedScheduler scheduler = new QueuedScheduler();
        final OnlineLookup players = new OnlineLookup();
        final FakePermissions permissions = new FakePermissions();
        final Set<UUID> announced = new HashSet<>();
        final PlayerSwitches switches;
        final MessageToggleStore messages;
        final ReplyRoutingStore replies;
        final SharedSocialSpy spies;
        final SharedConversations conversations;
        final HeldSettings held;
        final SettingsSessions sessions;

        Backend() {
            QuietLog log = new QuietLog();
            switches = new PlayerSwitches(
                    MessagingSwitches.NAMES, database.switchRows, clock, scheduler, log, announced::add);
            MessagingSwitches named = new MessagingSwitches(switches);
            messages = named.messages();
            replies = named.replies();
            spies = new SharedSocialSpy(
                    switches, database.targetRows, players, permissions, scheduler, log, announced::add);
            conversations = new SharedConversations(database.partnerRows, scheduler, log, announced::add);
            held = HeldSettings.of(switches, spies, conversations);
            sessions = new SettingsSessions(held, scheduler);
        }

        void login(PlayerRef player) {
            sessions.loggingIn(player.uuid());
            players.with(player);
        }

        void logout(PlayerRef player) {
            sessions.quit(player.uuid());
            players.leave(player);
        }
    }
}
