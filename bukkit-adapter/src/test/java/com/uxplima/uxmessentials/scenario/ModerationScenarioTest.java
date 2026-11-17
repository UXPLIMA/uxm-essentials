package com.uxplima.uxmessentials.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.plugin.Plugin;

import io.papermc.paper.event.player.AsyncChatEvent;

import com.uxplima.uxmessentials.moderation.application.ModerationGuard;
import com.uxplima.uxmessentials.shared.application.permission.EnforcementPermissions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockbukkit.mockbukkit.MockBukkit;

/** A ban, a lockdown and a mute, seen from the player they are aimed at. */
// The login event is the one the plugin decides on, so the scenario watches the same one.
@SuppressWarnings("deprecation")
@Tag("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ModerationScenarioTest {

    private static final InetAddress BEDROCK_ADDRESS = InetAddress.ofLiteral("203.0.113.9");

    private RunningPlugin running;
    private ScenarioPlayer moderator;
    private final LoginResults logins = new LoginResults();
    private Plugin watcher;

    @BeforeAll
    void start() {
        running = RunningPlugin.start();
        watcher = MockBukkit.createMockPlugin("LoginWatch");
        running.server.getPluginManager().registerEvents(logins, watcher);
        moderator = running.join("Mona");
        moderator.setOp(true);
    }

    @AfterAll
    void stop() {
        running.close();
    }

    @Test
    void aBannedPlayerIsKickedAndTurnedAwayBeforeLoginWhenTheyComeBack() {
        ScenarioPlayer player = running.join("Bert");

        running.run(moderator, "ban Bert griefing");
        assertThat(player.isOnline()).as("kicked by the ban").isFalse();

        ScenarioPlayer again = running.join("Bert");

        assertThat(again.isOnline()).isFalse();
        assertThat(logins.preLogin.get(again.getUniqueId()))
                .as("the pre-login only reads the ban, off the tick")
                .isEqualTo(AsyncPlayerPreLoginEvent.Result.ALLOWED);
        assertThat(logins.login.get(again.getUniqueId()))
                .as("the login turns the player away on what the pre-login read")
                .isEqualTo(PlayerLoginEvent.Result.KICK_BANNED);
        assertThat(running.problems()).isEmpty();
    }

    @Test
    void anOfflineOperatorKeepsTheExemptionTheyHoldByDefaultAndCannotBeBanned() {
        ScenarioPlayer admin = running.join("Olaf");
        admin.setOp(true);
        running.quit(admin);

        running.run(moderator, "ban Olaf griefing");

        assertThat(running.join("Olaf").isOnline()).as("not banned").isTrue();
        assertThat(running.problems()).isEmpty();
    }

    @Test
    void anOfflinePlayerWithoutTheExemptionIsBannedAndTurnedAwayWhenTheyComeBack() {
        ScenarioPlayer player = running.join("Odin");
        running.quit(player);

        running.run(moderator, "ban Odin griefing");

        assertThat(running.join("Odin").isOnline()).as("banned while offline").isFalse();
        assertThat(running.problems()).isEmpty();
    }

    @Test
    void anAccountBannedByItsUuidBeforeItEverJoinedIsTurnedAwayOnItsFirstLogin() {
        UUID newcomer = UUID.nameUUIDFromBytes("OfflinePlayer:Newcomer".getBytes(StandardCharsets.UTF_8));

        running.run(moderator, "ban " + newcomer + " known griefer");

        ScenarioPlayer first = running.join("Newcomer");
        assertThat(first.isOnline()).as("banned before joining").isFalse();
        assertThat(logins.login.get(newcomer)).isEqualTo(PlayerLoginEvent.Result.KICK_BANNED);
        assertThat(running.problems()).isEmpty();
    }

    @Test
    void anIpBanByABedrockPlayersNameBansTheAddressTheyPlayFrom() {
        ScenarioPlayer bedrock = new ScenarioPlayer(running.server, ".SteveBE");
        bedrock.setAddress(new InetSocketAddress(BEDROCK_ADDRESS, 19132));
        running.server.addPlayer(bedrock);
        running.settle();
        try {
            running.run(moderator, "banip .SteveBE");

            assertThat(bedrock.isOnline()).as("kicked by the address ban").isFalse();
        } finally {
            running.run(moderator, "unbanip 203.0.113.9");
        }
        assertThat(running.problems()).isEmpty();
    }

    @Test
    void anIpBanOnTheAddressGeyserHandsEveryBedrockPlayerIsRefusedAndKicksNobody() {
        InetSocketAddress geyser = new InetSocketAddress(InetAddress.ofLiteral("127.0.0.1"), 25565);
        ScenarioPlayer first = new ScenarioPlayer(running.server, ".AlexBE");
        first.setAddress(geyser);
        ScenarioPlayer second = new ScenarioPlayer(running.server, ".SamBE");
        second.setAddress(geyser);
        running.server.addPlayer(first);
        running.server.addPlayer(second);
        running.settle();

        running.run(moderator, "banip .AlexBE");

        assertThat(first.isOnline()).as("the shared address is not banned").isTrue();
        assertThat(second.isOnline()).as("nor is anybody else behind it").isTrue();
        assertThat(running.problems()).isEmpty();
    }

    @Test
    void aLockdownTurnsAPlayerAwayAtLoginAndLetsAStaffMemberWithTheBypassIn() {
        running.run(moderator, "lockdown on");
        try {
            ScenarioPlayer player = running.join("Lars");
            ScenarioPlayer staff = new ScenarioPlayer(running.server, "Lena");
            staff.addAttachment(watcher, EnforcementPermissions.MODERATION_LOCKDOWN_BYPASS, true);
            running.server.addPlayer(staff);
            running.settle();

            assertThat(logins.preLogin.get(player.getUniqueId()))
                    .as("the pre-login only reads; the bypass is a permission, known once the player exists")
                    .isEqualTo(AsyncPlayerPreLoginEvent.Result.ALLOWED);
            assertThat(logins.login.get(player.getUniqueId())).isNotEqualTo(PlayerLoginEvent.Result.ALLOWED);
            assertThat(player.isOnline()).isFalse();
            assertThat(logins.login.get(staff.getUniqueId())).isEqualTo(PlayerLoginEvent.Result.ALLOWED);
            assertThat(staff.isOnline()).isTrue();
        } finally {
            running.run(moderator, "lockdown off");
        }
        assertThat(running.join("Liam").isOnline())
                .as("in again once the lockdown is off")
                .isTrue();
        assertThat(running.problems()).isEmpty();
    }

    @Test
    void aMutedPlayersChatLineIsStoppedAndTheyAreToldWhileOthersStillTalk() {
        ScenarioPlayer muted = running.join("Milo");
        ScenarioPlayer talker = running.join("Tara");
        running.run(moderator, "mute Milo 10m spam");
        RunningPlugin.said(muted);

        AsyncChatEvent fromMuted = running.chat(muted, "hello");
        AsyncChatEvent fromTalker = running.chat(talker, "hello");

        assertThat(fromMuted.isCancelled()).isTrue();
        assertThat(RunningPlugin.said(muted)).as("why the line went nowhere").hasSize(1);
        assertThat(fromTalker.isCancelled()).isFalse();
        assertThat(running.problems()).isEmpty();
    }

    @Test
    void aMutedPlayerWhoIsExemptStillTalks() {
        ScenarioPlayer exempt = running.join("Ezra");
        running.run(moderator, "mute Ezra 10m spam");
        assertThat(running.chat(exempt, "first").isCancelled()).as("muted").isTrue();
        // Granted after the mute, which a moderator could not place on an exempt player.
        exempt.addAttachment(watcher, ModerationGuard.EXEMPT_NODE, true);
        RunningPlugin.said(exempt);

        AsyncChatEvent line = running.chat(exempt, "hello");

        assertThat(line.isCancelled()).isFalse();
        assertThat(RunningPlugin.said(exempt))
                .as("nothing tells them the line was stopped")
                .isEmpty();
        assertThat(running.problems()).isEmpty();
    }

    /** What the server decided for each login, after every plugin had its say. */
    static final class LoginResults implements Listener {

        final Map<UUID, AsyncPlayerPreLoginEvent.Result> preLogin = new ConcurrentHashMap<>();
        final Map<UUID, PlayerLoginEvent.Result> login = new ConcurrentHashMap<>();

        @EventHandler(priority = EventPriority.MONITOR)
        public void onPreLogin(AsyncPlayerPreLoginEvent event) {
            preLogin.put(event.getUniqueId(), event.getLoginResult());
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void onLogin(PlayerLoginEvent event) {
            login.put(event.getPlayer().getUniqueId(), event.getResult());
        }
    }
}
