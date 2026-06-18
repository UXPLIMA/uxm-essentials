package com.uxplima.uxmessentials.shared.adapter.outbound.action;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SerializedItemsTest {

    @Test
    void recognisesTheSerializedPrefix() {
        assertThat(SerializedItems.isSerialized("b64:AAAA")).isTrue();
        assertThat(SerializedItems.isSerialized("DIAMOND")).isFalse();
        assertThat(SerializedItems.isSerialized("DIAMOND:3")).isFalse();
    }

    @Test
    void treatsANullTokenAsNotSerialized() {
        assertThat(SerializedItems.isSerialized(null)).isFalse();
    }

    @Test
    void decodeIsEmptyForAPlainMaterialToken() {
        assertThat(SerializedItems.decode("DIAMOND")).isEmpty();
    }

    @Test
    void decodeIsFailSoftOnCorruptBase64() {
        assertThat(SerializedItems.decode("b64:not-valid-base64!!!")).isEmpty();
    }
}
