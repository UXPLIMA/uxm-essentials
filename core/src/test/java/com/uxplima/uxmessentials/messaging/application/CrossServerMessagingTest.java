package com.uxplima.uxmessentials.messaging.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import com.uxplima.uxmessentials.messaging.application.port.AfkStatus;
import com.uxplima.uxmessentials.messaging.application.port.ConversationStore;
import com.uxplima.uxmessentials.messaging.application.port.IgnoreStore;
import com.uxplima.uxmessentials.messaging.application.port.MailRepository;
import com.uxplima.uxmessentials.messaging.application.port.MessageDelivery;
import com.uxplima.uxmessentials.messaging.application.port.MessageToggleStore;
import com.uxplima.uxmessentials.messaging.application.port.MessagingNetwork;
import com.uxplima.uxmessentials.messaging.application.port.ReplyRoutingStore;
import com.uxplima.uxmessentials.messaging.application.port.SocialSpyStore;
import com.uxplima.uxmessentials.messaging.application.port.StaffAudience;
import com.uxplima.uxmessentials.messaging.application.port.VanishVisibility;
import com.uxplima.uxmessentials.messaging.domain.IgnoreList;
import com.uxplima.uxmessentials.messaging.domain.IgnoreScope;
import com.uxplima.uxmessentials.messaging.domain.LastConversation;
import com.uxplima.uxmessentials.messaging.domain.MailBox;
import com.uxplima.uxmessentials.messaging.domain.MailItem;
import com.uxplima.uxmessentials.messaging.domain.MessageBody;
import com.uxplima.uxmessentials.messaging.domain.event.PrivateMessageSent;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.NetworkPresence;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.OnlinePlayer;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.staff.application.RemoteStaffChat;
import com.uxplima.uxmessentials.staff.application.SendStaffChat;
import com.uxplima.uxmessentials.staff.application.port.StaffChannel;
import com.uxplima.uxmessentials.staff.application.port.StaffChatNetwork;
import com.uxplima.uxmessentials.testing.RecordingEvents;
import org.junit.jupiter.api.Test;

/**
 * Three backends share one bus. A private message to a player on another backend arrives there under the target's
 * own switches, a reply finds its way back, social spies everywhere see each message once, and help requests and staff
 * chat reach the staff of every backend once, naming the server they came from.
 */
class CrossServerMessagingTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-15T12:00:00Z"), ZoneOffset.UTC);
    private static final PlayerRef ALEX = new PlayerRef(UUID.randomUUID(), "Alex");
    private static final PlayerRef BOB = new PlayerRef(UUID.randomUUID(), "Bob");
    private static final PlayerRef MOD = new PlayerRef(UUID.randomUUID(), "Mod");
    private static final PlayerRef ADMIN = new PlayerRef(UUID.randomUUID(), "Admin");
    private static final PlayerRef HELPER = new PlayerRef(UUID.randomUUID(), "Helper");

    private final Network network = new Network();
    private final Backend survival = network.backend("survival");
    private final Backend lobby = network.backend("lobby");
    private final Backend creative = network.backend("creative");

    CrossServerMessagingTest() {
        survival.join(ALEX);
        lobby.join(BOB);
    }

    @Test
    void aMessageToAPlayerOnAnotherBackendIsDeliveredThere() {
        survival.msg(ALEX, "Bob", "hi");

        assertThat(survival.lines()).containsExactly("Alex: sent to Bob: hi");
        assertThat(lobby.lines()).containsExactly("Bob: received from Alex: hi");
        assertThat(creative.lines()).isEmpty();
        assertThat(survival.events.published())
                .containsExactly(new PrivateMessageSent(ALEX, BOB, body("hi"), CLOCK.instant()));
        assertThat(lobby.events.published()).isEmpty();
    }

    @Test
    void aTargetWhoTurnedMessagesOffRefusesWithTheUsualLine() {
        lobby.toggledOff.add(BOB.uuid());

        survival.msg(ALEX, "Bob", "hi");

        assertThat(survival.lines()).containsExactly("Alex: msg.toggled-off");
        assertThat(lobby.lines()).isEmpty();
    }

    @Test
    void anIgnoringTargetSeesNothingWhileTheSenderReadsItAsSent() {
        network.ignores.put(BOB.uuid(), IgnoreList.empty(BOB).ignore(ALEX, IgnoreScope.ALL));

        survival.msg(ALEX, "Bob", "hi");

        assertThat(survival.lines()).containsExactly("Alex: sent to Bob: hi");
        assertThat(lobby.lines()).isEmpty();
        assertThat(survival.events.published()).isEmpty();
    }

    @Test
    void aVanishedTargetLooksOfflineToASenderWhoseBackendKnowsTheyAreVanished() {
        network.crossServerVanish = true;
        lobby.vanishedHere.add(BOB.uuid());

        survival.msg(ALEX, "Bob", "hi");

        assertThat(survival.lines()).containsExactly("Alex: msg.target-offline");
        assertThat(lobby.lines()).isEmpty();
    }

    @Test
    void aTargetVanishedOnTheirOwnBackendLooksOfflineWhenTheSenderBackendCannotKnow() {
        lobby.vanishedHere.add(BOB.uuid());
        lobby.spy(MOD);

        survival.msg(ALEX, "Bob", "hi");

        assertThat(survival.lines()).containsExactly("Alex: msg.target-offline");
        assertThat(lobby.lines()).isEmpty();
    }

    @Test
    void aVanishedTargetStillGetsTheMessageOfASenderWhoCanSeeThem() {
        lobby.vanishedHere.add(BOB.uuid());
        network.seeVanished.add(ALEX.uuid());

        survival.msg(ALEX, "Bob", "hi");

        assertThat(lobby.lines()).containsExactly("Bob: received from Alex: hi");
    }

    @Test
    void aReplyToAPartnerWhoVanishedSinceFindsNoOne() {
        survival.msg(ALEX, "Bob", "hi");
        lobby.vanishedHere.add(BOB.uuid());

        survival.reply.reply(ALEX, body("still there?"));

        assertThat(survival.lines()).containsExactly("Alex: sent to Bob: hi", "Alex: msg.no-reply-target");
        assertThat(lobby.lines()).containsExactly("Bob: received from Alex: hi");
    }

    @Test
    void aBackendWhereAnotherPluginRunsPrivateMessagesAnswersOffline() {
        lobby.steppedBack = true;

        survival.msg(ALEX, "Bob", "hi");

        assertThat(survival.lines()).containsExactly("Alex: msg.target-offline");
        assertThat(lobby.lines()).isEmpty();
    }

    @Test
    void aMutedSenderIsRefusedBeforeTheMessageLeaves() {
        network.muted.add(ALEX.uuid());

        survival.msg(ALEX, "Bob", "hi");

        assertThat(survival.lines()).containsExactly("Alex: msg.muted");
        assertThat(lobby.lines()).isEmpty();
    }

    @Test
    void anAwayTargetGetsTheMessageAndTheSenderIsTold() {
        lobby.away.put(BOB.uuid(), "lunch");

        survival.msg(ALEX, "Bob", "hi");

        assertThat(survival.lines())
                .containsExactly("Alex: sent to Bob: hi", "Alex: msg.target-afk{player=Bob, reason=lunch}");
        assertThat(lobby.lines()).containsExactly("Bob: received from Alex: hi");
    }

    @Test
    void theRecipientRepliesAcrossServersAndTheSenderRepliesBack() {
        survival.msg(ALEX, "Bob", "hi");

        lobby.reply.reply(BOB, body("hey"));
        survival.reply.reply(ALEX, body("how are you"));

        assertThat(survival.lines())
                .containsExactly(
                        "Alex: sent to Bob: hi", "Alex: received from Bob: hey", "Alex: sent to Bob: how are you");
        assertThat(lobby.lines())
                .containsExactly(
                        "Bob: received from Alex: hi",
                        "Bob: sent to Alex: hey",
                        "Bob: received from Alex: how are you");
    }

    @Test
    void aReplyToAPartnerWhoTurnedReplyRoutingOffFindsNoOne() {
        survival.msg(ALEX, "Bob", "hi");
        lobby.repliesOff.add(BOB.uuid());

        survival.reply.reply(ALEX, body("again"));

        assertThat(survival.lines()).containsExactly("Alex: sent to Bob: hi", "Alex: msg.no-reply-target");
        assertThat(lobby.lines()).containsExactly("Bob: received from Alex: hi");
    }

    @Test
    void socialSpiesOnEveryBackendSeeAMessageOnceEvenWhenTheBusDeliversTwice() {
        network.deliverTwice = true;
        survival.spy(HELPER);
        lobby.spy(MOD);
        creative.spy(ADMIN);

        survival.msg(ALEX, "Bob", "hi");

        assertThat(survival.lines()).containsExactly("Alex: sent to Bob: hi", "Helper: spy Alex -> Bob: hi");
        assertThat(lobby.lines()).containsExactly("Bob: received from Alex: hi", "Mod: spy Alex -> Bob: hi");
        assertThat(creative.lines()).containsExactly("Admin: spy Alex -> Bob: hi");
    }

    @Test
    void socialSpiesSeeAnIgnoredMessageButNotARefusedOne() {
        lobby.spy(MOD);
        creative.spy(ADMIN);
        network.ignores.put(BOB.uuid(), IgnoreList.empty(BOB).ignore(ALEX, IgnoreScope.ALL));
        survival.msg(ALEX, "Bob", "ignored");
        network.ignores.clear();
        lobby.toggledOff.add(BOB.uuid());

        survival.msg(ALEX, "Bob", "refused");

        assertThat(lobby.lines()).containsExactly("Mod: spy Alex -> Bob: ignored");
        assertThat(creative.lines()).containsExactly("Admin: spy Alex -> Bob: ignored");
    }

    @Test
    void aTargetWhoLeftTheirBackendBeforeTheMessageArrivedReadsAsOffline() {
        lobby.connected.remove(BOB.uuid());

        survival.msg(ALEX, "Bob", "hi");

        assertThat(survival.lines()).containsExactly("Alex: msg.target-offline");
        assertThat(lobby.lines()).isEmpty();
    }

    @Test
    void aBackendThatNeverAnswersEndsInTheOfflineLineOnceTheWaitIsOver() {
        network.unreachable.add("lobby");
        survival.msg(ALEX, "Bob", "hi");
        assertThat(survival.lines()).isEmpty();

        survival.endWaits();

        assertThat(survival.lines()).containsExactly("Alex: msg.target-offline");
    }

    @Test
    void aTargetWhoLeftBecomesMailWhenOfflineToMailIsOn() {
        Network mailing = new Network(true);
        Backend from = mailing.backend("survival");
        Backend to = mailing.backend("lobby");
        from.join(ALEX);
        to.join(BOB);
        to.connected.remove(BOB.uuid());

        from.msg(ALEX, "Bob", "hi");

        assertThat(from.lines()).containsExactly("Alex: msg.sent-to-mail{player=Bob}");
        assertThat(mailing.mail).extracting(item -> item.body().value()).containsExactly("hi");
    }

    @Test
    void anUnansweredMessageNeverBecomesMail() {
        Network mailing = new Network(true);
        Backend from = mailing.backend("survival");
        mailing.backend("lobby").join(BOB);
        from.join(ALEX);
        mailing.unreachable.add("lobby");

        from.msg(ALEX, "Bob", "hi");
        from.endWaits();

        assertThat(from.lines()).containsExactly("Alex: msg.target-offline");
        assertThat(mailing.mail).isEmpty();
    }

    @Test
    void anAnswerThatArrivesAfterTheWaitStillTellsTheSenderAndWritesNoMail() {
        Network mailing = new Network(true);
        Backend from = mailing.backend("survival");
        Backend to = mailing.backend("lobby");
        from.join(ALEX);
        to.join(BOB);
        mailing.holdAnswers = true;

        from.msg(ALEX, "Bob", "hi");
        from.endWaits();
        mailing.releaseAnswers();

        assertThat(from.lines()).containsExactly("Alex: msg.target-offline", "Alex: sent to Bob: hi");
        assertThat(to.lines()).containsExactly("Bob: received from Alex: hi");
        assertThat(mailing.mail).isEmpty();
    }

    @Test
    void aBackendThatAnsweredIsNotAskedAgainWhenTheWaitEnds() {
        survival.msg(ALEX, "Bob", "hi");

        survival.endWaits();

        assertThat(survival.lines()).containsExactly("Alex: sent to Bob: hi");
    }

    @Test
    void aHelpRequestReachesTheStaffOfEveryBackendOnceNamingItsServer() {
        network.deliverTwice = true;
        survival.staff(HELPER);
        lobby.staff(MOD);
        creative.staff(ADMIN);

        survival.helpOp.raise(ALEX, body("stuck"));

        assertThat(survival.lines()).containsExactly("Helper: helpop Alex: stuck", "Alex: helpop.sent");
        assertThat(lobby.lines()).containsExactly("Mod: helpop Alex on survival: stuck");
        assertThat(creative.lines()).containsExactly("Admin: helpop Alex on survival: stuck");
    }

    @Test
    void aHelpRequestCountsAsSentWhenStaffMayBeOnAnotherBackend() {
        lobby.staff(MOD);

        survival.helpOp.raise(ALEX, body("stuck"));

        assertThat(survival.lines()).containsExactly("Alex: helpop.sent");
    }

    @Test
    void aHelpRequestWithNobodyOnAnyOtherBackendSaysNoStaffAreOnline() {
        lobby.leave(BOB);

        survival.helpOp.raise(ALEX, body("stuck"));

        assertThat(survival.lines()).containsExactly("Alex: helpop.no-staff");
    }

    @Test
    void staffChatReachesTheStaffOfEveryBackendOnceNamingItsServer() {
        network.deliverTwice = true;
        survival.staff(HELPER);
        lobby.staff(MOD);
        creative.staff(ADMIN);

        survival.staffChat.send(HELPER, "watch spawn");

        assertThat(survival.lines()).containsExactly("Helper: staffchat Helper: watch spawn");
        assertThat(lobby.lines()).containsExactly("Mod: staffchat Helper on survival: watch spawn");
        assertThat(creative.lines()).containsExactly("Admin: staffchat Helper on survival: watch spawn");
    }

    private static MessageBody body(String text) {
        return MessageBody.of(text);
    }

    /** The shared bus, the shared database and the presence view every backend reads. */
    private static final class Network {

        final boolean offlineToMail;
        final Map<String, Backend> backends = new LinkedHashMap<>();
        final Map<UUID, OnlinePlayer> online = new LinkedHashMap<>();
        final Map<UUID, IgnoreList> ignores = new HashMap<>();
        final List<MailItem> mail = new ArrayList<>();
        final Set<UUID> muted = new HashSet<>();
        final Set<UUID> seeVanished = new HashSet<>();
        final Set<String> unreachable = new HashSet<>();
        final List<Runnable> heldAnswers = new ArrayList<>();
        boolean deliverTwice;
        boolean crossServerVanish;
        boolean holdAnswers;

        Network() {
            this(false);
        }

        Network(boolean offlineToMail) {
            this.offlineToMail = offlineToMail;
        }

        /** Deliver the answers held back so far, as a bus that was only slow finally does. */
        void releaseAnswers() {
            holdAnswers = false;
            List<Runnable> due = List.copyOf(heldAnswers);
            heldAnswers.clear();
            due.forEach(Runnable::run);
        }

        /** Whether any backend but {@code server} hides {@code player}. */
        boolean vanishedElsewhere(String server, UUID player) {
            return backends.values().stream()
                    .anyMatch(backend -> !backend.name.equals(server) && backend.vanishedHere.contains(player));
        }

        Backend backend(String name) {
            Backend backend = new Backend(this, name);
            backends.put(name, backend);
            return backend;
        }

        /** Every backend but {@code origin} and the unreachable ones, twice over when the bus duplicates frames. */
        List<Backend> peersOf(String origin) {
            List<Backend> peers = new ArrayList<>();
            for (Backend backend : backends.values()) {
                if (!backend.name.equals(origin) && !unreachable.contains(backend.name)) {
                    peers.add(backend);
                    if (deliverTwice) {
                        peers.add(backend);
                    }
                }
            }
            return peers;
        }
    }

    /** One server: its own players, switches, spies and staff, wired to the use cases under test. */
    private static final class Backend {

        final Network network;
        final String name;
        final Set<UUID> connected = new HashSet<>();
        final Set<UUID> toggledOff = new HashSet<>();
        final Set<UUID> repliesOff = new HashSet<>();
        final Set<UUID> vanishedHere = new HashSet<>();
        boolean steppedBack;
        final Map<UUID, String> away = new HashMap<>();
        final Set<PlayerRef> spies = new HashSet<>();
        final List<PlayerRef> staff = new ArrayList<>();
        final Map<UUID, LastConversation> conversations = new HashMap<>();
        final List<Runnable> waits = new ArrayList<>();
        final List<String> lines = new ArrayList<>();
        final RecordingEvents events = new RecordingEvents();
        final RelayPrivateMessage relay;
        final Reply reply;
        final HelpOp helpOp;
        final SendStaffChat staffChat;

        Backend(Network network, String name) {
            this.network = network;
            this.name = name;
            Notifier notifier = new Notifier(
                    (PlayerRef viewer, MessageKey key, Map<String, String> placeholders) ->
                            key.key() + (placeholders.isEmpty() ? "" : new TreeMap<>(placeholders).toString()),
                    (viewer, text) -> lines.add(viewer.name() + ": " + text));
            Delivery delivery = new Delivery();
            Scheduler scheduler = new BackendScheduler();
            NetworkPresence presence = new Presence();
            VanishVisibility vanish = new Vanish();
            ReplyRoutingStore replyRouting = new Switch(repliesOff);
            SendMessage sendMessage = new SendMessage(
                    delivery,
                    new SharedIgnores(),
                    new Conversations(),
                    new Switch(toggledOff),
                    new Spies(),
                    who -> network.muted.contains(who.uuid()),
                    (AfkStatus) who -> Optional.ofNullable(away.get(who.uuid())),
                    new Mail(),
                    network.offlineToMail,
                    notifier,
                    events,
                    CLOCK);
            Bus bus = new Bus();
            this.relay = new RelayPrivateMessage(
                    sendMessage, replyRouting, vanish, presence, bus, notifier, scheduler, () -> steppedBack);
            this.reply = new Reply(
                    sendMessage,
                    new Conversations(),
                    new Lookup(),
                    vanish,
                    replyRouting,
                    notifier,
                    Duration.ofMinutes(5),
                    CLOCK,
                    relay);
            StaffAudience audience = node -> List.copyOf(staff);
            this.helpOp =
                    new HelpOp(audience, delivery, who -> false, notifier, events, CLOCK, bus, presence, scheduler);
            this.staffChat = new SendStaffChat(new Channel(), events, bus, scheduler);
        }

        void join(PlayerRef player) {
            connected.add(player.uuid());
            network.online.put(player.uuid(), new OnlinePlayer(player, name));
        }

        void leave(PlayerRef player) {
            connected.remove(player.uuid());
            network.online.remove(player.uuid());
        }

        void spy(PlayerRef player) {
            join(player);
            spies.add(player);
        }

        void staff(PlayerRef player) {
            join(player);
            staff.add(player);
        }

        /** {@code /msg} to a player the test put on another backend. */
        void msg(PlayerRef sender, String target, String text) {
            OnlinePlayer elsewhere = relay.elsewhere(target).orElseThrow();
            relay.send(sender, elsewhere, body(text));
        }

        /** Let every wait for an answer run out. */
        void endWaits() {
            List<Runnable> due = List.copyOf(waits);
            waits.clear();
            due.forEach(Runnable::run);
        }

        List<String> lines() {
            return List.copyOf(lines);
        }

        Optional<PlayerRef> here(UUID player) {
            OnlinePlayer at = network.online.get(player);
            return at != null && connected.contains(player) ? Optional.of(at.player()) : Optional.empty();
        }

        /** The players connected to this backend. */
        private final class Lookup implements PlayerLookup {

            @Override
            public Optional<PlayerRef> findOnlineByName(String playerName) {
                return network.online.values().stream()
                        .map(OnlinePlayer::player)
                        .filter(player -> player.name().equals(playerName) && connected.contains(player.uuid()))
                        .findFirst();
            }

            @Override
            public Optional<PlayerRef> findByUuid(UUID uuid) {
                return here(uuid);
            }

            @Override
            public boolean isOnline(UUID uuid) {
                return here(uuid).isPresent();
            }
        }

        /** Runs work at once, and a player's work only while they are connected here, as Folia does. */
        private final class BackendScheduler implements Scheduler {

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
                onEntity(player, task, () -> {});
            }

            @Override
            public void onEntity(PlayerRef player, Runnable task, Runnable retired) {
                (connected.contains(player.uuid()) ? task : retired).run();
            }

            @Override
            public void async(Runnable task) {
                task.run();
            }

            @Override
            public void asyncAfter(Duration delay, Runnable task) {
                waits.add(task);
            }
        }

        /** The presence view every backend shares, seen from this one. */
        private final class Presence implements NetworkPresence {

            @Override
            public String localServer() {
                return name;
            }

            @Override
            public Optional<OnlinePlayer> find(UUID player) {
                return Optional.ofNullable(network.online.get(player));
            }

            @Override
            public Optional<OnlinePlayer> findByName(String playerName) {
                return network.online.values().stream()
                        .filter(at -> at.player().name().equalsIgnoreCase(playerName))
                        .findFirst();
            }

            @Override
            public List<OnlinePlayer> onServer(String server) {
                return network.online.values().stream()
                        .filter(at -> at.server().equals(server))
                        .toList();
            }

            @Override
            public List<OnlinePlayer> online() {
                return List.copyOf(network.online.values());
            }
        }

        /** The bus as this backend sees it: every frame reaches each reachable peer. */
        private final class Bus implements MessagingNetwork, StaffChatNetwork {

            @Override
            public void send(RemoteMessage message) {
                network.peersOf(name).forEach(peer -> peer.relay.receive(message));
            }

            @Override
            public void answer(RemoteAnswer answer) {
                Runnable deliver = () -> network.peersOf(name).forEach(peer -> peer.relay.receive(answer));
                if (network.holdAnswers) {
                    network.heldAnswers.add(deliver);
                } else {
                    deliver.run();
                }
            }

            @Override
            public void raise(RemoteHelpOp request) {
                network.peersOf(name).forEach(peer -> peer.helpOp.receive(request, name));
            }

            @Override
            public void publish(RemoteStaffChat chat) {
                network.peersOf(name).forEach(peer -> peer.staffChat.receive(chat, name));
            }
        }

        /**
         * Vanish with flat levels: a viewer sees vanished players or does not. This backend knows whom it hides; it
         * knows whom the others hide only while cross-server vanish is on.
         */
        private final class Vanish implements VanishVisibility {

            @Override
            public boolean isHiddenFrom(PlayerRef viewer, PlayerRef target) {
                if (seeLevel(viewer) > 0) {
                    return false;
                }
                if (connected.contains(target.uuid())) {
                    return vanishedHere.contains(target.uuid());
                }
                return network.crossServerVanish && network.vanishedElsewhere(name, target.uuid());
            }

            @Override
            public int seeLevel(PlayerRef viewer) {
                return network.seeVanished.contains(viewer.uuid()) ? 1 : 0;
            }

            @Override
            public boolean isHiddenHereFrom(PlayerRef viewer, int viewerSeeLevel, PlayerRef target) {
                return vanishedHere.contains(target.uuid()) && viewerSeeLevel < 1;
            }
        }

        /** The mail every backend writes to the shared database. */
        private final class Mail implements MailRepository {

            @Override
            public MailBox load(PlayerRef recipient) {
                return MailBox.empty(recipient);
            }

            @Override
            public long unreadCount(PlayerRef recipient) {
                return 0;
            }

            @Override
            public MailItem append(MailItem item) {
                network.mail.add(item);
                return item;
            }

            @Override
            public void markAllRead(PlayerRef recipient) {}

            @Override
            public void clear(PlayerRef recipient) {}

            @Override
            public int deleteSentBefore(Instant cutoff) {
                return 0;
            }
        }

        /** Writes each line as the player reading it would see its gist. */
        private final class Delivery implements MessageDelivery {

            @Override
            public void deliverMessage(PlayerRef sender, PlayerRef recipient, MessageBody body) {
                lines.add(recipient.name() + ": received from " + sender.name() + ": " + body.value());
            }

            @Override
            public void echoSent(PlayerRef sender, PlayerRef recipient, MessageBody body) {
                lines.add(sender.name() + ": sent to " + recipient.name() + ": " + body.value());
            }

            @Override
            public void notifyNewMail(PlayerRef recipient, MailItem item) {
                lines.add(recipient.name() + ": new mail");
            }

            @Override
            public void deliverMailLine(PlayerRef reader, MailItem item) {
                lines.add(reader.name() + ": mail " + item.body().value());
            }

            @Override
            public void deliverHelpOp(PlayerRef requester, PlayerRef viewer, MessageBody body) {
                lines.add(viewer.name() + ": helpop " + requester.name() + ": " + body.value());
            }

            @Override
            public void deliverRemoteHelpOp(PlayerRef requester, String server, PlayerRef viewer, MessageBody body) {
                lines.add(viewer.name() + ": helpop " + requester.name() + " on " + server + ": " + body.value());
            }

            @Override
            public void deliverSpy(PlayerRef observer, PlayerRef sender, PlayerRef recipient, MessageBody body) {
                lines.add(observer.name() + ": spy " + sender.name() + " -> " + recipient.name() + ": " + body.value());
            }
        }

        /** The staff channel of this backend. */
        private final class Channel implements StaffChannel {

            @Override
            public void send(PlayerRef from, List<PlayerRef> recipients, String message) {
                recipients.forEach(viewer -> lines.add(viewer.name() + ": staffchat " + from.name() + ": " + message));
            }

            @Override
            public void sendRemote(String server, PlayerRef from, List<PlayerRef> recipients, String message) {
                recipients.forEach(viewer ->
                        lines.add(viewer.name() + ": staffchat " + from.name() + " on " + server + ": " + message));
            }

            @Override
            public List<PlayerRef> onlineStaff() {
                return List.copyOf(staff);
            }
        }

        /** The spies of this backend; each watches every conversation. */
        private final class Spies implements SocialSpyStore {

            @Override
            public boolean isSpying(PlayerRef who) {
                return spies.contains(who);
            }

            @Override
            public boolean toggle(PlayerRef who) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean toggleTarget(PlayerRef spy, PlayerRef target) {
                throw new UnsupportedOperationException();
            }

            @Override
            public List<PlayerRef> activeSpies() {
                return List.copyOf(spies);
            }

            @Override
            public Set<PlayerRef> observersOf(PlayerRef sender, PlayerRef target) {
                return Set.copyOf(spies);
            }
        }

        /** The reply targets of this backend, shared by its send and reply paths. */
        private final class Conversations implements ConversationStore {

            @Override
            public void remember(PlayerRef owner, LastConversation conversation) {
                conversations.put(owner.uuid(), conversation);
            }

            @Override
            public Optional<LastConversation> latest(PlayerRef owner) {
                return Optional.ofNullable(conversations.get(owner.uuid()));
            }

            @Override
            public void forget(PlayerRef owner) {
                conversations.remove(owner.uuid());
            }
        }

        /** The ignore lists every backend reads from the shared database. */
        private final class SharedIgnores implements IgnoreStore {

            @Override
            public IgnoreList load(PlayerRef owner) {
                return network.ignores.getOrDefault(owner.uuid(), IgnoreList.empty(owner));
            }

            @Override
            public void ignore(PlayerRef owner, PlayerRef ignored, IgnoreScope scope) {
                network.ignores.put(owner.uuid(), load(owner).ignore(ignored, scope));
            }

            @Override
            public void unignore(PlayerRef owner, PlayerRef ignored) {
                network.ignores.put(owner.uuid(), load(owner).unignore(ignored));
            }
        }
    }

    /** A per-player off switch: message toggle or reply routing. */
    private record Switch(Set<UUID> off) implements MessageToggleStore, ReplyRoutingStore {

        @Override
        public boolean acceptsMessages(PlayerRef who) {
            return !off.contains(who.uuid());
        }

        @Override
        public boolean acceptsReplies(PlayerRef who) {
            return !off.contains(who.uuid());
        }

        @Override
        public boolean toggle(PlayerRef who) {
            throw new UnsupportedOperationException();
        }
    }
}
