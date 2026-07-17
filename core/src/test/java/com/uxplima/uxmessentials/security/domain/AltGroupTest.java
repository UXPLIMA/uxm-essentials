package com.uxplima.uxmessentials.security.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * The pure alt-grouping rule: accounts that ever shared an IP token are grouped as alts, the target is never its
 * own alt, and accounts on unrelated tokens are left out. A token the target never used contributes nothing.
 */
class AltGroupTest {

    private static final UUID TARGET = UUID.randomUUID();
    private static final UUID ALT = UUID.randomUUID();
    private static final UUID FAR = UUID.randomUUID();

    @Test
    void groupsAccountsSharingAnIpToken() {
        List<IpAssociation> associations = List.of(
                new IpAssociation(TARGET, "ip-a"), new IpAssociation(ALT, "ip-a"), new IpAssociation(FAR, "ip-z"));

        AltGroup group = AltGroup.of(TARGET, associations);

        assertThat(group.alts()).containsExactly(ALT);
    }

    @Test
    void groupsAcrossAnyOfTheTargetsTokens() {
        UUID secondAlt = UUID.randomUUID();
        List<IpAssociation> associations = List.of(
                new IpAssociation(TARGET, "ip-a"),
                new IpAssociation(TARGET, "ip-b"),
                new IpAssociation(ALT, "ip-a"),
                new IpAssociation(secondAlt, "ip-b"));

        AltGroup group = AltGroup.of(TARGET, associations);

        assertThat(group.alts()).containsExactlyInAnyOrder(ALT, secondAlt);
    }

    @Test
    void theTargetIsNeverItsOwnAlt() {
        List<IpAssociation> associations =
                List.of(new IpAssociation(TARGET, "ip-a"), new IpAssociation(TARGET, "ip-a"));

        assertThat(AltGroup.of(TARGET, associations).alts()).isEmpty();
    }

    @Test
    void anAccountWithNoSharedIpHasNoAlts() {
        List<IpAssociation> associations = List.of(new IpAssociation(TARGET, "ip-a"), new IpAssociation(FAR, "ip-z"));

        AltGroup group = AltGroup.of(TARGET, associations);

        assertThat(group.isEmpty()).isTrue();
    }
}
