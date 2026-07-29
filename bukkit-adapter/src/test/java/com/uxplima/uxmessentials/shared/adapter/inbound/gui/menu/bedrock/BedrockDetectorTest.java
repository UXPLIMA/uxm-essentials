package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.bedrock;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/**
 * The Bedrock detector's selection rule and its degrade. Neither SDK is on the test runtime (Floodgate is a
 * {@code compileOnly} soft-depend, Geyser is purely reflective), exactly like {@code LuckPermsMetaSource} and the
 * other reflective hooks, so the delegating happy paths cannot be exercised here. What can be, and what decides
 * behaviour on a real server, is which detector {@link BedrockDetector#forServer} picks for each of the three
 * server shapes, that an unreachable API answers "Java player" rather than throwing into a menu open, and that
 * neither the interface nor {@code NONE} declares any {@code org.geysermc} type, so loading them on a Java-only
 * server pulls in zero SDK class.
 */
class BedrockDetectorTest {

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void none_isNeverBedrock() {
        assertThat(BedrockDetector.NONE.isBedrock(UUID.randomUUID())).isFalse();
    }

    @Test
    void forServer_withoutFloodgate_selectsNone() {
        // MockBukkit registers no floodgate plugin, so isPluginEnabled("floodgate") is false and the factory must
        // pick NONE without ever naming (or loading) the Floodgate-backed detector.
        BedrockDetector detector = BedrockDetector.forServer(server);

        assertThat(detector).isSameAs(BedrockDetector.NONE);
        assertThat(detector.isBedrock(UUID.randomUUID())).isFalse();
    }

    @Test
    void forServer_withGeyserOnly_selectsTheGeyserDetector() {
        // The Floodgate-less network shape: Geyser alone still knows who came from Bedrock, so the factory must
        // fall through to it rather than giving every Bedrock player a chest menu.
        MockBukkit.createMockPlugin("Geyser-Spigot");

        BedrockDetector detector = BedrockDetector.forServer(server);

        assertThat(detector).isNotSameAs(BedrockDetector.NONE);
        assertThat(detector.backend()).isEqualTo("geyser");
    }

    @Test
    void geyserDetector_withoutTheGeyserApi_answersJavaRatherThanThrowing() {
        // Geyser's API is not on the test classpath, so this exercises the degrade an unreachable API takes: a
        // menu open must not fail because Bedrock detection could not answer.
        MockBukkit.createMockPlugin("Geyser-Spigot");
        BedrockDetector detector = BedrockDetector.forServer(server);

        assertThat(detector.isBedrock(UUID.randomUUID())).isFalse();
    }

    @Test
    void none_reportsNoBackend() {
        assertThat(BedrockDetector.NONE.backend()).isEqualTo("none");
    }

    @Test
    void interfaceAndNoneDeclareNoFloodgateType() {
        // The structural confirmation that the interface and the NONE default carry no SDK type, so loading them
        // (and the forServer factory) on a Floodgate-less server cannot pull in org.geysermc.floodgate. Only
        // FloodgateBedrockDetector — constructed past forServer's present-guard — references the SDK.
        assertThat(referencesFloodgateSdk(BedrockDetector.class)).isFalse();
        assertThat(referencesFloodgateSdk(BedrockDetector.NONE.getClass())).isFalse();
    }

    private static boolean referencesFloodgateSdk(Class<?> type) {
        for (Method method : type.getDeclaredMethods()) {
            if (mentionsFloodgate(method.getReturnType())) {
                return true;
            }
            for (Class<?> parameter : method.getParameterTypes()) {
                if (mentionsFloodgate(parameter)) {
                    return true;
                }
            }
        }
        for (Field field : type.getDeclaredFields()) {
            if (mentionsFloodgate(field.getType())) {
                return true;
            }
        }
        return false;
    }

    private static boolean mentionsFloodgate(Class<?> type) {
        return type.getName().startsWith("org.geysermc");
    }
}
