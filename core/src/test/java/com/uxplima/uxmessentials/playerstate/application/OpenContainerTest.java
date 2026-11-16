package com.uxplima.uxmessentials.playerstate.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.playerstate.application.port.InventoryViewer;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.testing.RecordingNotifier;
import org.junit.jupiter.api.Test;

/**
 * The staff member is told whose storage they are looking at only once the window is in front of them. A window
 * that another plugin, or a save still under way, refuses is never confirmed.
 */
class OpenContainerTest {

    private static final PlayerRef ANN = new PlayerRef(UUID.fromString("00000000-0000-0000-0000-00000000000a"), "Ann");
    private static final PlayerRef SAM = new PlayerRef(UUID.fromString("00000000-0000-0000-0000-000000000005"), "Sam");

    private final RecordingNotifier notifier = new RecordingNotifier();
    private final Screens screens = new Screens();
    private final OpenContainer open = new OpenContainer(screens, notifier.notifier());

    @Test
    void anInventoryIsConfirmedOnceItsWindowShows() {
        open.openInventory(ANN, SAM);
        assertThat(notifier.keys()).as("the window has not appeared yet").isEmpty();

        screens.appear();

        assertThat(notifier.sent()).singleElement().satisfies(sent -> {
            assertThat(sent.viewer()).isEqualTo(ANN);
            assertThat(sent.key()).isEqualTo("playerstate.invsee.opened");
            assertThat(sent.placeholders()).containsEntry("player", "Sam");
        });
    }

    @Test
    void anEnderChestIsConfirmedOnceItsWindowShows() {
        open.openEnderChest(ANN, SAM);

        screens.appear();

        assertThat(notifier.keys()).containsExactly("playerstate.endersee.opened");
    }

    @Test
    void aRefusedWindowIsNotConfirmed() {
        open.openInventory(ANN, SAM);
        open.openEnderChest(ANN, SAM);

        screens.refuse();

        assertThat(notifier.keys()).isEmpty();
    }

    /** A screen that shows or refuses a window only when the test says so, as the server does a tick later. */
    private static final class Screens implements InventoryViewer {

        private final List<Runnable> asked = new ArrayList<>();

        @Override
        public void viewInventory(PlayerRef viewer, PlayerRef subject, Runnable shown) {
            asked.add(shown);
        }

        @Override
        public void viewEnderChest(PlayerRef viewer, PlayerRef subject, Runnable shown) {
            asked.add(shown);
        }

        void appear() {
            asked.forEach(Runnable::run);
            asked.clear();
        }

        void refuse() {
            asked.clear();
        }
    }
}
