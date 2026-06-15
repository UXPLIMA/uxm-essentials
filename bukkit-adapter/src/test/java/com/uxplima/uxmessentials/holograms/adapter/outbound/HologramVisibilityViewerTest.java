package com.uxplima.uxmessentials.holograms.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.holograms.domain.Visibility;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * Pins {@link HologramRenderer#maySee} — the per-player decision the renderer's allowed-viewer set is built
 * from. An {@code ALL} hologram is visible to everyone; a {@code PERMISSION} hologram is visible only to the
 * players the {@link Permissions} port says hold its node, so the restricted viewer set is exactly the
 * node-holders among the candidates.
 */
class HologramVisibilityViewerTest {

    private static final PlayerRef VIP = new PlayerRef(UUID.randomUUID(), "Vip");
    private static final PlayerRef PLAIN = new PlayerRef(UUID.randomUUID(), "Plain");
    private static final String NODE = "uxmessentials.hologram.see.vip";

    @Test
    void everyoneSeesAnAllHologram() {
        Permissions noOne = grantingTo(null);

        assertThat(HologramRenderer.maySee(noOne, Visibility.everyone(), VIP)).isTrue();
        assertThat(HologramRenderer.maySee(noOne, Visibility.everyone(), PLAIN)).isTrue();
    }

    @Test
    void onlyNodeHoldersSeeAPermissionHologram() {
        Permissions onlyVip = grantingTo(VIP);
        Visibility gated = Visibility.restrictedTo(NODE);

        assertThat(HologramRenderer.maySee(onlyVip, gated, VIP)).isTrue();
        assertThat(HologramRenderer.maySee(onlyVip, gated, PLAIN)).isFalse();
    }

    @Test
    void theRestrictedViewerSetIsExactlyTheNodeHolders() {
        Permissions onlyVip = grantingTo(VIP);
        Visibility gated = Visibility.restrictedTo(NODE);

        List<PlayerRef> visible = List.of(VIP, PLAIN).stream()
                .filter(who -> HologramRenderer.maySee(onlyVip, gated, who))
                .toList();

        assertThat(visible).containsExactly(VIP);
    }

    /** A {@link Permissions} fake that grants exactly the matching node to {@code holder} (everyone else: false). */
    private static Permissions grantingTo(@Nullable PlayerRef holder) {
        return new Permissions() {
            @Override
            public boolean has(PlayerRef who, String node) {
                return holder != null && who.uuid().equals(holder.uuid()) && node.equals(NODE);
            }

            @Override
            public QuotaResult resolveQuota(
                    PlayerRef who, QuotaFamily family, @Nullable WorldRef world, long configDefault) {
                return QuotaResult.limited(configDefault);
            }
        };
    }
}
