package com.uxplima.uxmessentials.worlds.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class WorldNameTest {

    @Test
    void acceptsAValidFolderName() {
        assertThat(WorldName.of("world_nether").value()).isEqualTo("world_nether");
        assertThat(WorldName.of("Creative-1").value()).isEqualTo("Creative-1");
    }

    @Test
    void rejectsPathTraversalAndSeparators() {
        for (String bad : new String[] {"..", ".", "a/b", "a\\b", "a:b", "../x", "world/"}) {
            assertThatThrownBy(() -> WorldName.of(bad)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void rejectsBlankNullAndOverlong() {
        assertThatThrownBy(() -> WorldName.of(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WorldName.of("a".repeat(65))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void equalityIsByValue() {
        assertThat(WorldName.of("world")).isEqualTo(WorldName.of("world"));
    }
}
