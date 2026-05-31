/**
 * Pure domain of the communication bounded context: the operator-authored broadcast surface that ties the
 * connection-message templates, the rotating announcer, and the static info pages together. Three families of
 * value object live here. {@code MessagePolicy} (with its {@code PolicyMode} and {@code Ordering}) decides which
 * template renders for a join, quit, or death — disabled, the vanilla default, or one of a list of operator
 * templates picked at random or in order. {@code PlaceholderBindings} is the small token map ({@code {player}},
 * {@code {count}}, …) every template is rendered against, with the substitution contract that replaces only the
 * known tokens and leaves an unknown one untouched. {@code AnnouncerSchedule} carries the timer cadence — the
 * interval, sequential-vs-random selection with the no-immediate-repeat rule, and the minimum-online gate — and
 * {@code AnnouncerCursor} is the immutable position the {@code NextAnnouncement} use case advances. {@code
 * InfoPage} pairs a command name with its lines.
 *
 * <p>Everything here is operator <em>content</em> rendered later through MiniMessage by the adapter, not plugin
 * strings: the templates and announcer lines never become {@code MessageKey}s and are never parity-checked. No
 * Bukkit, Paper, Kyori, or logging type appears in this package — the model is built from value objects and the
 * cross-cutting kernel primitive {@code PlayerRef}.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.communication.domain;
