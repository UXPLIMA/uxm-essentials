/**
 * The moderation context's application layer — the use cases that orchestrate the domain through the
 * outbound ports, the {@link com.uxplima.uxmessentials.moderation.application.ModerationModule} feature
 * module, and the {@link com.uxplima.uxmessentials.moderation.application.ModerationMessageKey} catalog.
 *
 * <p>Each sanction is one use case ({@code Mute}, {@code Jail}, {@code TempBan}, {@code Kick}, {@code
 * IssueWarn}, {@code BanIp}, {@code Freeze}, {@code Seen}, …) returning a {@code Result} for its modelled
 * failures rather than throwing; the exempt-node check, the duration grammar and the audit emission are
 * shared. This layer also <em>provides</em> the cross-context gates the other contexts consume — {@link
 * com.uxplima.uxmessentials.moderation.application.RepositoryMutePolicy} for messaging and {@link
 * com.uxplima.uxmessentials.moderation.application.RepositoryJailGate} for teleport — implemented against the
 * consuming contexts' ports so the soft couple stays a contract.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.moderation.application;
