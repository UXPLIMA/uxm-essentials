package com.uxplima.uxmessentials.rest.bridge;

import static org.assertj.core.api.Assertions.assertThat;

import com.uxplima.uxmessentials.api.bukkit.event.economy.UxmWalletCreditEvent;
import com.uxplima.uxmessentials.api.bukkit.event.home.UxmHomeCreateEvent;
import com.uxplima.uxmessentials.api.bukkit.event.playerwarp.UxmPlayerWarpCreateEvent;
import com.uxplima.uxmessentials.api.bukkit.event.pose.UxmPoseEvent;
import com.uxplima.uxmessentials.api.bukkit.event.teleport.UxmTeleportRequestSendEvent;
import org.junit.jupiter.api.Test;

class EventNamesTest {

    @Test
    void theContextIsThePackageAndTheRestIsTheClassName() {
        assertThat(EventNames.of(UxmWalletCreditEvent.class)).isEqualTo("economy.wallet-credit");
    }

    @Test
    void aContextRepeatedInTheClassNameIsNotSaidTwice() {
        assertThat(EventNames.of(UxmHomeCreateEvent.class)).isEqualTo("home.create");
        assertThat(EventNames.of(UxmPlayerWarpCreateEvent.class)).isEqualTo("playerwarp.create");
        assertThat(EventNames.of(UxmTeleportRequestSendEvent.class)).isEqualTo("teleport.request-send");
    }

    /** Dropping the context would leave nothing, so this one keeps it and reads a little oddly. */
    @Test
    void anEventNamedAfterNothingButItsContextKeepsIt() {
        assertThat(EventNames.of(UxmPoseEvent.class)).isEqualTo("pose.pose");
    }
}
