package com.uxplima.uxmessentials.migration.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.economy.application.port.WalletRepository;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.homes.application.port.HomeRepository;
import com.uxplima.uxmessentials.kits.adapter.outbound.KitItemCodec;
import com.uxplima.uxmessentials.kits.application.port.KitRepository;
import com.uxplima.uxmessentials.kits.domain.KitDefinition;
import com.uxplima.uxmessentials.kits.domain.KitId;
import com.uxplima.uxmessentials.kits.domain.KitItem;
import com.uxplima.uxmessentials.migration.BalancePolicy;
import com.uxplima.uxmessentials.migration.ConflictPolicy;
import com.uxplima.uxmessentials.migration.ImportMode;
import com.uxplima.uxmessentials.migration.ImportOptions;
import com.uxplima.uxmessentials.migration.ImportRecord;
import com.uxplima.uxmessentials.migration.RecordOutcome;
import com.uxplima.uxmessentials.migration.RecordWriter;
import com.uxplima.uxmessentials.migration.convert.map.ImportedKit;
import com.uxplima.uxmessentials.moderation.application.port.ModerationRepository;
import com.uxplima.uxmessentials.warps.application.port.WarpRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class RepositoryRecordWriterKitTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void writesAKitWithItemsConvertedToTheKitFormat() {
        FakeKitRepository kits = new FakeKitRepository();
        RecordWriter writer = writer(kits);
        KitDefinition raw = KitDefinition.repeatable(
                KitId.of("starter"), List.of(KitItem.of("diamond_sword 1 sharpness:5", 1)), Duration.ZERO);

        RecordOutcome outcome =
                writer.write(new ImportRecord.KitRecord(new ImportedKit(raw)), options(ConflictPolicy.OVERWRITE));

        assertThat(outcome).isEqualTo(RecordOutcome.WRITTEN);
        KitDefinition saved = kits.find(KitId.of("starter")).orElseThrow();
        assertThat(saved.items()).hasSize(1);
        ItemStack decoded = KitItemCodec.decode(saved.items().get(0));
        assertThat(decoded.getType()).isEqualTo(Material.DIAMOND_SWORD);
        assertThat(decoded.getEnchantmentLevel(Enchantment.SHARPNESS)).isEqualTo(5);
    }

    @Test
    void skipsAnExistingKitUnderTheSkipPolicy() {
        FakeKitRepository kits = new FakeKitRepository();
        kits.save(KitDefinition.repeatable(KitId.of("starter"), List.of(), Duration.ZERO));
        RecordWriter writer = writer(kits);
        KitDefinition raw =
                KitDefinition.repeatable(KitId.of("starter"), List.of(KitItem.of("stone 1", 1)), Duration.ZERO);

        RecordOutcome outcome =
                writer.write(new ImportRecord.KitRecord(new ImportedKit(raw)), options(ConflictPolicy.SKIP));

        assertThat(outcome).isEqualTo(RecordOutcome.SKIPPED);
        assertThat(kits.find(KitId.of("starter")).orElseThrow().items()).isEmpty(); // left untouched
    }

    @Test
    void dropsAnUnresolvableItemButStillImportsTheKit() {
        FakeKitRepository kits = new FakeKitRepository();
        RecordWriter writer = writer(kits);
        KitDefinition raw = KitDefinition.repeatable(
                KitId.of("mixed"),
                List.of(KitItem.of("definitely_not_an_item 1", 1), KitItem.of("stone 4", 4)),
                Duration.ZERO);

        writer.write(new ImportRecord.KitRecord(new ImportedKit(raw)), options(ConflictPolicy.OVERWRITE));

        KitDefinition saved = kits.find(KitId.of("mixed")).orElseThrow();
        assertThat(saved.items()).hasSize(1);
        assertThat(KitItemCodec.decode(saved.items().get(0)).getType()).isEqualTo(Material.STONE);
    }

    private RecordWriter writer(KitRepository kits) {
        return new RepositoryRecordWriter(
                mock(HomeRepository.class),
                mock(WarpRepository.class),
                mock(WalletRepository.class),
                mock(ModerationRepository.class),
                kits,
                mock(com.uxplima.uxmessentials.holograms.application.port.HologramRepository.class),
                mock(com.uxplima.uxmessentials.migration.PlayerWarpRecordWriter.class),
                mock(Currency.class),
                java.time.Clock.systemUTC());
    }

    private static ImportOptions options(ConflictPolicy onConflict) {
        return new ImportOptions(Path.of("."), ImportMode.LIVE, onConflict, BalancePolicy.SKIP_IF_PRESENT);
    }

    private static final class FakeKitRepository implements KitRepository {
        private final Map<String, KitDefinition> byId = new LinkedHashMap<>();

        @Override
        public Optional<KitDefinition> find(KitId id) {
            return Optional.ofNullable(byId.get(id.value()));
        }

        @Override
        public List<KitDefinition> all() {
            return List.copyOf(byId.values());
        }

        @Override
        public boolean exists(KitId id) {
            return byId.containsKey(id.value());
        }

        @Override
        public void save(KitDefinition definition) {
            byId.put(definition.id().value(), definition);
        }

        @Override
        public void delete(KitId id) {
            byId.remove(id.value());
        }
    }
}
