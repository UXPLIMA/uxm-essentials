package com.uxplima.uxmessentials.regions.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.Test;

/**
 * The pure classification of a roster identifier into a {@link RosterMember}: a uuid string is a removable player
 * whose removal builds the right {@link RegionMemberChange}; a {@code g:}-prefixed entry is a read-only group; and a
 * plain name is a legacy entry the uuid-keyed port cannot remove. No Bukkit or WorldGuard type is involved.
 */
class RosterMemberTest {

    private static final RegionRef REGION = new RegionRef(new WorldRef(UUID.randomUUID(), "world"), "spawn");

    @Test
    void aUuidIdentifierIsARemovablePlayerWhoseRemovalIsTheMatchingChange() {
        UUID uuid = UUID.randomUUID();
        RosterMember member = RosterMember.classify(uuid.toString(), RegionMemberChange.Role.MEMBER);

        assertThat(member.removable()).isTrue();
        assertThat(member.group()).isFalse();
        assertThat(member.player()).isEqualTo(uuid);
        assertThat(member.removalFrom(REGION))
                .isEqualTo(new RegionMemberChange(
                        REGION, uuid, RegionMemberChange.Role.MEMBER, RegionMemberChange.Action.REMOVE));
    }

    @Test
    void aGroupIdentifierIsAReadOnlyGroupWithItsPrefixStrippedForDisplay() {
        RosterMember member = RosterMember.classify("g:builders", RegionMemberChange.Role.OWNER);

        assertThat(member.group()).isTrue();
        assertThat(member.removable()).isFalse();
        assertThat(member.groupName()).isEqualTo("builders");
        assertThatThrownBy(() -> member.removalFrom(REGION)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aPlainNameIsALegacyEntryThatCannotBeRemovedByUuid() {
        RosterMember member = RosterMember.classify("Notch", RegionMemberChange.Role.MEMBER);

        assertThat(member.group()).isFalse();
        assertThat(member.removable()).isFalse();
        assertThat(member.player()).isNull();
        assertThatThrownBy(() -> member.removalFrom(REGION)).isInstanceOf(IllegalStateException.class);
    }
}
