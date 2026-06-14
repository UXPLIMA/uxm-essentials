package com.uxplima.uxmessentials.moderation.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import com.uxplima.uxmessentials.moderation.application.SanctionDurationLimit;
import com.uxplima.uxmessentials.shared.adapter.outbound.permission.BukkitPermissions;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of the duration-tier permission node ({@code uxmessentials.moderation.ban.maxduration
 * .<seconds>}) read by {@link SanctionDurationLimit} over the real {@link BukkitPermissions}. A staff member
 * holding {@code maxduration.3600} has a requested one-day ban reduced to one hour; a staff member with no node
 * is unlimited and keeps the full span.
 */
class SanctionDurationCapTest {

    private ServerMock server;
    private Permissions permissions;
    private SanctionDurationLimit limit;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        permissions = new BukkitPermissions();
        limit = new SanctionDurationLimit(permissions);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aMaxdurationNodeCapsARequestedSpan() {
        PlayerMock staff = server.addPlayer("Staff");
        staff.addAttachment(MockBukkit.createMockPlugin(), "uxmessentials.moderation.ban.maxduration.3600", true);
        PlayerRef actor = new PlayerRef(staff.getUniqueId(), staff.getName());

        Duration capped = limit.cap(actor, "ban", Duration.ofDays(1));

        assertThat(capped).isEqualTo(Duration.ofHours(1));
    }

    @Test
    void aRequestWithinTheCapPassesThrough() {
        PlayerMock staff = server.addPlayer("Staff");
        staff.addAttachment(MockBukkit.createMockPlugin(), "uxmessentials.moderation.ban.maxduration.3600", true);
        PlayerRef actor = new PlayerRef(staff.getUniqueId(), staff.getName());

        Duration within = limit.cap(actor, "ban", Duration.ofMinutes(30));

        assertThat(within).isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    void anActorWithNoNodeIsUnlimited() {
        PlayerMock staff = server.addPlayer("Staff");
        PlayerRef actor = new PlayerRef(staff.getUniqueId(), staff.getName());

        Duration uncapped = limit.cap(actor, "ban", Duration.ofDays(30));

        assertThat(uncapped).isEqualTo(Duration.ofDays(30));
    }

    @Test
    void theHighestNodeWins() {
        PlayerMock staff = server.addPlayer("Staff");
        staff.addAttachment(MockBukkit.createMockPlugin(), "uxmessentials.moderation.ban.maxduration.3600", true);
        staff.addAttachment(MockBukkit.createMockPlugin(), "uxmessentials.moderation.ban.maxduration.86400", true);
        PlayerRef actor = new PlayerRef(staff.getUniqueId(), staff.getName());

        Duration capped = limit.cap(actor, "ban", Duration.ofDays(7));

        assertThat(capped).isEqualTo(Duration.ofDays(1));
    }
}
