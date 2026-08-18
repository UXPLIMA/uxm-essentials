package com.uxplima.uxmessentials.skin.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * The rules that decide what a player may wear, kept pure so they can be read without a server.
 *
 * <p>Three of them are refusals an operator configures (a blocked name, a url from an unlisted host, a skin behind
 * its own permission node) and one is a choice (which pool entry an undressed player gets). The last is the
 * interesting one: it has to be stable, because a player who gets a different face on every join looks broken
 * rather than dressed.
 */
class SkinPolicyTest {

    private final SkinPolicy policy =
            new SkinPolicy(List.of("Herobrine"), List.of("i.imgur.com"), List.of("Alex", "Steve"));

    @Test
    void aBlockedSkinIsRefusedWhateverCaseItIsTypedIn() {
        assertThat(policy.blocked("herobrine")).isTrue();
        assertThat(policy.blocked("HEROBRINE")).isTrue();
        assertThat(policy.blocked("Notch")).isFalse();
    }

    @Test
    void onlyAnAllowedHostPassesTheUrlCheck() {
        assertThat(policy.urlAllowed("https://i.imgur.com/abc.png")).isTrue();
        assertThat(policy.urlAllowed("https://evil.invalid/abc.png")).isFalse();
    }

    @Test
    void aMalformedUrlIsRefusedRatherThanThrowing() {
        assertThat(policy.urlAllowed("not a url")).isFalse();
        assertThat(policy.urlAllowed("")).isFalse();
    }

    @Test
    void anEmptyAllowlistAllowsEveryHost() {
        SkinPolicy open = new SkinPolicy(List.of(), List.of(), List.of());

        assertThat(open.urlAllowed("https://anything.invalid/a.png")).isTrue();
    }

    @Test
    void aSkinNameMapsToItsOwnPermissionNode() {
        assertThat(policy.permissionFor("Notch")).isEqualTo("uxmessentials.skin.name.notch");
    }

    @Test
    void theFallbackIsTheSameFaceOnEveryJoin() {
        UUID player = UUID.randomUUID();

        assertThat(policy.fallbackFor(player)).isPresent().isEqualTo(policy.fallbackFor(player));
        assertThat(List.of("Alex", "Steve")).contains(policy.fallbackFor(player).orElseThrow());
    }

    @Test
    void anEmptyPoolMeansNoFallback() {
        SkinPolicy bare = new SkinPolicy(List.of(), List.of(), List.of());

        assertThat(bare.fallbackFor(UUID.randomUUID())).isEmpty();
    }
}
