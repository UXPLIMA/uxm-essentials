package com.uxplima.uxmessentials.skin.adapter.inbound.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Cooldowns;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.PlayerNameIndex;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.application.port.SkinTextures;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.SkinTexture;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.skin.application.ClearSkin;
import com.uxplima.uxmessentials.skin.application.DescribeSkin;
import com.uxplima.uxmessentials.skin.application.DressLogin;
import com.uxplima.uxmessentials.skin.application.DropSkin;
import com.uxplima.uxmessentials.skin.application.PurgeSkinCache;
import com.uxplima.uxmessentials.skin.application.SetSkin;
import com.uxplima.uxmessentials.skin.application.SkinConfig;
import com.uxplima.uxmessentials.skin.application.UpdateSkin;
import com.uxplima.uxmessentials.skin.application.port.BedrockSkins;
import com.uxplima.uxmessentials.skin.application.port.SkinRepository;
import com.uxplima.uxmessentials.skin.application.port.SkinUploads;
import com.uxplima.uxmessentials.skin.application.port.SkinView;
import com.uxplima.uxmessentials.skin.domain.PlayerSkin;
import com.uxplima.uxmessentials.skin.domain.SkinModel;
import com.uxplima.uxmessentials.skin.domain.SkinSource;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.CommandSourceStackMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The {@code /skin} surface, driven through a real Brigadier dispatcher against the real use cases over
 * in-memory ports: which branch a player reaches, which source it builds, which line they read back, and where
 * the permission gates cut a branch off entirely.
 */
class SkinCommandTest {

    private static final SkinTexture TEXTURE = new SkinTexture("value", "signature");

    private ServerMock server;
    private Repository repository;
    private Textures textures;
    private Uploads uploads;
    private View view;
    private Names names;
    private Gate cooldowns;
    private SkinConfig config;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        repository = new Repository();
        textures = new Textures();
        uploads = new Uploads();
        view = new View();
        names = new Names();
        cooldowns = Gate.open();
        config = SkinConfig.defaults();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void theBareNameFormWearsThatAccountsSkin() {
        textures.known.put("Notch", TEXTURE);
        PlayerMock player = allowed("Wearer");

        execute(player, "skin Notch");

        assertThat(lines(player)).contains("skin.applied");
        assertThat(view.dressed).containsExactly("Wearer");
        assertThat(repository.find(player.getUniqueId()).orElseThrow().source())
                .isEqualTo(new SkinSource.ByName("Notch"));
    }

    @Test
    void anUnknownNameIsRefusedRatherThanLeavingThePlayerBare() {
        PlayerMock player = allowed("Wearer");

        execute(player, "skin set Nobody");

        assertThat(lines(player)).contains("skin.not-found");
        assertThat(view.dressed).isEmpty();
    }

    @Test
    void theSlimWordCutsAnUploadedImageForTheThreePixelArm() {
        PlayerMock player = allowed("Wearer");

        execute(player, "skin url \"https://i.imgur.com/a.png\" slim");

        assertThat(uploads.models).containsExactly(SkinModel.SLIM);
        assertThat(lines(player)).contains("skin.applied");
    }

    @Test
    void aUrlOutsideTheAllowlistNeverReachesTheUploadService() {
        PlayerMock player = allowed("Wearer");

        execute(player, "skin url \"https://elsewhere.example/a.png\"");

        assertThat(uploads.asked).isEmpty();
        assertThat(lines(player)).contains("skin.url-not-allowed");
    }

    @Test
    void aFileNamesTheServersOwnFolderRatherThanTheWeb() {
        PlayerMock player = allowed("Wearer");

        execute(player, "skin file knight");

        assertThat(uploads.asked).containsExactly("knight");
        assertThat(repository.find(player.getUniqueId()).orElseThrow().source())
                .isEqualTo(new SkinSource.ByFile("knight"));
    }

    @Test
    void aPlayerWhoIsStillOnCooldownIsToldSoAndNothingIsResolved() {
        cooldowns = Gate.holding();
        textures.known.put("Notch", TEXTURE);
        PlayerMock player = allowed("Wearer");

        execute(player, "skin Notch");

        assertThat(lines(player)).contains("skin.on-cooldown");
        assertThat(textures.asked).isEmpty();
    }

    @Test
    void clearingWithNothingStoredSaysSoInsteadOfPretendingToUndress() {
        PlayerMock player = allowed("Wearer");

        execute(player, "skin clear");

        assertThat(lines(player)).contains("skin.nothing-to-clear");
    }

    @Test
    void staffDressAnotherPlayerAndReadTheOtherPlayersWording() {
        textures.known.put("Notch", TEXTURE);
        PlayerMock staff = allowed("Staff");
        PlayerMock target = server.addPlayer("Target");
        names.known.put("Target", new PlayerRef(target.getUniqueId(), "Target"));

        execute(staff, "skin set Notch Target");

        assertThat(lines(staff)).contains("skin.set-for-other");
        assertThat(view.dressed).containsExactly("Target");
    }

    @Test
    void aPlayerWithoutTheOtherNodeCannotReachTheThirdArgumentAtAll() {
        PlayerMock player = server.addPlayer("Wearer");
        player.addAttachment(MockBukkit.createMockPlugin(), "uxmessentials.skin.use", true);
        CommandSourceStack source = CommandSourceStackMock.from(player);

        assertThatThrownBy(() -> dispatcher().execute("skin set Notch Target", source))
                .isInstanceOf(CommandSyntaxException.class);
    }

    @Test
    void anUnknownTargetIsNamedBackRatherThanSilentlyIgnored() {
        PlayerMock staff = allowed("Staff");

        execute(staff, "skin info Ghost");

        assertThat(lines(staff)).contains("skin.unknown-player");
    }

    @Test
    void infoReadsBackTheStoredSourceModelAndWhenItWasSet() {
        PlayerMock staff = allowed("Staff");
        PlayerMock target = server.addPlayer("Target");
        PlayerRef ref = new PlayerRef(target.getUniqueId(), "Target");
        names.known.put("Target", ref);
        repository.save(new PlayerSkin(
                ref,
                new SkinSource.ByName("Notch"),
                TEXTURE,
                SkinModel.CLASSIC,
                Instant.parse("2026-08-18T10:15:00Z")));

        execute(staff, "skin info Target");

        assertThat(lines(staff))
                .contains("skin.info.header", "skin.info.source", "skin.info.model", "skin.info.applied");
    }

    @Test
    void droppingAStoredSkinDeletesTheRowAndSaysSo() {
        PlayerMock staff = allowed("Staff");
        PlayerMock target = server.addPlayer("Target");
        PlayerRef ref = new PlayerRef(target.getUniqueId(), "Target");
        names.known.put("Target", ref);
        repository.save(new PlayerSkin(ref, new SkinSource.ByName("Notch"), TEXTURE, SkinModel.CLASSIC, Instant.EPOCH));

        execute(staff, "skin drop Target");

        assertThat(lines(staff)).contains("skin.dropped");
        assertThat(repository.find(ref.uuid())).isEmpty();
    }

    @Test
    void purgingForgetsOneNameSoTheNextLookupIsFresh() {
        PlayerMock staff = allowed("Staff");

        execute(staff, "skin purge Notch");

        assertThat(textures.purged).containsExactly("Notch");
        assertThat(lines(staff)).contains("skin.purged");
    }

    @Test
    void theRootWithNoArgumentsExplainsWhatToType() {
        PlayerMock player = allowed("Wearer");

        execute(player, "skin");

        assertThat(lines(player)).contains("skin.usage");
    }

    /** A player holding every {@code uxmessentials.skin.*} node the command gates on. */
    private PlayerMock allowed(String name) {
        PlayerMock player = server.addPlayer(name);
        for (String node : List.of("use", "url", "file", "update", "other", "drop", "info", "purge")) {
            player.addAttachment(MockBukkit.createMockPlugin(), "uxmessentials.skin." + node, true);
        }
        return player;
    }

    private void execute(PlayerMock player, String input) {
        CommandSourceStack source = CommandSourceStackMock.from(player);
        try {
            dispatcher().execute(input, source);
        } catch (CommandSyntaxException failure) {
            throw new AssertionError(failure.getMessage(), failure);
        }
    }

    private CommandDispatcher<CommandSourceStack> dispatcher() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(command().build());
        return dispatcher;
    }

    private SkinCommand command() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-18T12:00:00Z"), ZoneOffset.UTC);
        Events events = new Events();
        DressLogin dressLogin = new DressLogin(repository, textures, BedrockSkins.none(), config, new NoopLogger());
        SetSkin setSkin =
                new SetSkin(repository, textures, uploads, view, new EveryNode(), cooldowns, events, config, clock);
        return new SkinCommand(
                setSkin,
                new ClearSkin(repository, dressLogin, view, events, clock),
                new UpdateSkin(repository, textures, uploads, view, events, clock),
                new DropSkin(repository),
                new DescribeSkin(repository),
                new PurgeSkinCache(textures),
                names,
                new InlineScheduler(),
                new KeyMessages());
    }

    /** Every line the player was sent, which is the catalog key itself under {@link KeyMessages}. */
    private static List<String> lines(PlayerMock player) {
        List<String> read = new ArrayList<>();
        String message = player.nextMessage();
        while (message != null) {
            read.add(message);
            message = player.nextMessage();
        }
        return read;
    }

    /** A store holding at most one skin per player, like the real table. */
    private static final class Repository implements SkinRepository {
        private final Map<UUID, PlayerSkin> rows = new HashMap<>();

        @Override
        public Optional<PlayerSkin> find(UUID player) {
            return Optional.ofNullable(rows.get(player));
        }

        @Override
        public void save(PlayerSkin skin) {
            rows.put(skin.owner().uuid(), skin);
        }

        @Override
        public void delete(UUID player) {
            rows.remove(player);
        }
    }

    /** A name lookup answering from a fixed map, recording what it was asked and what it was told to forget. */
    private static final class Textures implements SkinTextures {
        private final Map<String, SkinTexture> known = new HashMap<>();
        private final List<String> asked = new ArrayList<>();
        private final List<String> purged = new ArrayList<>();

        @Override
        public CompletableFuture<Optional<SkinTexture>> byName(String username) {
            return CompletableFuture.completedFuture(fetchNow(username));
        }

        @Override
        public Optional<SkinTexture> fetchNow(String username) {
            asked.add(username);
            return Optional.ofNullable(known.get(username));
        }

        @Override
        public void purge(String username) {
            purged.add(username);
        }
    }

    /** An upload service that signs everything, recording the image and the model it was cut for. */
    private static final class Uploads implements SkinUploads {
        private final List<String> asked = new ArrayList<>();
        private final List<SkinModel> models = new ArrayList<>();

        @Override
        public Optional<SkinTexture> fromUrl(String url, SkinModel model) {
            asked.add(url);
            models.add(model);
            return Optional.of(TEXTURE);
        }

        @Override
        public Optional<SkinTexture> fromFile(String fileName, SkinModel model) {
            asked.add(fileName);
            models.add(model);
            return Optional.of(TEXTURE);
        }
    }

    /** A view recording who ended up wearing something. */
    private static final class View implements SkinView {
        private final List<String> dressed = new ArrayList<>();

        @Override
        public void apply(PlayerRef who, SkinTexture texture, SkinModel model) {
            dressed.add(who.name());
        }
    }

    /** A name index answering from a fixed map: nobody else exists. */
    private static final class Names implements PlayerNameIndex {
        private final Map<String, PlayerRef> known = new HashMap<>();

        @Override
        public Optional<PlayerRef> byName(String name) {
            return Optional.ofNullable(known.get(name));
        }

        @Override
        public void record(UUID uuid, String name) {
            known.put(name, new PlayerRef(uuid, name));
        }
    }

    /** The permissive server: the command's own gates are what this test is about, not the per-skin nodes. */
    private static final class EveryNode implements Permissions {
        @Override
        public boolean has(PlayerRef who, String node) {
            return true;
        }

        @Override
        public QuotaResult resolveQuota(PlayerRef who, QuotaFamily family, @Nullable WorldRef world, long fallback) {
            return QuotaResult.limited(fallback);
        }
    }

    /** A cooldown gate that is either open or holding everyone back. */
    private static final class Gate implements Cooldowns {
        private final @Nullable Duration remaining;

        private Gate(@Nullable Duration remaining) {
            this.remaining = remaining;
        }

        static Gate open() {
            return new Gate(null);
        }

        static Gate holding() {
            return new Gate(Duration.ofSeconds(12));
        }

        @Override
        public Result<Unit, Duration> check(PlayerRef who, CooldownKind kind) {
            return remaining == null ? Result.ok(Unit.INSTANCE) : Result.err(remaining);
        }

        @Override
        public void stamp(PlayerRef who, CooldownKind kind) {}

        @Override
        public Result<Unit, Duration> checkLabel(PlayerRef who, String label) {
            return check(who, new CooldownKind(label, 0L, CooldownStartPhase.TELEPORT));
        }

        @Override
        public void stampLabel(PlayerRef who, String label) {}
    }

    private static final class Events implements DomainEventPublisher {
        @Override
        public void publish(DomainEvent event) {}
    }

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

    /** Resolves every key to its dotted catalog id, so a test reads back which line was sent. */
    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    /** Runs every scheduler hop inline so the off-thread lookup resolves before the assertion. */
    private static final class InlineScheduler implements Scheduler {
        @Override
        public void onGlobal(Runnable task) {
            task.run();
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            task.run();
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            task.run();
        }

        @Override
        public void async(Runnable task) {
            task.run();
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            task.run();
        }
    }
}
