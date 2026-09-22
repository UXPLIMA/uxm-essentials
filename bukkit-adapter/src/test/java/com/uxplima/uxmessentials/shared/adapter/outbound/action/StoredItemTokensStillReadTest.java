package com.uxplima.uxmessentials.shared.adapter.outbound.action;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmlib.item.SerializedItems;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * A token this plugin wrote before 2026-09-22 still reads.
 *
 * <p>This plugin had its own {@code b64:} codec and so does uxmLib, and the two did not write the same
 * bytes: the library stamps a {@code UXMI} header, a format version and the server's data version in
 * front of the payload, and this plugin wrote the bare payload. **The same prefix meant two different
 * formats**, which is the one thing that makes a codec unsafe to swap.
 *
 * <p>It is safe in this direction and only this one. The library's reader takes a header-less blob as
 * well as a headered one, so every click action, hologram and rank reward already stored on every server
 * keeps working; what it writes from now on carries the header. Going the other way would not have
 * worked: this plugin's reader handed {@code UXMI...} straight to the deserialiser, which fails, and the
 * item would have quietly disappeared.
 *
 * <p>So this test is not about the codec. It is the reason the port was allowed, kept where somebody
 * will find it: if the library ever stops reading a header-less blob, this fails, and what fails with it
 * is every payload written before today.
 */
class StoredItemTokensStillReadTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("a token written by this plugin's old codec decodes through the library's")
    void aheaderlessTokenStillDecodes() {
        ItemStack item = new ItemStack(Material.DIAMOND_SWORD);
        String oldToken = "b64:" + Base64.getEncoder().encodeToString(item.serializeAsBytes());

        assertThat(SerializedItems.isSerialized(oldToken)).isTrue();
        assertThat(SerializedItems.decode(oldToken))
                .describedAs("every click action, hologram and rank reward stored before today is one of these")
                .contains(item);
    }

    @Test
    @DisplayName("what it writes now carries the header, and reads back")
    void aheaderedTokenRoundTrips() {
        ItemStack item = new ItemStack(Material.GOLDEN_APPLE, 3);

        assertThat(SerializedItems.decode(SerializedItems.encode(item))).contains(item);
    }
}
