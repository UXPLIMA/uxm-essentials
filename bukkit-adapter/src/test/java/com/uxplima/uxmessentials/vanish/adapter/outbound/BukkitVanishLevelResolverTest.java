package com.uxplima.uxmessentials.vanish.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.vanish.domain.VanishLevel;
import com.uxplima.uxmessentials.vanish.domain.VanishLevels;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The resolver maps PremiumVanish-style permission nodes to see/use levels: the plain {@code .use}/{@code .see} node is
 * level 1, a numbered {@code .level<N>} node is level N (highest wins), no see node at all is see level 0, and op / a
 * wildcard resolves to the high default that clears every realistic level.
 */
class BukkitVanishLevelResolverTest {

    private ServerMock server;
    private final BukkitVanishLevelResolver resolver = new BukkitVanishLevelResolver();

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aPlayerWithNoSeeNodeHasSeeLevelZero() {
        PlayerMock p = server.addPlayer("Nobody");

        assertThat(resolver.seeLevel(BukkitRefs.toRef(p))).isEqualTo(VanishLevels.NO_SEE_LEVEL);
    }

    @Test
    void thePlainSeeNodeIsSeeLevelOne() {
        PlayerMock p = server.addPlayer("Plain");
        p.addAttachment(MockBukkit.createMockPlugin(), "uxmessentials.vanish.see", true);

        assertThat(resolver.seeLevel(BukkitRefs.toRef(p))).isEqualTo(1);
    }

    @Test
    void aNumberedSeeNodeResolvesToItsLevelAndHighestWins() {
        PlayerMock p = server.addPlayer("Layered");
        p.addAttachment(MockBukkit.createMockPlugin(), "uxmessentials.vanish.see.level2", true);
        p.addAttachment(MockBukkit.createMockPlugin(), "uxmessentials.vanish.see.level5", true);

        assertThat(resolver.seeLevel(BukkitRefs.toRef(p))).isEqualTo(5);
    }

    @Test
    void aPlayerWithNoUseNodeStillVanishesAtLevelOne() {
        PlayerMock p = server.addPlayer("Bare");

        assertThat(resolver.useLevel(BukkitRefs.toRef(p))).isEqualTo(VanishLevel.DEFAULT);
    }

    @Test
    void aNumberedUseNodeResolvesToItsLevel() {
        PlayerMock p = server.addPlayer("Deep");
        p.addAttachment(MockBukkit.createMockPlugin(), "uxmessentials.vanish.use.level3", true);

        assertThat(resolver.useLevel(BukkitRefs.toRef(p))).isEqualTo(VanishLevel.of(3));
    }

    @Test
    void anOpResolvesToAHighDefaultForBothSeeAndUse() {
        PlayerMock op = server.addPlayer("Admin");
        op.setOp(true);

        // Op clears every realistic use level (sees everyone) and vanishes above every non-op viewer.
        assertThat(resolver.seeLevel(BukkitRefs.toRef(op))).isGreaterThan(1000);
        assertThat(resolver.useLevel(BukkitRefs.toRef(op)).level()).isGreaterThan(1000);
    }
}
