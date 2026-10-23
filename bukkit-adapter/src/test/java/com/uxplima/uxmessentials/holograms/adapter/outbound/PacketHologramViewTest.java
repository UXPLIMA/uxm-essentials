package com.uxplima.uxmessentials.holograms.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.Vector;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.destroystokyo.paper.event.player.PlayerUseUnknownEntityEvent;
import com.uxplima.uxmessentials.holograms.application.port.LeaderboardEntry;
import com.uxplima.uxmessentials.holograms.application.port.LeaderboardProvider;
import com.uxplima.uxmessentials.holograms.application.port.LeaderboardProviders;
import com.uxplima.uxmessentials.holograms.domain.Hologram;
import com.uxplima.uxmessentials.holograms.domain.HologramLine;
import com.uxplima.uxmessentials.holograms.domain.HologramName;
import com.uxplima.uxmessentials.holograms.domain.HologramPage;
import com.uxplima.uxmessentials.holograms.domain.LeaderboardSpec;
import com.uxplima.uxmessentials.holograms.domain.Rotation;
import com.uxplima.uxmessentials.holograms.domain.Visibility;
import com.uxplima.uxmessentials.shared.adapter.outbound.hud.HudText;
import com.uxplima.uxmessentials.shared.application.message.CatalogLines;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.shared.domain.action.ClickAction;
import com.uxplima.uxmessentials.shared.domain.action.ClickActionType;
import com.uxplima.uxmessentials.shared.domain.action.ClickTrigger;
import com.uxplima.uxmlib.packet.display.FakeHologramPackets;
import com.uxplima.uxmlib.packet.display.FakeRegionScheduler;
import com.uxplima.uxmlib.packet.display.HologramProbe;
import com.uxplima.uxmlib.packet.display.PacketHologramClicks;
import com.uxplima.uxmlib.packet.display.PacketLine;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * A stored hologram, drawn for each viewer with packets and never spawned. What the client holds is read back
 * through the library's probe, frame by frame, so every test here is a change seen as a change: a viewer who
 * changes language, turns a page, steps out of the audience or comes back reads what they should on the next frame,
 * and the world holds no display entity at all.
 */
class PacketHologramViewTest {

    private ServerMock server;
    private World world;
    private PlayerMock alice;
    private PlayerMock bob;

    private final FakeHologramPackets packets = new FakeHologramPackets();
    private final HologramProbe probe = new HologramProbe(packets);
    private final FakeRegionScheduler frames = new FakeRegionScheduler();
    private final StepScheduler scheduler = new StepScheduler();
    private final HologramPageState pages = new HologramPageState();
    private final Map<UUID, Locale> languages = new ConcurrentHashMap<>();
    private final Map<UUID, UnaryOperator<String>> bridges = new ConcurrentHashMap<>();
    private final Set<String> granted = new HashSet<>();
    private final Map<String, Set<UUID>> manual = new ConcurrentHashMap<>();
    private final Map<String, Set<UUID>> blacklist = new ConcurrentHashMap<>();
    private final Map<String, List<LeaderboardEntry>> boards = new ConcurrentHashMap<>();
    private final List<String> warnings = new ArrayList<>();
    private final List<String> clicked = new ArrayList<>();

    private PacketHologramClicks clicks;
    private PacketHologramView view;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        alice = playerAt("Alice");
        bob = playerAt("Bob");
        HudText.useLanguages(uuid -> languages.getOrDefault(uuid, Locale.ENGLISH));
        clicks = new PacketHologramClicks(inlineEntityHops());
        HologramViewerText text = new HologramViewerText(
                uuid -> bridges.getOrDefault(uuid, UnaryOperator.identity()),
                MiniMessage.miniMessage(),
                TagResolver::empty,
                pages);
        HologramViewers viewers = new HologramViewers(
                new GrantedPermissions(),
                name -> manual.getOrDefault(name.value(), Set.of()),
                name -> blacklist.getOrDefault(name.value(), Set.of()));
        LeaderboardProvider kills = limit -> boards.getOrDefault("kills", List.of());
        LeaderboardProviders leaderboards = new LeaderboardProviders(Map.of("kills", kills));
        view = new PacketHologramView(
                packets,
                frames,
                scheduler,
                new RecordingLogger(),
                text,
                source -> source.replace(":heart:", "❤"),
                viewers,
                npc -> Optional.empty(),
                leaderboards,
                pages,
                clicks,
                (player, name, attack) -> clicked.add(player.getName() + ":" + name.value() + ":" + attack));
    }

    @AfterEach
    void tearDown() {
        HudText.useLanguages(uuid -> Locale.ROOT);
        HudText.useCatalogLines(CatalogLines.none());
        MockBukkit.unmock();
    }

    // -- drawing ----------------------------------------------------------------------------------------------

    @Test
    void aTextHologramIsDrawnWithPacketsAndLeavesNoEntityInTheWorld() {
        view.render(text("welcome", "hello"));
        frame();

        assertThat(heldText(alice)).isEqualTo("hello");
        assertThat(world.getEntitiesByClass(TextDisplay.class)).isEmpty();
    }

    @Test
    void severalLinesAreOnePanelAsTheDisplayEntityDrewThem() {
        view.render(lines("rules", "first", "second"));
        frame();

        assertThat(probe.heldBy(alice)).hasSize(1);
        assertThat(heldText(alice)).isEqualTo("first\nsecond");
    }

    /** A line everybody reads the same still goes through the server's own bridge, which turns a symbol into its glyph. */
    @Test
    void aSharedLineIsReadThroughTheServersBridge() {
        view.render(text("lives", "lives :heart:"));
        frame();

        assertThat(heldText(alice)).isEqualTo("lives ❤");
    }

    /** Nothing changes for a hologram nobody edits, so it is resolved once, not once per frame or per viewer. */
    @Test
    void aStaticHologramIsResolvedOnceForEverybody() {
        AtomicInteger resolves = new AtomicInteger();
        view = viewWithGlobalBridge(source -> {
            resolves.incrementAndGet();
            return source;
        });
        view.render(text("static", "nothing changes here"));
        frame();
        frames.tick();
        frames.tick();

        assertThat(heldText(alice)).isEqualTo("nothing changes here");
        assertThat(heldText(bob)).isEqualTo("nothing changes here");
        assertThat(resolves).hasValue(1);
    }

    // -- each viewer ------------------------------------------------------------------------------------------

    @Test
    void eachViewerReadsACatalogueLineInTheirOwnLanguage() {
        catalogue();
        languages.put(alice.getUniqueId(), Locale.forLanguageTag("tr"));
        view.render(text("join", "@lobby.join"));
        frame();

        assertThat(heldText(alice)).isEqualTo("Katil");
        assertThat(heldText(bob)).isEqualTo("Join");
    }

    @Test
    void aViewerWhoChangesLanguageReadsTheNewOneOnTheNextFrame() {
        catalogue();
        view.render(text("join", "@lobby.join"));
        frame();
        assertThat(heldText(alice)).isEqualTo("Join");

        languages.put(alice.getUniqueId(), Locale.forLanguageTag("tr"));
        frames.tick();
        scheduler.runAll();
        frames.tick();

        assertThat(heldText(alice)).isEqualTo("Katil");
        assertThat(heldText(bob)).isEqualTo("Join");
    }

    /**
     * A viewer's own values are read on the viewer's own thread, never on the hologram's: under Folia a viewer may
     * stand in another region from the hologram, and reading their state from there is not safe.
     */
    @Test
    void eachViewersTextIsResolvedOnThatViewersOwnThread() {
        bridges.put(alice.getUniqueId(), source -> source.replace("%name%", "Alice"));
        bridges.put(bob.getUniqueId(), source -> source.replace("%name%", "Bob"));
        view.render(text("hello", "hello %name%"));
        frames.firstFrame();

        assertThat(scheduler.entityHops).containsExactlyInAnyOrder(alice.getUniqueId(), bob.getUniqueId());
        scheduler.runAll();
        frames.tick();
        assertThat(heldText(alice)).isEqualTo("hello Alice");
        assertThat(heldText(bob)).isEqualTo("hello Bob");
    }

    /** A viewer is shown nothing until their own text is ready, so no frame ever reads in the wrong language. */
    @Test
    void aViewerIsShownTheHologramOnlyOnceTheirTextIsReady() {
        bridges.put(alice.getUniqueId(), source -> source.replace("%name%", "Alice"));
        view.render(text("hello", "hello %name%"));
        frames.firstFrame();

        assertThat(probe.heldBy(alice)).isEmpty();
        scheduler.runAll();
        frames.tick();
        assertThat(heldText(alice)).isEqualTo("hello Alice");
    }

    @Test
    void nobodyNearbyMeansNothingIsResolvedForAnybody() {
        alice.teleport(new Location(world, 500, 64, 500));
        bob.teleport(new Location(world, 500, 64, 500));
        view.render(text("hello", "hello %name%"));
        frame();

        assertThat(scheduler.entityHops).isEmpty();
    }

    @Test
    void aViewerWhoseTextFailsIsLeftOutAndTheOthersStillReadTheirs() {
        bridges.put(alice.getUniqueId(), source -> {
            throw new IllegalStateException("boom");
        });
        bridges.put(bob.getUniqueId(), source -> source.replace("%name%", "Bob"));
        view.render(text("hello", "hello %name%"));
        frame();

        assertThat(probe.heldBy(alice)).isEmpty();
        assertThat(heldText(bob)).isEqualTo("hello Bob");
        assertThat(warnings).anySatisfy(warning -> assertThat(warning).contains("hello"));
    }

    /** A text that cannot be read is not read again on every frame, which would flood the log and the threads. */
    @Test
    void aTextThatFailsIsNotTriedAgainUntilSomethingItReadsChanges() {
        AtomicInteger attempts = new AtomicInteger();
        bridges.put(alice.getUniqueId(), source -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("boom");
        });
        view.render(text("hello", "hello %name%"));
        frame();
        frames.tick();
        scheduler.runAll();
        frames.tick();

        assertThat(attempts).hasValue(1);
        assertThat(probe.heldBy(alice)).isEmpty();
    }

    @Test
    void aPlayerWhoLeftTheRangeIsTakenOffAndOneWhoCameBackIsDrawnAgain() {
        view.render(text("welcome", "hello"));
        frame();
        alice.teleport(new Location(world, 500, 64, 500));
        frames.tick();
        assertThat(probe.heldBy(alice)).isEmpty();

        alice.teleport(new Location(world, 1, 64, 1));
        frames.tick();
        assertThat(heldText(alice)).isEqualTo("hello");
    }

    // -- who may see it ---------------------------------------------------------------------------------------

    @Test
    void aPermissionHologramIsDrawnOnlyForTheHoldersOfItsNode() {
        granted.add(alice.getUniqueId() + "=staff.board");
        view.render(text("staff", "staff only").withVisibility(Visibility.restrictedTo("staff.board")));
        frame();

        assertThat(heldText(alice)).isEqualTo("staff only");
        assertThat(probe.heldBy(bob)).isEmpty();
    }

    @Test
    void aManualHologramIsDrawnOnlyForTheViewersItWasShownTo() {
        manual.put("secret", Set.of(bob.getUniqueId()));
        view.render(text("secret", "for bob").withVisibility(Visibility.manual()));
        frame();

        assertThat(probe.heldBy(alice)).isEmpty();
        assertThat(heldText(bob)).isEqualTo("for bob");
    }

    @Test
    void showingAManualHologramToSomebodyDrawsItForThemAtOnce() {
        view.render(text("secret", "for alice").withVisibility(Visibility.manual()));
        frame();
        assertThat(probe.heldBy(alice)).isEmpty();

        manual.put("secret", Set.of(alice.getUniqueId()));
        view.applyManualViewer(HologramName.of("secret"), alice.getUniqueId(), true);
        scheduler.runAll();

        assertThat(heldText(alice)).isEqualTo("for alice");
    }

    @Test
    void aBlacklistedViewerIsNeverDrawnTheHologram() {
        blacklist.put("welcome", Set.of(alice.getUniqueId()));
        view.render(text("welcome", "hello"));
        frame();

        assertThat(probe.heldBy(alice)).isEmpty();
        assertThat(heldText(bob)).isEqualTo("hello");
    }

    @Test
    void aDistanceLimitIsHowFarAViewerMayStand() {
        alice.teleport(new Location(world, 10, 64, 0));
        view.render(
                text("near", "close by").withVisibility(Visibility.everyone().withDistance(5)));
        frame();

        assertThat(probe.heldBy(alice)).isEmpty();
        assertThat(heldText(bob)).isEqualTo("close by");
    }

    // -- models -----------------------------------------------------------------------------------------------

    @Test
    void anItemHologramIsOneItemLine() {
        view.render(Hologram.createItem(HologramName.of("torch"), at(), "TORCH", Instant.EPOCH));
        frame();

        assertThat(probe.heldBy(alice))
                .singleElement()
                .isInstanceOfSatisfying(
                        PacketLine.Item.class,
                        line -> assertThat(line.item().getType()).isEqualTo(Material.TORCH));
    }

    @Test
    void anEntityHologramIsOneMobLine() {
        view.render(Hologram.createEntity(HologramName.of("zombie"), at(), "ZOMBIE", Instant.EPOCH));
        frame();

        assertThat(probe.heldBy(alice)).singleElement().isEqualTo(PacketLine.mob("zombie"));
    }

    @Test
    void aModelThatDoesNotExistIsSkippedAndSaidSo() {
        view.render(Hologram.createItem(HologramName.of("bad"), at(), "NOT_A_MATERIAL", Instant.EPOCH));
        frame();

        assertThat(probe.heldBy(alice)).isEmpty();
        assertThat(warnings).anySatisfy(warning -> assertThat(warning).contains("bad"));
    }

    @Test
    void aRotatedHologramFacesItsRotation() {
        view.render(text("sign", "hello").withRotation(Rotation.of(90f, 15f)));
        frame();

        assertThat(spawnsOf(alice)).allSatisfy(spawn -> {
            assertThat(spawn.yaw()).isEqualTo(90f);
            assertThat(spawn.pitch()).isEqualTo(15f);
        });
    }

    @Test
    void anUnrotatedHologramFacesTheWayItWasPlaced() {
        view.render(Hologram.create(
                HologramName.of("placed"),
                new Position(worldRef(), 0.0, 64.0, 0.0, 45f, 0f),
                List.of(new HologramLine("hello")),
                Instant.EPOCH));
        frame();

        assertThat(spawnsOf(alice)).allSatisfy(spawn -> assertThat(spawn.yaw()).isEqualTo(45f));
    }

    @Test
    void aGrowUpHologramIsRaisedByItsTextSoItsBottomSitsOnTheAnchor() {
        view.render(lines("tall", "one", "two", "three").withGrowUp(true));
        frame();

        assertThat(spawnsOf(alice))
                .allSatisfy(spawn -> assertThat(spawn.y()).isEqualTo(64.0 + 3 * PacketHologramView.LINE_HEIGHT));
    }

    // -- edits ------------------------------------------------------------------------------------------------

    @Test
    void renderingAgainReplacesWhatTheViewerHolds() {
        view.render(text("welcome", "hello"));
        frame();
        view.render(text("welcome", "goodbye"));
        frame();

        assertThat(probe.heldBy(alice)).hasSize(1);
        assertThat(heldText(alice)).isEqualTo("goodbye");
    }

    @Test
    void aDespawnedHologramIsTakenOffEveryClient() {
        view.render(text("welcome", "hello"));
        frame();
        view.despawn(HologramName.of("welcome"));
        scheduler.runAll();
        frames.tick();

        assertThat(probe.heldBy(alice)).isEmpty();
        assertThat(probe.heldBy(bob)).isEmpty();
    }

    @Test
    void despawningEverythingTakesEveryHologramOff() {
        view.render(text("one", "first"));
        frame();
        view.render(text("two", "second"));
        frame();
        assertThat(probe.heldBy(alice)).hasSize(2);
        view.despawnAll();
        scheduler.runAll();

        assertThat(probe.heldBy(alice)).isEmpty();
    }

    // -- pages ------------------------------------------------------------------------------------------------

    @Test
    void turningAPageChangesOnlyThatViewersText() {
        view.render(text("guide", "page one").withPageAppended(HologramPage.of(List.of(new HologramLine("page two")))));
        frame();

        view.cyclePage(alice, HologramName.of("guide"));
        scheduler.runAll();

        assertThat(heldText(alice)).isEqualTo("page two");
        assertThat(heldText(bob)).isEqualTo("page one");
    }

    // -- clicks -----------------------------------------------------------------------------------------------

    @Test
    void aHologramWithAClickCommandAnswersAClickOnItsBox() {
        view.render(text("portal", "click me").withClickCommand("spawn"));
        frame();

        clickBox(alice, false);
        scheduler.runAll();

        assertThat(clicked).containsExactly("Alice:portal:false");
    }

    /** A hologram with click actions and nothing else needs a box too, or its actions can never fire. */
    @Test
    void aHologramWithOnlyClickActionsGetsABoxToo() {
        view.render(
                text("shop", "buy").withActionAdded(new ClickAction(ClickTrigger.ANY, ClickActionType.MESSAGE, "hi")));
        frame();

        assertThat(boxesSentTo(alice)).isNotEmpty();
    }

    @Test
    void aHologramWithNothingToDoOnAClickHasNoBox() {
        view.render(text("plain", "just words"));
        frame();

        assertThat(boxesSentTo(alice)).isEmpty();
    }

    /** A client sends a click only within three blocks, so a look from further off is worked out by the server. */
    @Test
    void aLookFromAcrossTheSquareFindsAClickableHologram() {
        view.render(text("portal", "click me").withClickCommand("spawn"));
        frame();
        lookFrom(alice, -8, 64.1, -90f);

        assertThat(view.aimedAt(alice, 12)).hasValueSatisfying(aimed -> {
            assertThat(aimed.name().value()).isEqualTo("portal");
            assertThat(aimed.distance()).isCloseTo(8.0, within(0.01));
        });
    }

    @Test
    void aLookAwayOrOutOfReachFindsNothing() {
        view.render(text("portal", "click me").withClickCommand("spawn"));
        frame();

        lookFrom(alice, -8, 64.1, 90f);
        assertThat(view.aimedAt(alice, 12)).isEmpty();
        lookFrom(alice, -8, 64.1, -90f);
        assertThat(view.aimedAt(alice, 6)).isEmpty();
    }

    @Test
    void aHologramNothingHappensOnIsNeverAimedAt() {
        view.render(text("plain", "just words"));
        frame();
        lookFrom(alice, -8, 64.1, -90f);

        assertThat(view.aimedAt(alice, 12)).isEmpty();
    }

    /** The text is drawn raised and scaled, so a look is matched against the text where it is drawn. */
    @Test
    void aRaisedHologramIsAimedAtWhereItIsDrawn() {
        Hologram raised = text("portal", "click me").withClickCommand("spawn");
        raised = raised.withAppearance(raised.appearance().withTranslation(0f, 3f, 0f));
        view.render(raised);
        frame();

        lookFrom(alice, -8, 64.1, -90f);
        assertThat(view.aimedAt(alice, 12)).isEmpty();
        lookFrom(alice, -8, 67.1, -90f);
        assertThat(view.aimedAt(alice, 12)).isPresent();
    }

    /** The box a close click lands on stands where the raised text is drawn, not on the anchor below it. */
    @Test
    void theClickBoxRisesWithTheText() {
        Hologram raised = text("portal", "click me").withClickCommand("spawn");
        raised = raised.withAppearance(raised.appearance().withTranslation(0f, 3f, 0f));
        view.render(raised);
        frame();

        assertThat(boxSpawnsOf(alice))
                .last()
                .satisfies(box -> assertThat(box.y()).isCloseTo(67.0, within(0.001)));
    }

    // -- refresh ----------------------------------------------------------------------------------------------

    @Test
    void aLeaderboardIsDrawnFromItsProviderAndRefreshedInPlace() {
        boards.put("kills", List.of(new LeaderboardEntry("Ayse", "10")));
        Hologram board = text("top", "placeholder").withLeaderboard(new LeaderboardSpec("kills", 3));
        view.render(board);
        scheduler.runAll();
        frame();
        assertThat(heldText(alice)).contains("Ayse").contains("10");
        int spawnsBefore = spawnsOf(alice).size();

        boards.put("kills", List.of(new LeaderboardEntry("Bob", "12")));
        view.refresh(board);
        scheduler.runAll();
        frames.tick();

        assertThat(heldText(alice)).contains("Bob").contains("12");
        assertThat(spawnsOf(alice)).as("a refresh repaints and never respawns").hasSize(spawnsBefore);
    }

    @Test
    void aRefreshMovesAnAnimatedLine() {
        view.render(text("ticker", "<anim:typewriter>welcome"));
        frame();
        String before = heldText(alice);

        view.refresh(text("ticker", "<anim:typewriter>welcome"));
        view.refresh(text("ticker", "<anim:typewriter>welcome"));
        scheduler.runAll();
        frames.tick();

        assertThat(heldText(alice)).isNotEqualTo(before);
    }

    @Test
    void aPlayerWhoQuitIsForgotten() {
        bridges.put(alice.getUniqueId(), source -> source.replace("%name%", "Alice"));
        view.render(text("hello", "hello %name%"));
        frame();

        view.forget(alice.getUniqueId());
        int hopsBefore = scheduler.entityHops.size();
        frames.tick();

        assertThat(scheduler.entityHops)
                .as("a forgotten viewer is resolved again")
                .hasSize(hopsBefore + 1);
    }

    // -- fixtures ---------------------------------------------------------------------------------------------

    private void frame() {
        scheduler.runAll();
        frames.firstFrame();
        scheduler.runAll();
        frames.tick();
    }

    private PacketHologramView viewWithGlobalBridge(UnaryOperator<String> globalBridge) {
        return new PacketHologramView(
                packets,
                frames,
                scheduler,
                new RecordingLogger(),
                new HologramViewerText(
                        uuid -> bridges.getOrDefault(uuid, UnaryOperator.identity()),
                        MiniMessage.miniMessage(),
                        TagResolver::empty,
                        pages),
                globalBridge,
                new HologramViewers(new GrantedPermissions(), name -> Set.of(), name -> Set.of()),
                npc -> Optional.empty(),
                new LeaderboardProviders(Map.of()),
                pages,
                clicks,
                (player, name, attack) -> {});
    }

    /** A library scheduler that runs an entity hop at once, which is all the click router asks of it. */
    private static com.uxplima.uxmlib.scheduler.Scheduler inlineEntityHops() {
        return (com.uxplima.uxmlib.scheduler.Scheduler) java.lang.reflect.Proxy.newProxyInstance(
                com.uxplima.uxmlib.scheduler.Scheduler.class.getClassLoader(),
                new Class<?>[] {com.uxplima.uxmlib.scheduler.Scheduler.class},
                (proxy, method, args) -> {
                    if (!method.getName().equals("entity")) {
                        throw new UnsupportedOperationException(method.getName());
                    }
                    ((Runnable) args[1]).run();
                    return null;
                });
    }

    private void catalogue() {
        HudText.useCatalogLines(new CatalogLines(
                (viewer, key, arguments) ->
                        Locale.forLanguageTag("tr").equals(languages.get(viewer.uuid())) ? "Katil" : "Join",
                Set.of("lobby.join")::contains));
    }

    private PlayerMock playerAt(String name) {
        PlayerMock player = server.addPlayer(name);
        player.teleport(new Location(world, 1, 64, 1));
        return player;
    }

    private String heldText(Player viewer) {
        List<PacketLine> held = probe.heldBy(viewer);
        assertThat(held).as("what %s holds", viewer.getName()).isNotEmpty();
        return PlainTextComponentSerializer.plainText().serialize(((PacketLine.Text) held.get(0)).text());
    }

    private List<FakeHologramPackets.Spawn> spawnsOf(Player viewer) {
        List<FakeHologramPackets.Spawn> spawns = new ArrayList<>();
        for (Object packet : packets.packetsFor(viewer)) {
            collect(packet, FakeHologramPackets.Spawn.class, spawns);
        }
        return spawns.stream()
                .filter(spawn -> !spawn.entityTypeKey().equals("interaction"))
                .toList();
    }

    private List<FakeHologramPackets.Spawn> boxSpawnsOf(Player viewer) {
        List<FakeHologramPackets.Spawn> spawns = new ArrayList<>();
        for (Object packet : packets.packetsFor(viewer)) {
            collect(packet, FakeHologramPackets.Spawn.class, spawns);
        }
        return spawns.stream()
                .filter(spawn -> spawn.entityTypeKey().equals("interaction"))
                .toList();
    }

    /** Stand {@code viewer} with their eyes at {@code eyeY}, looking level along {@code yaw}. */
    private void lookFrom(PlayerMock viewer, double x, double eyeY, float yaw) {
        viewer.teleport(new Location(world, x, eyeY - viewer.getEyeHeight(), 0, yaw, 0f));
    }

    private List<FakeHologramPackets.InteractionSize> boxesSentTo(Player viewer) {
        List<FakeHologramPackets.InteractionSize> boxes = new ArrayList<>();
        for (Object packet : packets.packetsFor(viewer)) {
            collect(packet, FakeHologramPackets.InteractionSize.class, boxes);
        }
        return boxes;
    }

    private static <T> void collect(Object packet, Class<T> type, List<T> into) {
        if (packet instanceof FakeHologramPackets.Bundle bundle) {
            for (Object inner : bundle.packets()) {
                collect(inner, type, into);
            }
        } else if (type.isInstance(packet)) {
            into.add(type.cast(packet));
        }
    }

    private void clickBox(Player player, boolean attack) {
        int box = boxesSentTo(player).getLast().entityId();
        clicks.onUseUnknownEntity(
                new PlayerUseUnknownEntityEvent(player, box, attack, EquipmentSlot.HAND, new Vector(0, 0, 0)));
        frames.tick();
    }

    private Hologram text(String name, String line) {
        return Hologram.create(HologramName.of(name), at(), List.of(new HologramLine(line)), Instant.EPOCH);
    }

    private Hologram lines(String name, String... lines) {
        return Hologram.create(
                HologramName.of(name),
                at(),
                List.of(lines).stream().map(HologramLine::new).toList(),
                Instant.EPOCH);
    }

    private Position at() {
        return new Position(worldRef(), 0.0, 64.0, 0.0, 0f, 0f);
    }

    private WorldRef worldRef() {
        return new WorldRef(world.getUID(), world.getName());
    }

    /** Grants the nodes this test put in {@link #granted}, as {@code uuid=node}. */
    private final class GrantedPermissions implements Permissions {

        @Override
        public boolean has(PlayerRef who, String node) {
            return granted.contains(who.uuid() + "=" + node);
        }

        @Override
        public QuotaResult resolveQuota(PlayerRef who, QuotaFamily family, @Nullable WorldRef world, long def) {
            return QuotaResult.unlimited();
        }
    }

    private final class RecordingLogger implements Logger {

        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {
            warnings.add(message + " " + List.of(args));
        }

        @Override
        public void error(String message, Throwable cause) {
            warnings.add(message + " " + cause);
        }

        @Override
        public void debug(String message, Object... args) {}
    }

    /**
     * Queues every hop and runs them when the test says so, recording whose entity thread each one asked for. A
     * region hop and an async one queue the same way, so a test steps through what the server would interleave.
     */
    private static final class StepScheduler implements Scheduler {

        private final List<Runnable> queued = new ArrayList<>();
        private final List<UUID> entityHops = new ArrayList<>();

        @Override
        public void onGlobal(Runnable task) {
            queued.add(task);
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            queued.add(task);
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            entityHops.add(player.uuid());
            queued.add(task);
        }

        @Override
        public void async(Runnable task) {
            queued.add(task);
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            queued.add(task);
        }

        /** Run every queued hop, and the hops they queue, failing rather than spinning when they never settle. */
        void runAll() {
            int rounds = 0;
            while (!queued.isEmpty()) {
                if (++rounds > 100) {
                    throw new AssertionError("the hops never settle: each round queues another");
                }
                List<Runnable> due = List.copyOf(queued);
                queued.clear();
                due.forEach(Runnable::run);
            }
        }
    }
}
