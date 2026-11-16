package com.uxplima.uxmessentials.kits.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.kits.application.port.KitRepository;
import com.uxplima.uxmessentials.kits.domain.KitDefinition;
import com.uxplima.uxmessentials.kits.domain.KitError;
import com.uxplima.uxmessentials.kits.domain.KitId;
import com.uxplima.uxmessentials.kits.domain.KitItem;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.testing.RecordingNotifier;
import org.junit.jupiter.api.Test;

/** Finding a kit to edit confirms nothing; the editor is confirmed once its window shows. */
class KitEditorTest {

    private static final PlayerRef ANN = new PlayerRef(UUID.fromString("00000000-0000-0000-0000-00000000000a"), "Ann");
    private static final KitId STARTER = KitId.of("starter");

    private final RecordingNotifier notifier = new RecordingNotifier();
    private final Kits kits = new Kits();
    private final KitEditor editor = new KitEditor(kits, notifier.notifier());

    @Test
    void findingAKitToEditTellsNothingUntilTheWindowShows() {
        kits.save(KitDefinition.builder()
                .id(STARTER)
                .items(List.of(KitItem.of("STONE_SWORD", 1)))
                .build());

        Optional<KitDefinition> found = editor.open(ANN, STARTER).asValue();
        assertThat(found).isPresent();
        assertThat(notifier.keys()).isEmpty();

        editor.opened(ANN, STARTER);

        assertThat(notifier.keys()).containsExactly("kit.edit.opened");
    }

    @Test
    void anUnknownKitIsToldAndNotOpened() {
        assertThat(editor.open(ANN, STARTER).asError()).contains(KitError.NOT_FOUND);
        assertThat(notifier.keys()).doesNotContain("kit.edit.opened");
    }

    private static final class Kits implements KitRepository {

        private final Map<KitId, KitDefinition> byId = new HashMap<>();

        @Override
        public Optional<KitDefinition> find(KitId id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public List<KitDefinition> all() {
            return List.copyOf(byId.values());
        }

        @Override
        public boolean exists(KitId id) {
            return byId.containsKey(id);
        }

        @Override
        public void save(KitDefinition definition) {
            byId.put(definition.id(), definition);
        }

        @Override
        public void delete(KitId id) {
            byId.remove(id);
        }
    }
}
