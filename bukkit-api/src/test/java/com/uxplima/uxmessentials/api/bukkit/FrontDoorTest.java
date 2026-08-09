package com.uxplima.uxmessentials.api.bukkit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import com.uxplima.uxmessentials.api.bukkit.menu.MenuApi;
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
    }
}
