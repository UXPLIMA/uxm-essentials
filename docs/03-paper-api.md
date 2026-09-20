# 3. The Paper API

Which parts of the platform this plugin uses, and the four places where using the obvious one
is wrong. It is written from the code that cites it.

## 3.1 Paper, not Bukkit or Spigot

The target is Paper 26.2, taken `compileOnly`. Where Paper offers its own API for something
Bukkit also offers, Paper's is the one used: it is the platform we support, and the Bukkit
equivalents are the ones with the surprising behaviour.

Nothing here schedules through `BukkitScheduler` or `BukkitRunnable`. See
`02-concurrency.md`, and the guard that fails the build over either.

## 3.2 `paper-plugin.yml`, never `plugin.yml`

Paper loads a `plugin.yml` in preference to a `paper-plugin.yml`, silently. A jar that ships
both loads through the legacy path, which has no `PluginBootstrap` and no `PluginLoader`: the
bootstrap never runs, the Brigadier registration lifecycle never fires, and the plugin comes
up looking enabled while most of it is not wired. It reads as "the commands are missing"
rather than as "the wrong manifest was picked", which is a long afternoon.

So no module's resources carry a `plugin.yml`. There is one manifest, in `:bukkit-adapter`,
and the Velocity jar carries its own annotation based descriptor instead.
`LegacyPluginYmlDriftTest` keeps it that way.

## 3.3 Commands are Brigadier

Commands are registered through the Brigadier lifecycle event, never as a `CommandExecutor`
or a `TabCompleter`. A command is declared as a spec, with its literal and its base
permission paired, and the module that owns it hands its specs to the composition root. A
module that is switched off registers nothing.

## 3.4 Storing state on the server

*§3.6 below is the number the code cites; §3.5 and the earlier subsections of this section
are unwritten.*

### 3.4.1 The database is for what must survive

Anything an operator or a player would miss after a restart or a rollback: homes, balances,
vault contents, sanctions. That is `:persistence-adapter`, and `01-architecture.md` §8 holds
its invariants.

### 3.6 The persistent data container is for what may die with a rollback

A cooldown stamp, a one-time claim flag, a kit unlock: transient per-holder state whose loss
in a world rollback is not a support ticket. Those live in the holder's PDC rather than in
the database, because writing them to the database would put a row per player per feature
behind a lock for something that is allowed to disappear.

The consequence to remember is offline players. An offline player has no PDC to read, so a
read answers "no stamp" and a write is a no-op. Both sides of that are deliberate and are the
same in every store that uses it.

## 3.7 Text

### 3.7.1 Parse once, on the way out

A message is resolved from the catalogue, parsed from MiniMessage into a component exactly
once, and delivered to the viewer on the viewer's own thread. The shared `<prefix>` tag is
supplied as a parsed resolver rather than inlined by every template.

There is no trusted and untrusted split at this layer, because there is nothing untrusted
here: every template is operator owned, out of the catalogue. Player supplied text never
reaches the parser as markup.

A viewer who is offline or gone is a silent no-op. The entity scheduler refuses a despawned
entity, and that is the right answer rather than an error to log.

### 3.7.2 No legacy colour

`ChatColor` and the section-sign codes are forbidden, and a guard fails the build over them.
`14-ui-style.md` says why: a legacy code is a colour no operator can restyle afterwards.

## 3.8 Events

An event handler is thin: it turns the event into a call on a use case and does nothing else.

Priority is a decision, not a default. A ban has to refuse a connection before player data
loads, so it is enforced at `PlayerLoginEvent` at `HIGHEST`, which runs after the lower
priorities and before `MONITOR`. A login another plugin already denied stays denied.
`09-deployment.md` §9.7 holds the rest of that.
