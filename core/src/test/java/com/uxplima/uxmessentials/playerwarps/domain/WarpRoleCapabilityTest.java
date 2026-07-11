package com.uxplima.uxmessentials.playerwarps.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Pins the full role × capability matrix exactly, so a change to who-can-do-what is a deliberate edit here and can
 * never drift from {@link WarpRole#can}. Owner holds everything; a co-owner holds everything but delete, transfer,
 * and managing members; a manager holds only metadata, the whitelist, and bans.
 */
class WarpRoleCapabilityTest {

    private static final Map<WarpRole, Set<WarpCapability>> EXPECTED = expected();

    private static Map<WarpRole, Set<WarpCapability>> expected() {
        Map<WarpRole, Set<WarpCapability>> matrix = new EnumMap<>(WarpRole.class);
        matrix.put(WarpRole.OWNER, EnumSet.allOf(WarpCapability.class));
        matrix.put(
                WarpRole.CO_OWNER,
                EnumSet.complementOf(
                        EnumSet.of(WarpCapability.DELETE, WarpCapability.TRANSFER, WarpCapability.MANAGE_MEMBERS)));
        matrix.put(
                WarpRole.MANAGER,
                EnumSet.of(WarpCapability.EDIT_METADATA, WarpCapability.MANAGE_WHITELIST, WarpCapability.MANAGE_BANS));
        return matrix;
    }

    @ParameterizedTest
    @EnumSource(WarpRole.class)
    void everyRoleMatchesTheExpectedRowExactly(WarpRole role) {
        Set<WarpCapability> row = Objects.requireNonNull(EXPECTED.get(role), "row");
        for (WarpCapability capability : WarpCapability.values()) {
            boolean expected = row.contains(capability);
            assertThat(role.can(capability)).as("%s.can(%s)", role, capability).isEqualTo(expected);
        }
    }

    @Test
    void ownerHoldsEveryCapability() {
        for (WarpCapability capability : WarpCapability.values()) {
            assertThat(WarpRole.OWNER.can(capability)).isTrue();
        }
    }

    @Test
    void coOwnerLacksExactlyDeleteTransferAndManageMembers() {
        assertThat(WarpRole.CO_OWNER.can(WarpCapability.DELETE)).isFalse();
        assertThat(WarpRole.CO_OWNER.can(WarpCapability.TRANSFER)).isFalse();
        assertThat(WarpRole.CO_OWNER.can(WarpCapability.MANAGE_MEMBERS)).isFalse();
        assertThat(WarpRole.CO_OWNER.can(WarpCapability.WITHDRAW)).isTrue();
        assertThat(WarpRole.CO_OWNER.can(WarpCapability.EDIT_PRICE)).isTrue();
    }

    @Test
    void managerHoldsOnlyMetadataWhitelistAndBans() {
        assertThat(WarpRole.MANAGER.can(WarpCapability.EDIT_METADATA)).isTrue();
        assertThat(WarpRole.MANAGER.can(WarpCapability.MANAGE_WHITELIST)).isTrue();
        assertThat(WarpRole.MANAGER.can(WarpCapability.MANAGE_BANS)).isTrue();
        assertThat(WarpRole.MANAGER.can(WarpCapability.EDIT_ACCESS)).isFalse();
        assertThat(WarpRole.MANAGER.can(WarpCapability.EDIT_PRICE)).isFalse();
        assertThat(WarpRole.MANAGER.can(WarpCapability.MOVE)).isFalse();
        assertThat(WarpRole.MANAGER.can(WarpCapability.RENAME)).isFalse();
        assertThat(WarpRole.MANAGER.can(WarpCapability.TRANSFER)).isFalse();
        assertThat(WarpRole.MANAGER.can(WarpCapability.DELETE)).isFalse();
        assertThat(WarpRole.MANAGER.can(WarpCapability.MANAGE_MEMBERS)).isFalse();
        assertThat(WarpRole.MANAGER.can(WarpCapability.WITHDRAW)).isFalse();
    }
}
