package com.uxplima.uxmessentials.commandcontrol.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * The pure channel-registration filter behind the plugin-channel hider: it drops every channel not on the allow-list
 * from the advertised channel list, keeps the allowed ones in order, is case-insensitive, and is a no-op when the hider
 * is switched off. This is the tested core the adapter's packet plumbing folds over the outbound register/unregister
 * payload.
 */
class ChannelHidePolicyTest {

    @Test
    void aNonAllowedChannelIsDroppedFromTheRegisterList() {
        ChannelHidePolicy policy = ChannelHidePolicy.of(true, List.of("minecraft:brand", "velocity:main"));

        List<String> advertised = List.of(
                "minecraft:brand", "worldedit:cui", "velocity:main", "litematica:cats", "fabric:screen_handler");

        // Only the two allowed channels survive; the fingerprinting mod channels are stripped.
        assertThat(policy.filter(advertised)).containsExactly("minecraft:brand", "velocity:main");
    }

    @Test
    void theFilterPreservesOrderAndIsCaseInsensitive() {
        ChannelHidePolicy policy = ChannelHidePolicy.of(true, List.of("Minecraft:Brand"));

        assertThat(policy.filter(List.of("minecraft:brand", "some:mod"))).containsExactly("minecraft:brand");
        assertThat(policy.allows("MINECRAFT:BRAND")).isTrue();
        assertThat(policy.allows("some:mod")).isFalse();
    }

    @Test
    void filteringEverythingYieldsAnEmptyList() {
        ChannelHidePolicy policy = ChannelHidePolicy.of(true, List.of("minecraft:brand"));

        assertThat(policy.filter(List.of("worldedit:cui", "some:mod"))).isEmpty();
    }

    @Test
    void aDisabledPolicyLeavesTheListUnchanged() {
        ChannelHidePolicy policy = ChannelHidePolicy.of(false, List.of("minecraft:brand"));

        List<String> advertised = List.of("minecraft:brand", "worldedit:cui");
        assertThat(policy.filter(advertised)).isEqualTo(advertised);
        assertThat(policy.isEnabled()).isFalse();
    }
}
