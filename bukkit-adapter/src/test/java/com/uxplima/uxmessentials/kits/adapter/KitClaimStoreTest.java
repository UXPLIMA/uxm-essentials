package com.uxplima.uxmessentials.kits.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.kits.adapter.outbound.BukkitKitGranter;
import com.uxplima.uxmessentials.kits.adapter.outbound.KitItemCodec;
import com.uxplima.uxmessentials.kits.adapter.outbound.PdcKitClaims;
import com.uxplima.uxmessentials.kits.application.port.KitGranter;
import com.uxplima.uxmessentials.kits.domain.KitId;
import com.uxplima.uxmessentials.kits.domain.KitItem;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of the kits outbound adapters against a real (mock) Bukkit server: the
 * {@link PdcKitClaims} one-time-claim store (the PDC stamp survives across reads, a per-kit reset clears one
 * stamp, {@code resetAll} clears every stamp), the {@link KitItemCodec} round-trip (an {@code ItemStack}
 * serialises and deserialises back with its amount), and the {@link BukkitKitGranter} delivering a kit into a
 * player's inventory. These are the real adapters the {@code /kit} and {@code /kitreset} commands drive.
 */
class KitClaimStoreTest {

    private ServerMock server;
    private Plugin plugin;
    private PdcKitClaims claims;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("uxmEssentials");
        server.addSimpleWorld("world");
        claims = new PdcKitClaims(plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aOneTimeStampSurvivesAcrossReads() {
        PlayerRef alice = ref("Alice");

        assertThat(claims.hasClaimed(alice, KitId.of("vote"))).isFalse();
        claims.markClaimed(alice, KitId.of("vote"));

        assertThat(claims.hasClaimed(alice, KitId.of("vote"))).isTrue();
    }

    @Test
    void aPerKitResetClearsOnlyThatStamp() {
        PlayerRef alice = ref("Alice");
        claims.markClaimed(alice, KitId.of("vote"));
        claims.markClaimed(alice, KitId.of("daily"));

        claims.reset(alice, KitId.of("vote"));

        assertThat(claims.hasClaimed(alice, KitId.of("vote"))).isFalse();
        assertThat(claims.hasClaimed(alice, KitId.of("daily"))).isTrue();
    }

    @Test
    void resetAllClearsEveryStamp() {
        PlayerRef alice = ref("Alice");
        claims.markClaimed(alice, KitId.of("vote"));
        claims.markClaimed(alice, KitId.of("daily"));

        claims.resetAll(alice);

        assertThat(claims.hasClaimed(alice, KitId.of("vote"))).isFalse();
        assertThat(claims.hasClaimed(alice, KitId.of("daily"))).isFalse();
    }

    @Test
    void anOfflinePlayerHasNoClaimAndMarkingNoOps() {
        PlayerRef ghost = new PlayerRef(java.util.UUID.randomUUID(), "Ghost");

        assertThat(claims.hasClaimed(ghost, KitId.of("vote"))).isFalse();
        claims.markClaimed(ghost, KitId.of("vote")); // no PDC to write — silently no-ops
        assertThat(claims.hasClaimed(ghost, KitId.of("vote"))).isFalse();
    }

    @Test
    void anItemRoundTripsThroughTheCodecWithItsAmount() {
        KitItem encoded = KitItemCodec.encode(new ItemStack(Material.DIAMOND, 16));

        ItemStack decoded = KitItemCodec.decode(encoded);

        assertThat(decoded.getType()).isEqualTo(Material.DIAMOND);
        assertThat(decoded.getAmount()).isEqualTo(16);
        assertThat(encoded.amount()).isEqualTo(16);
    }

    @Test
    void grantingAKitFillsThePlayersInventory() {
        PlayerMock alice = server.addPlayer("Alice");
        KitGranter granter = new BukkitKitGranter(new NoopLogger());
        List<KitItem> items = List.of(KitItemCodec.encode(new ItemStack(Material.IRON_INGOT, 5)));

        KitGranter.Grant grant = granter.grant(BukkitRefs.toRef(alice), items);

        assertThat(grant.fitInInventory()).isTrue();
        assertThat(alice.getInventory().contains(Material.IRON_INGOT, 5)).isTrue();
    }

    private PlayerRef ref(String name) {
        PlayerMock player = server.addPlayer(name);
        return BukkitRefs.toRef(player);
    }

    /** A logger that discards every line. */
    private static final class NoopLogger implements Logger {
        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }
}
