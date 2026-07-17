/**
 * The server-tweaks context's domain: the pure decision behind the console-spam filter
 * ({@link com.uxplima.uxmessentials.servertweaks.domain.ConsoleFilterPolicy}), which decides whether a single
 * rendered console line should be suppressed against an operator-configured substring list. It is a conservative,
 * side-effect-free predicate — the Log4j2 filter that attaches it to the server logger lives in the adapter. Pure
 * Java: no Bukkit, Paper, Kyori, or SLF4J.
 */
@NullMarked
package com.uxplima.uxmessentials.servertweaks.domain;

import org.jspecify.annotations.NullMarked;
