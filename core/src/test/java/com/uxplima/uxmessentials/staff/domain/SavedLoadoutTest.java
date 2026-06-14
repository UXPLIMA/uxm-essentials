package com.uxplima.uxmessentials.staff.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SavedLoadoutTest {

    private static SavedLoadout sample() {
        return new SavedLoadout(
                LoadoutBlob.of(new byte[] {1, 2}),
                LoadoutBlob.of(new byte[] {3}),
                LoadoutBlob.of(new byte[] {4}),
                0,
                10,
                0.25f,
                "CREATIVE",
                true,
                LoadoutBlob.of(new byte[] {5}));
    }

    @Test
    void blobDefensivelyCopiesTheBytesOnTheWayInAndOut() {
        byte[] source = {1, 2, 3};
        LoadoutBlob blob = LoadoutBlob.of(source);

        // Mutating the source array after construction must not change the stored value.
        source[0] = 99;
        assertThat(blob.bytes()[0]).isEqualTo((byte) 1);

        // The accessor returns a fresh copy, so mutating it must not change the stored value either.
        blob.bytes()[0] = 42;
        assertThat(blob.bytes()[0]).isEqualTo((byte) 1);
    }

    @Test
    void valueEqualityComparesTheBlobContents() {
        assertThat(sample()).isEqualTo(sample());
        assertThat(sample().hashCode()).isEqualTo(sample().hashCode());
        assertThat(LoadoutBlob.of(new byte[] {1, 2})).isEqualTo(LoadoutBlob.of(new byte[] {1, 2}));
    }

    @Test
    void rejectsAHeldSlotOutOfRange() {
        assertThatThrownBy(() -> new SavedLoadout(
                        LoadoutBlob.empty(),
                        LoadoutBlob.empty(),
                        LoadoutBlob.empty(),
                        9,
                        0,
                        0f,
                        "SURVIVAL",
                        false,
                        LoadoutBlob.empty()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SavedLoadout(
                        LoadoutBlob.empty(),
                        LoadoutBlob.empty(),
                        LoadoutBlob.empty(),
                        -1,
                        0,
                        0f,
                        "SURVIVAL",
                        false,
                        LoadoutBlob.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsABlankGameMode() {
        assertThatThrownBy(() -> new SavedLoadout(
                        LoadoutBlob.empty(),
                        LoadoutBlob.empty(),
                        LoadoutBlob.empty(),
                        0,
                        0,
                        0f,
                        "  ",
                        false,
                        LoadoutBlob.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
