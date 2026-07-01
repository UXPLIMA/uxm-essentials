/**
 * Bedrock detection: the substrate the later hybrid renderer builds on to answer "should this viewer get a native
 * Bedrock form instead of a chest menu?".
 *
 * <p>{@link com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.bedrock.BedrockDetector} answers a single
 * question — is a player a Floodgate/Bedrock player? — and has two implementations: the {@code NONE} constant
 * (always {@code false}, the Java-only default, carrying no Floodgate reference at all) and the
 * package-private {@code FloodgateBedrockDetector}, which delegates to the Floodgate SDK. The
 * {@code forServer(Server)} factory picks between them by probing whether the {@code floodgate} plugin is
 * enabled, so the Floodgate-backed detector — and the {@code org.geysermc.floodgate} class it references — is
 * only ever constructed on a server that actually has Floodgate installed. On a Java-only server the factory
 * returns {@code NONE} and no Floodgate class is ever loaded.
 *
 * <p>This is the same present-guard shape the LuckPerms meta source uses: a typed {@code compileOnly} SDK
 * reference isolated to one class, named only behind a plugin-present branch. It lives in the Bukkit adapter
 * shell (not the pure {@code spec}/{@code eval} packages), so it may touch Bukkit and the Floodgate API.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.bedrock;
