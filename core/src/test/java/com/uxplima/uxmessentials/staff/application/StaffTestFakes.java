package com.uxplima.uxmessentials.staff.application;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.staff.application.port.StaffChannel;
import com.uxplima.uxmessentials.staff.application.port.StaffLoadoutCapture;
import com.uxplima.uxmessentials.staff.application.port.StaffLoadoutRepository;
import com.uxplima.uxmessentials.staff.application.port.StaffModeStore;
import com.uxplima.uxmessentials.staff.application.port.StaffVanish;
import com.uxplima.uxmessentials.staff.domain.LoadoutBlob;
import com.uxplima.uxmessentials.staff.domain.SavedLoadout;

/**
 * In-memory fakes for every staff port, sharing a single ordered call log so the use-case tests can assert
 * the load-bearing call ORDER (capture → save → set-active → publish → apply-hotbar → vanish on enter;
 * restore → delete → clear on exit). The fakes are deliberately simple so the tests read as behaviour, not
 * mock bookkeeping.
 */
final class StaffTestFakes {

    /** The single ordered log every fake appends to, so cross-port ordering is observable. */
    final List<String> calls = new ArrayList<>();

    final RecordingStore store = new RecordingStore();
    final RecordingRepository repository = new RecordingRepository();
    final RecordingCapture capture = new RecordingCapture();
    final RecordingVanish vanish = new RecordingVanish();
    final RecordingEvents events = new RecordingEvents();

    static SavedLoadout sampleLoadout() {
        return new SavedLoadout(
                LoadoutBlob.of(new byte[] {1, 2, 3}),
                LoadoutBlob.of(new byte[] {4}),
                LoadoutBlob.of(new byte[] {5}),
                3,
                30,
                0.5f,
                "SURVIVAL",
                false,
                LoadoutBlob.of(new byte[] {6, 7}),
                false);
    }

    /** A {@link StaffNotifier} wired over collecting message ports, so tests can read what was sent. */
    StaffNotifier notifier() {
        return new StaffNotifier(new EchoMessages(), new CollectingSink(sentKeys));
    }

    final List<MessageKey> sentKeys = new ArrayList<>();

    final class RecordingStore implements StaffModeStore {
        final Map<UUID, String> active = new HashMap<>();

        @Override
        public boolean isActive(PlayerRef who) {
            return active.containsKey(who.uuid());
        }

        @Override
        public void setActive(PlayerRef who, String modeName) {
            calls.add("setActive");
            active.put(who.uuid(), modeName);
        }

        @Override
        public void clear(PlayerRef who) {
            calls.add("clear");
            active.remove(who.uuid());
        }
    }

    final class RecordingRepository implements StaffLoadoutRepository {
        final Map<UUID, SavedLoadout> rows = new HashMap<>();

        @Override
        public void save(UUID owner, SavedLoadout loadout) {
            calls.add("save");
            rows.put(owner, loadout);
        }

        @Override
        public Optional<SavedLoadout> load(UUID owner) {
            calls.add("load");
            return Optional.ofNullable(rows.get(owner));
        }

        @Override
        public void delete(UUID owner) {
            calls.add("delete");
            rows.remove(owner);
        }
    }

    final class RecordingCapture implements StaffLoadoutCapture {
        SavedLoadout toCapture = sampleLoadout();
        final List<SavedLoadout> restored = new ArrayList<>();
        // The restore-reach the offline-race tests flip false to assert exit/recovery keep the row.
        boolean restoreReachesPlayer = true;

        @Override
        public SavedLoadout capture(PlayerRef who) {
            calls.add("capture");
            return toCapture;
        }

        @Override
        public boolean restore(PlayerRef who, SavedLoadout loadout) {
            calls.add("restore");
            restored.add(loadout);
            return restoreReachesPlayer;
        }

        @Override
        public void applyGadgetHotbar(PlayerRef who, String modeName) {
            calls.add("applyGadgetHotbar");
        }
    }

    final class RecordingVanish implements StaffVanish {
        final List<Boolean> states = new ArrayList<>();
        // The pre-enter vanish state capture() reads; tests set this to assert it rides into the saved loadout.
        boolean vanished = false;

        @Override
        public void setVanished(PlayerRef who, boolean vanished) {
            calls.add("vanish:" + vanished);
            states.add(vanished);
        }

        @Override
        public boolean isVanished(PlayerRef who) {
            return vanished;
        }
    }

    final class RecordingEvents implements DomainEventPublisher {
        final List<DomainEvent> published = new ArrayList<>();

        @Override
        public void publish(DomainEvent event) {
            calls.add("publish:" + event.getClass().getSimpleName());
            published.add(event);
        }
    }

    /** A {@link StaffChannel} that records the fan-out and exposes a fixed online-staff audience. */
    static final class RecordingChannel implements StaffChannel {
        final List<PlayerRef> audience;
        final List<PlayerRef> sentTo = new ArrayList<>();
        String sentMessage = "";

        @org.jspecify.annotations.Nullable PlayerRef sentFrom;

        RecordingChannel(List<PlayerRef> audience) {
            this.audience = List.copyOf(audience);
        }

        @Override
        public void send(PlayerRef from, List<PlayerRef> recipients, String message) {
            this.sentFrom = from;
            this.sentMessage = message;
            this.sentTo.addAll(recipients);
        }

        @Override
        public List<PlayerRef> onlineStaff() {
            return audience;
        }
    }

    private static final class EchoMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    private static final class CollectingSink implements MessageSink {
        private final List<MessageKey> keys;

        CollectingSink(List<MessageKey> keys) {
            this.keys = keys;
        }

        @Override
        public void deliver(PlayerRef viewer, String renderedText) {
            // EchoMessages renders the catalog key, so map it back to a StaffMessageKey for assertions.
            for (StaffMessageKey key : StaffMessageKey.values()) {
                if (key.key().equals(renderedText)) {
                    keys.add(key);
                    return;
                }
            }
        }
    }
}
