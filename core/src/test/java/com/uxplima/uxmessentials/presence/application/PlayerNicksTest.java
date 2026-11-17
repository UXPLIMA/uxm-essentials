package com.uxplima.uxmessentials.presence.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.presence.application.port.NickDisplay;
import com.uxplima.uxmessentials.presence.application.port.NickRecords;
import com.uxplima.uxmessentials.presence.application.port.NickWrite;
import com.uxplima.uxmessentials.presence.domain.NickState;
import com.uxplima.uxmessentials.shared.application.port.PlayerNameRepository;
import com.uxplima.uxmessentials.shared.domain.PlayerName;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.testing.ManualScheduler;
import com.uxplima.uxmessentials.testing.QuietLog;
import com.uxplima.uxmessentials.testing.RecordingNotifier;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * A nick is kept in the database, so it survives a relog and follows the player to another server; a nick that copies
 * another player's name or nick is refused, and one that copied a name before that name joined is taken back; a write
 * another backend overtook is refused and never confirmed; a Bedrock player keeps their own dotted name.
 */
class PlayerNicksTest {

    private static final PlayerRef ALEX = new PlayerRef(UUID.randomUUID(), "Alex");
    private static final PlayerRef STEVE = new PlayerRef(UUID.randomUUID(), "Steve");
    /** A Bedrock player through Floodgate: the Xbox user id under a zero high half, the name prefixed with a dot. */
    private static final PlayerRef BEDROCK =
            new PlayerRef(UUID.fromString("00000000-0000-0000-0009-01f2a3b4c5d6"), ".BedrockGamer");

    private final Rows rows = new Rows();
    private final Map<UUID, PlayerRef> accounts = new HashMap<>();
    private final RecordingNotifier notifier = new RecordingNotifier();
    private final Backend survival = new Backend();
    private final Backend lobby = new Backend();

    PlayerNicksTest() {
        for (PlayerRef account : List.of(ALEX, STEVE, BEDROCK)) {
            accounts.put(account.uuid(), account);
        }
    }

    @Test
    void aNickSurvivesARelogAndFollowsThePlayerToAnotherServer() {
        survival.login(ALEX);
        survival.setNick.set(ALEX, ALEX, "Captain");
        survival.scheduler.runAsync();
        assertThat(survival.shown).containsEntry(ALEX.uuid(), Optional.of("Captain"));
        assertThat(notifier.keys()).contains("presence.nick-set");

        survival.quit(ALEX);
        lobby.login(ALEX);

        assertThat(lobby.shown).containsEntry(ALEX.uuid(), Optional.of("Captain"));
    }

    @Test
    void aNickSetOnAnotherServerIsShownWhereThePlayerIsWhenItIsAnnounced() {
        survival.login(ALEX);
        lobby.setNick.set(STEVE, ALEX, "Captain");
        lobby.scheduler.runAsync();
        assertThat(survival.shown).containsEntry(ALEX.uuid(), Optional.empty());

        survival.announced(ALEX);

        assertThat(survival.shown).containsEntry(ALEX.uuid(), Optional.of("Captain"));
        assertThat(lobby.shown).doesNotContainKey(ALEX.uuid());
    }

    @Test
    void anotherPlayersNameInAnyCaseIsRefused() {
        survival.login(ALEX);

        survival.setNick.set(ALEX, ALEX, "steve");
        survival.scheduler.runAsync();

        assertThat(notifier.keys()).containsExactly("presence.nick-taken");
        assertThat(rows.read(ALEX.uuid())).isEqualTo(NickState.NONE);
    }

    @Test
    void anotherPlayersNickIsRefused() {
        survival.login(ALEX);
        survival.login(STEVE);
        survival.setNick.set(STEVE, STEVE, "Captain");
        survival.scheduler.runAsync();

        survival.setNick.set(ALEX, ALEX, "CAPTAIN");
        survival.scheduler.runAsync();

        assertThat(notifier.keys()).containsExactly("presence.nick-set", "presence.nick-taken");
        assertThat(survival.nicks.nickOf(ALEX.uuid())).isEmpty();
    }

    @Test
    void aBedrockPlayerKeepsTheirOwnDottedNameButNobodyElseTakesIt() {
        survival.login(BEDROCK);
        survival.login(ALEX);

        survival.setNick.set(BEDROCK, BEDROCK, ".bedrockgamer");
        survival.setNick.set(ALEX, ALEX, ".BedrockGamer");
        survival.scheduler.runAsync();

        assertThat(survival.nicks.nickOf(BEDROCK.uuid())).contains(".bedrockgamer");
        assertThat(notifier.keys()).containsExactly("presence.nick-invalid", "presence.nick-set");
    }

    @Test
    void anOfflineModeCaseVariantOfAnotherAccountIsRefused() {
        PlayerRef lowerSteve =
                new PlayerRef(UUID.nameUUIDFromBytes("OfflinePlayer:steve".getBytes(StandardCharsets.UTF_8)), "steve");
        accounts.put(lowerSteve.uuid(), lowerSteve);
        survival.login(STEVE);

        survival.setNick.set(STEVE, STEVE, "steve");
        survival.scheduler.runAsync();

        assertThat(notifier.keys()).containsExactly("presence.nick-taken");
    }

    @Test
    void aMalformedNickIsRefusedBeforeAnythingIsRead() {
        survival.login(ALEX);

        survival.setNick.set(ALEX, ALEX, "<red>Admin");

        assertThat(notifier.keys()).containsExactly("presence.nick-invalid");
        assertThat(survival.scheduler.pendingAsync()).isZero();
    }

    @Test
    void anOfflinePlayersNickIsWrittenAndAnnouncedAndTheirNextLoginShowsIt() {
        survival.setNick.set(STEVE, ALEX, "Captain");
        survival.scheduler.runAsync();

        assertThat(rows.read(ALEX.uuid()).value()).contains("Captain");
        assertThat(survival.announcements).containsExactly(ALEX.uuid());
        assertThat(notifier.keys()).containsExactly("presence.nick-set-other");

        lobby.login(ALEX);
        assertThat(lobby.shown).containsEntry(ALEX.uuid(), Optional.of("Captain"));
    }

    @Test
    void clearingShowsTheAccountNameAgainEverywhere() {
        survival.login(ALEX);
        survival.setNick.set(ALEX, ALEX, "Captain");
        survival.scheduler.runAsync();

        survival.clearNick.clear(ALEX);
        survival.scheduler.runAsync();

        assertThat(survival.shown).containsEntry(ALEX.uuid(), Optional.empty());
        assertThat(rows.read(ALEX.uuid()).value()).isEmpty();
        assertThat(notifier.keys()).containsExactly("presence.nick-set", "presence.nick-cleared");
        lobby.login(ALEX);
        assertThat(lobby.shown).containsEntry(ALEX.uuid(), Optional.empty());
    }

    @Test
    void aWriteAnotherBackendOvertookIsRefusedNeverConfirmedAndTheNickIsReadAgain() {
        survival.login(ALEX);
        survival.setNick.set(STEVE, ALEX, "Mine");
        // Between this backend's read and its write, the lobby sets another nick: the row moved on.
        rows.beforeNextWrite =
                () -> rows.write(ALEX.uuid(), rows.read(ALEX.uuid()).version(), "Theirs");

        survival.scheduler.runAsync();

        assertThat(notifier.keys()).containsExactly("presence.nick-conflict");
        assertThat(rows.read(ALEX.uuid()).value()).contains("Theirs");
        assertThat(survival.shown).containsEntry(ALEX.uuid(), Optional.of("Theirs"));
        assertThat(survival.announcements).isEmpty();
    }

    @Test
    void aNameThatJoinsTakesBackTheNickAnotherPlayerCopiedFromIt() {
        survival.login(STEVE);
        survival.setNick.set(STEVE, STEVE, "Newbie");
        survival.scheduler.runAsync();
        assertThat(survival.shown).containsEntry(STEVE.uuid(), Optional.of("Newbie"));
        PlayerRef newbie = new PlayerRef(UUID.randomUUID(), "newbie");
        accounts.put(newbie.uuid(), newbie);

        lobby.nicks.nameJoined(newbie.uuid(), newbie.name());
        lobby.scheduler.runAsync();
        survival.announced(STEVE);

        assertThat(rows.read(STEVE.uuid()).value()).isEmpty();
        assertThat(survival.shown).containsEntry(STEVE.uuid(), Optional.empty());
        assertThat(lobby.announcements).containsExactly(STEVE.uuid());
    }

    @Test
    void aNickThePlayerSetOnTheirOwnNameIsKeptWhenTheyJoin() {
        survival.setNick.set(STEVE, STEVE, "steve");
        survival.scheduler.runAsync();

        survival.nicks.nameJoined(STEVE.uuid(), STEVE.name());

        assertThat(rows.read(STEVE.uuid()).value()).contains("steve");
    }

    @Test
    void aLegacyNickIsTakenOverOnlyWhenNobodyElseHoldsIt() {
        survival.setNick.set(STEVE, STEVE, "Captain");
        survival.scheduler.runAsync();

        assertThat(survival.nicks.adoptLegacy(ALEX.uuid(), ALEX.name(), "steve"))
                .isTrue();
        assertThat(survival.nicks.adoptLegacy(ALEX.uuid(), ALEX.name(), "captain"))
                .isTrue();
        assertThat(survival.nicks.adoptLegacy(ALEX.uuid(), ALEX.name(), "<red>Admin"))
                .isTrue();
        assertThat(rows.read(ALEX.uuid())).isEqualTo(NickState.NONE);

        assertThat(survival.nicks.adoptLegacy(ALEX.uuid(), ALEX.name(), "Sailor"))
                .isTrue();
        assertThat(rows.read(ALEX.uuid()).value()).contains("Sailor");
        // A nick the database holds since is never replaced by the old value.
        assertThat(survival.nicks.adoptLegacy(ALEX.uuid(), ALEX.name(), "Pirate"))
                .isTrue();
        assertThat(rows.read(ALEX.uuid()).value()).contains("Sailor");
    }

    /** One backend: its nicks, its scheduler and what it showed. */
    private final class Backend {

        final ManualScheduler scheduler = new ManualScheduler();
        final Map<UUID, Optional<String>> shown = new HashMap<>();
        final List<UUID> announcements = new ArrayList<>();
        final Set<UUID> here = new HashSet<>();
        final PlayerNicks nicks;
        final SetNick setNick;
        final ClearNick clearNick;

        Backend() {
            NickDisplay display = (player, nick) -> {
                if (here.contains(player)) {
                    shown.put(player, nick);
                }
            };
            nicks = new PlayerNicks(rows, new Names(), display, scheduler, new QuietLog(), announcements::add);
            setNick = new SetNick(nicks, scheduler, notifier.notifier(), new QuietLog());
            clearNick = new ClearNick(nicks, scheduler, notifier.notifier(), new QuietLog());
        }

        void login(PlayerRef player) {
            nicks.settings().load(player.uuid());
            here.add(player.uuid());
            nicks.joined(player.uuid());
        }

        void quit(PlayerRef player) {
            here.remove(player.uuid());
            nicks.settings().forget(player.uuid());
        }

        /** Another backend announced a change for {@code player}. */
        void announced(PlayerRef player) {
            nicks.settings().refresh(player.uuid());
            scheduler.runAsync();
        }
    }

    /** The shared table: one row per player, a version counted up per write, one holder per nick in any case. */
    private static final class Rows implements NickRecords {

        final Map<UUID, NickState> stored = new HashMap<>();

        @Nullable Runnable beforeNextWrite;

        @Override
        public NickState read(UUID player) {
            return stored.getOrDefault(player, NickState.NONE);
        }

        @Override
        public NickWrite write(UUID player, long version, String nick) {
            Runnable interleaved = beforeNextWrite;
            beforeNextWrite = null;
            if (interleaved != null) {
                interleaved.run();
            }
            if (read(player).version() != version) {
                return new NickWrite.Stale();
            }
            if (!nick.isEmpty() && holders(nick).stream().anyMatch(holder -> !holder.equals(player))) {
                return new NickWrite.Taken();
            }
            NickState next = new NickState(nick, version + 1);
            stored.put(player, next);
            return new NickWrite.Written(next);
        }

        @Override
        public List<UUID> holders(String nick) {
            List<UUID> holders = new ArrayList<>();
            stored.forEach((player, state) -> {
                if (!state.nick().isEmpty() && state.nick().equalsIgnoreCase(nick.strip())) {
                    holders.add(player);
                }
            });
            return holders;
        }
    }

    /** The shared name index: every account that joined, by name in any case. */
    private final class Names implements PlayerNameRepository {

        @Override
        public List<PlayerName> loadRecent(int limit) {
            return List.of();
        }

        @Override
        public List<PlayerName> underName(String name, int limit) {
            List<PlayerName> found = new ArrayList<>();
            for (PlayerRef account : accounts.values()) {
                if (account.name().toLowerCase(Locale.ROOT).equals(name.toLowerCase(Locale.ROOT))) {
                    found.add(new PlayerName(account.uuid(), account.name(), 0L));
                }
            }
            return found;
        }

        @Override
        public Set<String> sharedNames() {
            return Set.of();
        }

        @Override
        public Optional<PlayerName> byUuid(UUID uuid) {
            return Optional.empty();
        }

        @Override
        public void upsert(PlayerName record) {}

        @Override
        public int count() {
            return accounts.size();
        }
    }
}
