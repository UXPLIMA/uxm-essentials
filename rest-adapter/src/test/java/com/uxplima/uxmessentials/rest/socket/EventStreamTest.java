package com.uxplima.uxmessentials.rest.socket;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.uxplima.uxmessentials.rest.auth.Scopes;
import com.uxplima.uxmessentials.rest.http.HttpStatus;
import com.uxplima.uxmessentials.rest.http.Json;
import com.uxplima.uxmessentials.rest.http.RestServer;
import com.uxplima.uxmessentials.rest.http.Route;
import com.uxplima.uxmessentials.rest.http.Router;
import org.junit.jupiter.api.Test;

/**
 * The stream over a real socket, because a handshake that is right on paper still has to be written, flushed and
 * read in the order a client expects.
 */
class EventStreamTest {

    private static final String PATH = "/api/v1/events";
    private static final int MAX_SUBSCRIBERS = 2;
    private static final Logger QUIET = quietLogger();

    @Test
    void aSubscriberIsGreetedAndThenHearsWhatItAskedFor() throws IOException {
        EventStream stream = new EventStream(PATH, MAX_SUBSCRIBERS, QUIET);
        try (RestServer server = start(stream);
                Client client = Client.connect(server.port())) {
            assertThat(client.next().get("event").getAsString()).isEqualTo("connected");

            client.send("{\"subscribe\":[\"economy.*\"]}");
            assertThat(client.next().get("event").getAsString()).isEqualTo("subscribed");

            stream.publish("economy.wallet-credit", amount("25.00"));
            JsonObject event = client.next();

            assertThat(event.get("event").getAsString()).isEqualTo("economy.wallet-credit");
            assertThat(event.getAsJsonObject("data").get("amount").getAsString())
                    .isEqualTo("25.00");
        }
    }

    @Test
    void anEventNobodySubscribedToIsNotSent() throws IOException {
        EventStream stream = new EventStream(PATH, MAX_SUBSCRIBERS, QUIET);
        try (RestServer server = start(stream);
                Client client = Client.connect(server.port())) {
            client.next(); // the greeting
            client.send("{\"subscribe\":[\"home.create\"]}");
            client.next(); // the acknowledgement

            stream.publish("economy.wallet-credit", amount("25.00"));
            stream.publish("home.create", new JsonObject());

            assertThat(client.next().get("event").getAsString()).isEqualTo("home.create");
        }
    }

    @Test
    void aConnectionThatSubscribedToNothingHearsNothing() throws IOException {
        EventStream stream = new EventStream(PATH, MAX_SUBSCRIBERS, QUIET);
        try (RestServer server = start(stream);
                Client client = Client.connect(server.port())) {
            client.next(); // the greeting

            stream.publish("economy.wallet-credit", amount("25.00"));
            client.send("{\"subscribe\":[\"home.create\"]}");

            assertThat(client.next().get("event").getAsString()).isEqualTo("subscribed");
        }
    }

    @Test
    void aLineThatIsNotJsonIsAnsweredRatherThanDropped() throws IOException {
        EventStream stream = new EventStream(PATH, MAX_SUBSCRIBERS, QUIET);
        try (RestServer server = start(stream);
                Client client = Client.connect(server.port())) {
            client.next();
            client.send("hello?");

            JsonObject answer = client.next();
            assertThat(answer.get("event").getAsString()).isEqualTo("error");
            assertThat(answer.get("message").getAsString()).contains("subscribe");
        }
    }

    @Test
    void aPingIsAnsweredWithAPong() throws IOException {
        EventStream stream = new EventStream(PATH, MAX_SUBSCRIBERS, QUIET);
        try (RestServer server = start(stream);
                Client client = Client.connect(server.port())) {
            client.next();
            client.write(Frames.clientPing());

            assertThat(client.nextFrame().opcode()).isEqualTo(Frame.PONG);
        }
    }

    @Test
    void aClosedSubscriberLeavesTheLiveSet() throws IOException {
        EventStream stream = new EventStream(PATH, MAX_SUBSCRIBERS, QUIET);
        try (RestServer server = start(stream)) {
            try (Client client = Client.connect(server.port())) {
                client.next();
                assertThat(stream.subscribers()).isEqualTo(1);
                client.write(Frames.clientClose(1000));
                client.nextFrame(); // the server's close frame
            }
            awaitNoSubscribers(stream);
            assertThat(stream.subscribers()).isZero();
        }
    }

    /** Every open stream is a socket and a thread held until the client lets go, so there is a limit on them. */
    @Test
    void pastTheCapAFurtherStreamIsRefusedRatherThanOpened() throws IOException {
        EventStream stream = new EventStream(PATH, MAX_SUBSCRIBERS, QUIET);
        try (RestServer server = start(stream);
                Client first = Client.connect(server.port());
                Client second = Client.connect(server.port())) {
            first.next();
            second.next();

            assertThat(handshakeAnswer(server.port()))
                    .startsWith("HTTP/1.1 503 Service Unavailable")
                    .contains("too-many-subscribers");
        }
    }

    @Test
    void anOrdinaryRequestToTheStreamPathIsToldToUpgrade() throws IOException {
        try (RestServer server = start(new EventStream(PATH, MAX_SUBSCRIBERS, QUIET))) {
            assertThat(get(server.port())).startsWith("HTTP/1.1 426 Upgrade Required");
        }
    }

    @Test
    void aTokenWithoutTheScopeNeverReachesTheHandshake() throws IOException {
        RestServer.RequestFilter refusing = (request, route) -> RestServer.RequestFilter.Decision.refuse(
                Json.error(HttpStatus.FORBIDDEN, "missing-scope", "this token needs the events scope"));

        try (RestServer server = RestServer.start(
                "127.0.0.1", 0, router(), refusing, new EventStream(PATH, MAX_SUBSCRIBERS, QUIET), QUIET)) {
            assertThat(handshakeAnswer(server.port())).startsWith("HTTP/1.1 403 Forbidden");
        }
    }

    private static void awaitNoSubscribers(EventStream stream) {
        for (int attempt = 0; attempt < 100 && stream.subscribers() > 0; attempt++) {
            Thread.onSpinWait();
        }
    }

    private static JsonObject amount(String value) {
        JsonObject data = new JsonObject();
        data.addProperty("amount", value);
        return data;
    }

    private static RestServer start(EventStream stream) throws IOException {
        return RestServer.start("127.0.0.1", 0, router(), RestServer.RequestFilter.allowing(), stream, QUIET);
    }

    private static Router router() {
        return new Router().add(Route.of("GET", PATH, Scopes.EVENTS, request -> Json.done()));
    }

    private static String get(int port) throws IOException {
        return exchange(port, "GET " + PATH + " HTTP/1.1\r\nHost: localhost\r\n\r\n");
    }

    private static String handshakeAnswer(int port) throws IOException {
        return exchange(port, Client.HANDSHAKE);
    }

    private static String exchange(int port, String request) throws IOException {
        try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), port)) {
            socket.setSoTimeout(5_000);
            socket.getOutputStream().write(request.getBytes(StandardCharsets.UTF_8));
            socket.getOutputStream().flush();
            return new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Logger quietLogger() {
        Logger logger = Logger.getLogger(EventStreamTest.class.getName());
        logger.setUseParentHandlers(false);
        return logger;
    }

    /** A WebSocket client with just enough in it to hold up one end of a conversation. */
    private record Client(Socket socket, InputStream in, OutputStream out) implements AutoCloseable {

        private static final String HANDSHAKE = "GET " + PATH + " HTTP/1.1\r\n"
                + "Host: localhost\r\n"
                + "Upgrade: websocket\r\n"
                + "Connection: Upgrade\r\n"
                + "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n"
                + "Sec-WebSocket-Version: 13\r\n\r\n";

        static Client connect(int port) throws IOException {
            Socket socket = new Socket(InetAddress.getLoopbackAddress(), port);
            socket.setSoTimeout(5_000);
            OutputStream out = socket.getOutputStream();
            out.write(HANDSHAKE.getBytes(StandardCharsets.US_ASCII));
            out.flush();
            InputStream in = socket.getInputStream();
            readHead(in);
            return new Client(socket, in, out);
        }

        void send(String message) throws IOException {
            write(Frames.clientText(message));
        }

        void write(byte[] frame) throws IOException {
            out.write(frame);
            out.flush();
        }

        /** The next frame, whatever it is. */
        Frame nextFrame() throws IOException {
            return Frames.serverFrame(in);
        }

        /** The next text frame, parsed. */
        JsonObject next() throws IOException {
            Frame frame = nextFrame();
            return JsonParser.parseString(frame.text()).getAsJsonObject();
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }

        /** Read to the end of the handshake response, leaving the stream at the first frame. */
        private static void readHead(InputStream in) throws IOException {
            int consecutive = 0;
            while (consecutive < 2) {
                int next = in.read();
                if (next < 0) {
                    throw new IOException("the server closed during the handshake");
                }
                if (next == '\n') {
                    consecutive++;
                } else if (next != '\r') {
                    consecutive = 0;
                }
            }
        }
    }
}
