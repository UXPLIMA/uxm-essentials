package com.uxplima.uxmessentials.messaging.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.messaging.application.port.MessageToggleStore;
import com.uxplima.uxmessentials.messaging.application.port.ReplyPartnerStore;
import com.uxplima.uxmessentials.messaging.application.port.ReplyRoutingStore;
import com.uxplima.uxmessentials.messaging.application.port.SpyTargetStore;
import com.uxplima.uxmessentials.messaging.domain.LastConversation;
import com.uxplima.uxmessentials.shared.application.permission.SocialPermissions;
import com.uxplima.uxmessentials.shared.application.port.PlayerSwitchStore;
import com.uxplima.uxmessentials.shared.application.settings.HeldSettings;
import com.uxplima.uxmessentials.shared.application.settings.PlayerSwitches;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.testing.FakePermissions;
import com.uxplima.uxmessentials.testing.OnlineLookup;
import com.uxplima.uxmessentials.testing.QueuedScheduler;
import com.uxplima.uxmessentials.testing.QuietLog;
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
    void aReadThatLandsBeforeTheWriteKeepsTheChangeMadeHere() {
        survival.login(ALEX);
        survival.messages.toggle(ALEX);

        survival.held.refresh(ALEX.uuid());
        survival.scheduler.lastFirst().runNext();

        assertThat(survival.messages.acceptsMessages(ALEX)).isFalse();
        survival.scheduler.runAll();
        assertThat(database.switches.get(ALEX.uuid())).containsEntry(MessagingSwitches.MESSAGES, false);
    }

    @Test
    void quickChangesLandInTheOrderTheyWereMade() {
        survival.login(ALEX);

        survival.messages.toggle(ALEX);
        survival.messages.toggle(ALEX);
        survival.messages.toggle(ALEX);
        survival.scheduler.runAll();

        assertThat(database.switches.get(ALEX.uuid())).containsEntry(MessagingSwitches.MESSAGES, false);
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

        assertThat(survival.messages.acceptsMessages(ALEX)).isFalse();
        assertThat(survival.messages.acceptsMessages(BOB)).isTrue();
    }

    /** The shared database every backend reads and writes. */
    private static final class Database {

        final Map<UUID, Map<String, Boolean>> switches = new HashMap<>();
        final Map<UUID, Set<UUID>> targets = new HashMap<>();
        final Map<UUID, LastConversation> partners = new HashMap<>();

        final PlayerSwitchStore switchRows = new PlayerSwitchStore() {
            @Override
            public Map<String, Boolean> load(UUID player, Set<String> names) {
                Map<String, Boolean> owned = new HashMap<>(switches.getOrDefault(player, Map.of()));
                owned.keySet().retainAll(names);
                return owned;
            }

            @Override
            public void save(UUID player, Map<String, Boolean> changed) {
                switches.computeIfAbsent(player, id -> new HashMap<>()).putAll(changed);
            }
        };

        final SpyTargetStore targetRows = new SpyTargetStore() {
            @Override
            public Set<UUID> load(UUID spy) {
                return targets.getOrDefault(spy, Set.of());
            }

            @Override
            public void save(UUID spy, Set<UUID> watched) {
                targets.put(spy, Set.copyOf(watched));
            }
        };

        final ReplyPartnerStore partnerRows = new ReplyPartnerStore() {
            @Override
            public Optional<LastConversation> load(UUID owner) {
                return Optional.ofNullable(partners.get(owner));
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

        Backend() {
            QuietLog log = new QuietLog();
            switches = new PlayerSwitches(MessagingSwitches.NAMES, database.switchRows, scheduler, log, announced::add);
            MessagingSwitches named = new MessagingSwitches(switches);
            messages = named.messages();
            replies = named.replies();
            spies = new SharedSocialSpy(
                    switches, database.targetRows, players, permissions, scheduler, log, announced::add);
            conversations = new SharedConversations(database.partnerRows, scheduler, log);
            held = HeldSettings.of(switches, spies, conversations);
        }

        void login(PlayerRef player) {
            held.load(player.uuid());
            players.with(player);
        }

        void logout(PlayerRef player) {
            held.forget(player.uuid());
            players.leave(player);
        }
    }
}
