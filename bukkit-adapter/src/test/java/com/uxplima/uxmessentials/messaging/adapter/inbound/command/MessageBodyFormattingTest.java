package com.uxplima.uxmessentials.messaging.adapter.inbound.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Objects;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmessentials.messaging.domain.MessageBody;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * What a sender may put in a private message.
 *
 * <p>The body is delivered inside a MiniMessage template, so a tag typed into it would be parsed with the template
 * unless something stops it. {@code uxmessentials.msg.color} is what decides, and until this test existed the node
 * was described in two javadocs and read by nothing, which meant every player had the privilege.
 */
class MessageBodyFormattingTest {

    private ServerMock server;
    private PlayerMock alice;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        alice = server.addPlayer("Alice");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void tagsArriveAsTextWhenTheSenderMayNotColourThem() {
        MessageBody body = requireBody(MessagingCommandSupport.body(alice, "<red>hello</red>"));

        assertThat(rendered(body)).isEqualTo("<red>hello</red>");
    }

    @Test
    void aClickTagCannotBeSmuggledInByAPlayerWithoutTheNode() {
        MessageBody body =
                requireBody(MessagingCommandSupport.body(alice, "<click:run_command:/op Alice>trust me</click>"));

        Component message = MiniMessage.miniMessage().deserialize(body.value());

        assertThat(message.clickEvent()).isNull();
        assertThat(PlainTextComponentSerializer.plainText().serialize(message))
                .isEqualTo("<click:run_command:/op Alice>trust me</click>");
    }

    @Test
    void theNodeLetsTagsThrough() {
        alice.addAttachment(MockBukkit.createMockPlugin("uxmEssentials"), MessagingCommandSupport.COLOUR, true);

        MessageBody body = requireBody(MessagingCommandSupport.body(alice, "<red>hello</red>"));

        assertThat(body.value()).isEqualTo("<red>hello</red>");
    }

    @Test
    void theLegacySectionSignNeverSurvivesEitherWay() {
        alice.addAttachment(MockBukkit.createMockPlugin("uxmEssentials"), MessagingCommandSupport.COLOUR, true);

        MessageBody body = requireBody(MessagingCommandSupport.body(alice, "\u00A7cred text"));

        assertThat(body.value()).doesNotContain("\u00A7");
    }

    /** What the receiver actually reads once the body has been through MiniMessage. */
    private static String rendered(MessageBody body) {
        return PlainTextComponentSerializer.plainText()
                .serialize(MiniMessage.miniMessage().deserialize(body.value()));
    }

    private static MessageBody requireBody(@Nullable MessageBody body) {
        assertThat(body).isNotNull();
        return Objects.requireNonNull(body);
    }

    @Test
    void anEmptyBodyIsStillRefused() {
        assertThat(MessagingCommandSupport.body(alice, "   ")).isNull();
    }
}
