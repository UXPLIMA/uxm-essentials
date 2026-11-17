package com.uxplima.uxmessentials.shared.adapter.outbound.sink;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmessentials.communication.domain.CommunicationMessageKey;
import com.uxplima.uxmessentials.messaging.adapter.outbound.BukkitMessageDelivery;
import com.uxplima.uxmessentials.messaging.domain.MessageBody;
import com.uxplima.uxmessentials.security.domain.SecurityMessageKey;
import com.uxplima.uxmessentials.shared.adapter.outbound.bus.BusPublisher;
import com.uxplima.uxmessentials.shared.adapter.outbound.message.CatalogMessages;
import com.uxplima.uxmessentials.shared.adapter.outbound.message.ClientLocales;
import com.uxplima.uxmessentials.shared.adapter.outbound.message.HoconLocaleCatalog;
import com.uxplima.uxmessentials.shared.adapter.outbound.message.LocaleResolver;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.PlaceholderShield;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.ThemeFile;
import com.uxplima.uxmessentials.shared.application.port.LocaleStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.network.NetworkMessage;
import com.uxplima.uxmessentials.shared.network.StaffChatFrame;
import com.uxplima.uxmessentials.staff.adapter.outbound.BusStaffChatNetwork;
import com.uxplima.uxmessentials.staff.adapter.outbound.MessagingStaffChannel;
import com.uxplima.uxmessentials.staff.application.SendStaffChat;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.clip.placeholderapi.replacer.CharsReplacer;
import me.clip.placeholderapi.replacer.Replacer;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * With PlaceholderAPI installed, a placeholder a player types reaches the reader as typed, here and from another
 * server, while the placeholders the operator wrote into a line and its prefix are filled for the reader.
 */
class PlayerTextPlaceholdersTest {

    private static final String BREAK = "‌";

    @TempDir
    Path messagesDir;

    private ServerMock server;
    private PlayerMock staff;
    private PlayerRef staffRef;
    private PlayerRef alex;

    @BeforeEach
    void start() {
        server = MockBukkit.mock();
        staff = server.addPlayer("Staff");
        staffRef = new PlayerRef(staff.getUniqueId(), "Staff");
        alex = new PlayerRef(UUID.fromString("00000000-0000-0000-0000-000000000a1e"), "Alex");
    }

    @AfterEach
    void stop() {
        MockBukkit.unmock();
    }

    @Test
    void aPlaceholderTypedIntoAPrivateMessageArrivesAsTyped() {
        BukkitMessageDelivery delivery = new BukkitMessageDelivery(messages(), sink("<prefix>"));

        delivery.deliverMessage(alex, staffRef, MessageBody.of("hi %player_name%, your ip is %player_ip%"));

        String seen = nextLine();
        assertThat(seen).contains("hi %player_name%, your ip is %player_ip%");
        assertThat(seen).doesNotContain("203.0.113.7");
    }

    @Test
    void aPlaceholderTheOperatorWroteIntoTheLineAndThePrefixIsFilledForTheReader() throws IOException {
        Files.writeString(
                messagesDir.resolve("messages_en.conf"),
                "\"msg.received\" = \"<prefix> for %player_name%: {message}\"\n");
        BukkitMessageDelivery delivery = new BukkitMessageDelivery(messages(), sink("[%player_name%]"));

        delivery.deliverMessage(alex, staffRef, MessageBody.of("%player_name%"));

        // The catalogue's own words are drawn in small capitals; the prefix and the values are not.
        assertThat(nextLine()).startsWith("[Staff] ").endsWith(" Staff: %player_name%");
    }

    @Test
    void aStaffChatLineFromAnotherServerArrivesAsTyped() {
        CatalogMessages messages = messages();
        BusStaffChatNetwork network = new BusStaffChatNetwork(new NoBus());
        network.bind(new SendStaffChat(
                new MessagingStaffChannel(node -> List.of(staffRef), messages, sink("<prefix>"), "staff.chat"),
                event -> {},
                network,
                new Inline()));

        network.onFrame(new StaffChatFrame("lobby", UUID.randomUUID(), alex.uuid(), "Alex", "look: %player_ip%"));

        String seen = nextLine();
        assertThat(seen).contains("look: %player_ip%");
        assertThat(seen).doesNotContain("203.0.113.7");
    }

    @Test
    void aBroadcastBodyIsTheSendersOwnLineAndItsPlaceholdersAreFilled() {
        CatalogMessages messages = messages();

        String line = messages.resolveAsWritten(
                staffRef, CommunicationMessageKey.BROADCAST_LINE, Map.of("message", "hello %player_name%"));
        sink("<prefix>").deliver(staffRef, line);

        assertThat(nextLine()).contains("hello Staff");
    }

    @Test
    void aValueInAClickStaysExactSoASetupLinkStillCopies() {
        String uri = "otpauth://totp/Uxm%3AAlex?secret=ABC";

        sink("<prefix>")
                .deliver(
                        staffRef,
                        messages().resolve(staffRef, SecurityMessageKey.SECURITY_2FA_SETUP_URI, Map.of("uri", uri)));

        assertThat(clicks(nextComponent())).containsExactly(uri);
    }

    @Test
    void twoTypedValuesCannotTurnTheOperatorsClosingPercentIntoAPlaceholder() throws IOException {
        // The operator puts a placeholder right before one value, with another value earlier in the line. A value's
        // percent that could close the operator's placeholder would make its closing percent open %player_ip%.
        Files.writeString(
                messagesDir.resolve("messages_en.conf"),
                "\"staff.chat.format-remote\" = \"{player} <gray>%luckperms_prefix%{message}\"\n");
        CatalogMessages messages = messages();
        BusStaffChatNetwork network = new BusStaffChatNetwork(new NoBus());
        network.bind(new SendStaffChat(
                new MessagingStaffChannel(node -> List.of(staffRef), messages, sink("<prefix>"), "staff.chat"),
                event -> {},
                network,
                new Inline()));

        network.onFrame(new StaffChatFrame("lobby", UUID.randomUUID(), alex.uuid(), "%x_y", "player_ip%"));

        String seen = nextLine();
        assertThat(seen).isEqualTo("%x_y [VIP]player_ip%");
        assertThat(seen).doesNotContain("203.0.113.7");
    }

    @Test
    void aValueTypedIntoAClickIsNeitherFilledNorChanged() throws IOException {
        Files.writeString(
                messagesDir.resolve("messages_en.conf"),
                "\"msg.received\" = \"<click:suggest_command:'/msg {player} {message}'>{player}</click>\"\n");
        BukkitMessageDelivery delivery = new BukkitMessageDelivery(messages(), sink("<prefix>"));

        delivery.deliverMessage(alex, staffRef, MessageBody.of("%player_ip% 50%"));

        assertThat(clicks(nextComponent())).containsExactly("/msg Alex %player_ip% 50%");
    }

    private CatalogMessages messages() {
        HoconLocaleCatalog catalog = new HoconLocaleCatalog(new Quiet(), messagesDir, ThemeFile::shippedTheme);
        return new CatalogMessages(catalog, new LocaleResolver(new NoChoices(), new ClientLocales(), Locale.ENGLISH));
    }

    private static BukkitMessageSink sink(String prefix) {
        return new BukkitMessageSink(new Inline(), prefix, PlayerTextPlaceholdersTest::placeholderApi);
    }

    /**
     * PlaceholderAPI's own parser with the player and LuckPerms expansions, called the way the plugin calls it for the
     * reader: through the shield, which keeps a value's percent out of its sight.
     */
    private static UnaryOperator<String> placeholderApi(UUID reader) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(reader);
        Function<String, @Nullable PlaceholderExpansion> lookup = identifier -> switch (identifier) {
            case "player" -> new PlayerExpansion();
            case "luckperms" -> new PrefixExpansion();
            default -> null;
        };
        return PlaceholderShield.around(
                text -> new CharsReplacer(Replacer.Closure.PERCENT).apply(text, player, lookup));
    }

    private Component nextComponent() {
        Component line = staff.nextComponentMessage();
        assertThat(line).as("a line was delivered").isNotNull();
        return Objects.requireNonNull(line);
    }

    /** What every click in {@code line} carries, in order. */
    private static List<String> clicks(Component line) {
        List<String> found = new ArrayList<>();
        ClickEvent<?> click = line.clickEvent();
        if (click != null && click.payload() instanceof ClickEvent.Payload.Text text) {
            found.add(text.value());
        }
        line.children().forEach(child -> found.addAll(clicks(child)));
        return found;
    }

    /** The next line the staff member saw, as text, with the invisible break the shield adds set aside. */
    private String nextLine() {
        Component line = staff.nextComponentMessage();
        assertThat(line).as("a line was delivered").isNotNull();
        return PlainTextComponentSerializer.plainText().serialize(line).replace(BREAK, "");
    }

    /** LuckPerms' expansion, as far as these lines ask it: everyone's prefix is [VIP]. */
    private static final class PrefixExpansion extends PlaceholderExpansion {

        @Override
        public String getIdentifier() {
            return "luckperms";
        }

        @Override
        public String getAuthor() {
            return "test";
        }

        @Override
        public String getVersion() {
            return "1";
        }

        @Override
        public @Nullable String onRequest(@Nullable OfflinePlayer player, String params) {
            return params.equals("prefix") ? "[VIP]" : null;
        }
    }

    /** PlaceholderAPI's player expansion, as far as these lines ask it. */
    private static final class PlayerExpansion extends PlaceholderExpansion {

        @Override
        public String getIdentifier() {
            return "player";
        }

        @Override
        public String getAuthor() {
            return "test";
        }

        @Override
        public String getVersion() {
            return "1";
        }

        @Override
        public @Nullable String onRequest(@Nullable OfflinePlayer player, String params) {
            return switch (params) {
                case "name" -> player == null ? null : player.getName();
                case "ip" -> "203.0.113.7";
                default -> null;
            };
        }
    }

    /** Runs every hop at once, on the test thread. */
    private static final class Inline implements Scheduler {

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

    private static final class NoBus implements BusPublisher {

        private final List<NetworkMessage> sent = new ArrayList<>();

        @Override
        public void publish(NetworkMessage message) {
            sent.add(message);
        }

        @Override
        public String serverId() {
            return "survival";
        }
    }

    private static final class NoChoices implements LocaleStore {

        @Override
        public Optional<Locale> override(PlayerRef player) {
            return Optional.empty();
        }

        @Override
        public void setOverride(PlayerRef player, Locale locale) {}

        @Override
        public void clearOverride(PlayerRef player) {}
    }

    private static final class Quiet implements Logger {

        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }
}
