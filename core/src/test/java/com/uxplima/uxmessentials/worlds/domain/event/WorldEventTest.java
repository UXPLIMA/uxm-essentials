package com.uxplima.uxmessentials.worlds.domain.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import org.junit.jupiter.api.Test;

class WorldEventTest {

    @Test
    void eventsAreDomainEventsCarryingTheWorldName() {
        WorldName name = WorldName.of("creative");
        WorldEvent created = new WorldCreated(name);
        WorldEvent deleted = new WorldDeleted(name);
        assertThat(created).isInstanceOf(DomainEvent.class);
        assertThat(created.name()).isEqualTo(name);
        assertThat(deleted.name()).isEqualTo(name);
    }
}
