package com.uxplima.uxmessentials.api.bukkit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.api.action.UxmActions;
import com.uxplima.uxmessentials.api.bukkit.menu.MenuApi;
import com.uxplima.uxmessentials.api.query.UxmDiscordLinkQuery;
import com.uxplima.uxmessentials.api.query.UxmEconomyQuery;
import com.uxplima.uxmessentials.api.query.UxmHologramsQuery;
import com.uxplima.uxmessentials.api.query.UxmHomesQuery;
import com.uxplima.uxmessentials.api.query.UxmInvRollbackQuery;
import com.uxplima.uxmessentials.api.query.UxmItemworldQuery;
import com.uxplima.uxmessentials.api.query.UxmKitsQuery;
import com.uxplima.uxmessentials.api.query.UxmMessagingQuery;
import com.uxplima.uxmessentials.api.query.UxmModerationQuery;
import com.uxplima.uxmessentials.api.query.UxmNpcQuery;
import com.uxplima.uxmessentials.api.query.UxmPlayerStateQuery;
import com.uxplima.uxmessentials.api.query.UxmPlayerWarpsQuery;
import com.uxplima.uxmessentials.api.query.UxmPlaytimeQuery;
import com.uxplima.uxmessentials.api.query.UxmPresenceQuery;
import com.uxplima.uxmessentials.api.query.UxmRanksQuery;
import com.uxplima.uxmessentials.api.query.UxmRegionsQuery;
import com.uxplima.uxmessentials.api.query.UxmSecurityQuery;
import com.uxplima.uxmessentials.api.query.UxmStaffQuery;
import com.uxplima.uxmessentials.api.query.UxmTeleportQuery;
import com.uxplima.uxmessentials.api.query.UxmTradeQuery;
import com.uxplima.uxmessentials.api.query.UxmVanishQuery;
import com.uxplima.uxmessentials.api.query.UxmVaultsQuery;
import com.uxplima.uxmessentials.api.query.UxmVoteQuery;
import com.uxplima.uxmessentials.api.query.UxmWarpsQuery;
import com.uxplima.uxmessentials.api.query.UxmWorldsQuery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The front door has to work for a consumer that enables before uxmEssentials and one that enables after it,
 * because a developer cannot control load order from their side. Both run the callback exactly once, and a callback
 * survives a disable so the next enable restores whatever it registered: a reload rebuilds the engine's registries,
 * and a consumer that had already run would otherwise be silently unregistered with no way to notice.
 */
class FrontDoorTest {

    @AfterEach
    void tearDown() {
        UxmApiHolder.uninstall();
        UxmApiHolder.forgetWaiting();
    }

    @Test
    void getIsNullBeforeInstall() {
        assertThat(UxmEssentialsApi.get()).isNull();
    }

    @Test
    void aCallbackRegisteredBeforeInstallRunsOnInstall() {
        List<UxmEssentialsApi> seen = new ArrayList<>();
        UxmEssentialsApi.whenReady(seen::add);
        assertThat(seen).isEmpty();

        UxmEssentialsApi api = new FakeApi();
        UxmApiHolder.install(api);

        assertThat(seen).containsExactly(api);
    }

    @Test
    void aCallbackRegisteredAfterInstallRunsImmediatelyAndOnlyOnce() {
        UxmEssentialsApi api = new FakeApi();
        UxmApiHolder.install(api);
        List<UxmEssentialsApi> seen = new ArrayList<>();

        UxmEssentialsApi.whenReady(seen::add);

        assertThat(seen).containsExactly(api);
    }

    @Test
    void aCallbackRunsAgainAfterAReloadSoItsRegistrationsAreRestored() {
        List<UxmEssentialsApi> seen = new ArrayList<>();
        UxmEssentialsApi.whenReady(seen::add);
        UxmEssentialsApi first = new FakeApi();
        UxmApiHolder.install(first);

        UxmApiHolder.uninstall();
        assertThat(UxmEssentialsApi.get()).isNull();
        UxmEssentialsApi second = new FakeApi();
        UxmApiHolder.install(second);

        assertThat(seen).containsExactly(first, second);
    }

    @Test
    void getAnswersTheInstalledApi() {
        UxmEssentialsApi api = new FakeApi();
        UxmApiHolder.install(api);

        assertThat(UxmEssentialsApi.get()).isSameAs(api);
        assertThat(api.version()).isEqualTo("test");
    }

    private static final class FakeApi implements UxmEssentialsApi {
        @Override
        public String version() {
            return "test";
        }

        @Override
        public boolean isModuleEnabled(String moduleId) {
            return true;
        }

        @Override
        public MenuApi menus() {
            throw new UnsupportedOperationException("the front-door test does not exercise the menu surface");
        }

        @Override
        public UxmActions actions(Plugin plugin) {
            throw new UnsupportedOperationException("this test never writes anything");
        }

        @Override
        public UxmActions actions(Plugin plugin, String actingFor) {
            throw new UnsupportedOperationException("this test never writes anything");
        }

        @Override
        public Optional<UxmHomesQuery> homes() {
            return Optional.empty();
        }

        @Override
        public Optional<UxmWarpsQuery> warps() {
            return Optional.empty();
        }

        @Override
        public Optional<UxmPlayerWarpsQuery> playerWarps() {
            return Optional.empty();
        }

        @Override
        public Optional<UxmEconomyQuery> economy() {
            return Optional.empty();
        }

        @Override
        public Optional<UxmKitsQuery> kits() {
            return Optional.empty();
        }

        @Override
        public Optional<UxmVaultsQuery> vaults() {
            return Optional.empty();
        }

        @Override
        public Optional<UxmModerationQuery> moderation() {
            return Optional.empty();
        }

        @Override
        public Optional<UxmPresenceQuery> presence() {
            return Optional.empty();
        }

        @Override
        public Optional<UxmVanishQuery> vanish() {
            return Optional.empty();
        }

        @Override
        public Optional<UxmPlaytimeQuery> playtime() {
            return Optional.empty();
        }

        @Override
        public Optional<UxmPlayerStateQuery> playerState() {
            return Optional.empty();
        }

        @Override
        public Optional<UxmWorldsQuery> worlds() {
            return Optional.empty();
        }

        @Override
        public Optional<UxmTeleportQuery> teleport() {
            return Optional.empty();
        }

        @Override
        public Optional<UxmRanksQuery> ranks() {
            return Optional.empty();
        }

        @Override
        public Optional<UxmSecurityQuery> security() {
            return Optional.empty();
        }

        @Override
        public Optional<UxmInvRollbackQuery> invRollback() {
            return Optional.empty();
        }

        @Override
        public Optional<UxmRegionsQuery> regions() {
            return Optional.empty();
        }

        @Override
        public Optional<UxmDiscordLinkQuery> discordLink() {
            return Optional.empty();
        }

        @Override
        public Optional<UxmNpcQuery> npc() {
            return Optional.empty();
        }

        @Override
        public Optional<UxmHologramsQuery> holograms() {
            return Optional.empty();
        }

        @Override
        public Optional<UxmStaffQuery> staff() {
            return Optional.empty();
        }

        @Override
        public Optional<UxmItemworldQuery> itemworld() {
            return Optional.empty();
        }

        @Override
        public Optional<com.uxplima.uxmessentials.api.query.UxmCommandControlQuery> commandControl() {
            return Optional.empty();
        }

        @Override
        public Optional<com.uxplima.uxmessentials.api.query.UxmScoreboardQuery> scoreboard() {
            return Optional.empty();
        }

        @Override
        public Optional<UxmTradeQuery> trade() {
            return Optional.empty();
        }

        @Override
        public Optional<UxmVoteQuery> vote() {
            return Optional.empty();
        }

        @Override
        public Optional<UxmMessagingQuery> messaging() {
            return Optional.empty();
        }
    }
}
