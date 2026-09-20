# 9. Deployment

How this plugin is installed, what else can be installed beside it, and what an operator has
to decide before a server starts. It is written from the code that cites it, so every number
and every key below is read out of the source rather than remembered.

Three shapes are described, and the code calls them by these names. **Path A** is one Paper
server. **Path B** is a network of backends behind a proxy, or behind Redis. **Path C** is the
Discord bridge. They compose: a network that also mirrors to Discord is B and C together.

## 9.1 The jars

The build produces five, and only the first is required.

| Jar | Where it goes | What it is |
|---|---|---|
| `uxmEssentials.jar` | a Paper server's `plugins/` | the plugin. Everything below is optional. |
| `uxmEssentials-velocity.jar` | the proxy's `plugins/` | the bus broker, Path B |
| `uxmEssentials-redis.jar` | every backend's `plugins/` | the Redis carrier for the bus, Path B without a proxy |
| `uxmEssentials-discord.jar` | one backend's `plugins/` | the Discord bridge, Path C |
| `uxmEssentials-rest.jar` | a backend's `plugins/` | the published API over HTTP |

Each optional jar is a real plugin rather than a library. The Redis one carries Lettuce and
its Netty and Reactor transitives relocated into itself, so the main jar holds no Redis client
at all: a server that does not use Redis never loads one. It publishes its transport factory
through the `ServicesManager` and the host looks it up, which is what lets the transport
instance cross into the host's bus without a loader constraint error.

Nothing else of ours is needed. Each jar carries the uxmLib modules it uses, shaded, so no
library jar goes beside them.

## 9.2 Path A: one server

Drop `uxmEssentials.jar` into `plugins/` and start the server. There is nothing to configure
first.

The first enable writes the editable defaults next to the database: the root `config.conf`,
the per-module tree under `modules/`, the message catalogues under `messages/`, and the
menus. Twelve languages ship (`en`, `tr`, `de`, `ru`, `es`, `pt`, `fr`, `pl`, `uk`, `zh`,
`ja`, `ko`), and a key a translation is missing falls back to the English wording rather than
to the key name.

Storage defaults to SQLite in `uxmessentials.db` under the plugin folder, so a bare install
needs no database server. The `storage` block chooses otherwise:

```hocon
storage {
  backend = "sqlite"            # sqlite | mysql | postgres
  file    = "uxmessentials.db"  # SQLite only
  host    = "localhost"
  port    = 3306                # 3306 for mysql, 5432 for postgres
  database = "uxmessentials"
  username = "uxmessentials"
  password = ""
  read-pool-size = 8            # network backends only; SQLite is a single writer
  connection-timeout-ms = 5000
}
```

A network backend is what a network of servers needs, because Path B shares the data as well
as the events. SQLite is right for one server and wrong for several.

Turning a feature off is per module, in its own `modules/<name>/config.conf`. A module that is
off registers no listener and no command.

## 9.3 Path B: a network of backends

Two things have to be true for a change on one backend to reach the others: the data is
shared, and the event is carried. The first is the `storage` block above, pointed at one
MySQL or Postgres server. The second is the bus.

The bus is off by default, so a single server runs with no proxy and no behavioural change.

```hocon
network {
  enabled   = false                      # opt this backend into the shared bus
  server-id = "server-1"                 # unique per backend
  bus-channel = "uxmessentials:bus_v1"   # must match the proxy broker
  heartbeat-seconds = 30
  transport = "velocity"                 # velocity | redis | both
  redis {
    host = "127.0.0.1"
    port = 6379
    password = ""
    channel = "uxmessentials:bus"
  }
}
```

**`server-id` is the one value nobody may copy between backends.** It is the origin tag
stamped into every outbound frame and the sentinel that stops a frame coming back to its
sender. Two backends that share it corrupt origin routing.

**The carrier is a choice, not a requirement.** `velocity` moves frames as plugin messages
through the proxy, which needs `uxmEssentials-velocity.jar` on the proxy and no other service.
`redis` moves them over one pub/sub channel and needs no proxy in the path, which is what a
network without Velocity uses, and it needs `uxmEssentials-redis.jar` on every backend. `both`
fans out over both carriers for a mixed network. An unrecognised value falls back to
`velocity` and warns rather than refusing to start. With the Redis companion absent the bus
degrades to local only.

**The channel name has to match on both sides.** An operator who renames `bus-channel` on the
backends and not on the proxy gets a bridge that silently carries nothing.

**The queue is bounded.** Outbound frames buffer up to `outbound-queue-size` (256 by default)
and the oldest are dropped past it. A backend that cannot reach the bus therefore leaks no
memory.

**A peer is live for three beats.** `heartbeat-seconds` is how often a backend announces
itself, and a peer ages out of the `/uxmess doctor` count after three missed beats, so one
missed ping does not flap the cluster count.

The proxy jar also carries proxy-side command control, so a command an operator disables is
disabled at the proxy too rather than only on the backend the player happens to be on.

## 9.4 Path C: the Discord bridge

`uxmEssentials-discord.jar` goes on one backend, never on all of them: each copy would mirror
the same event again. It is its own Paper plugin, it soft-depends on the host, and it is
dormant when it is extracted.

1. Start the server once with the jar in place. It writes `config/discord.conf` and does
   nothing else.
2. Make a bot in the Discord developer portal and paste its token into `token`. The bridge
   only posts text, so the privileged message content intent is **not** required.
3. Turn on Developer Mode in Discord, copy the channel ids, and fill in `channels.audit` and,
   if the server wants money kept apart, `channels.economy`. A category left blank is not
   forwarded. Economy left blank folds those notices into the audit category.
4. Set `enabled = true` and reload.

Three things protect the channel and the bot. `min-eco-notify` drops routine low-value `/pay`
notifications below an amount. `max-per-minute` is a flood cap, 60 by default, after which
further notices are dropped until the minute rolls over: a mass kick, a bulk economy sweep or
a migration import that emits thousands of audit lines cannot exhaust the REST budget. The
loop guard means a notice the bridge itself raised is never mirrored twice.

A bad token does not take the server with it. The bridge self-disables on a connect failure
and logs the reason.

When the host's `discordlink` module is on, the bridge also registers the `/link` slash
command players use to confirm the code `/discordlink` gives them in game. That needs no extra
setup and no privileged intent.

## 9.5 The REST add-on

`uxmEssentials-rest.jar` publishes the developer API over HTTP. It compiles against the
published API and nothing else, which is deliberate: an endpoint that cannot be written
without reaching into the host's internals is an endpoint a third-party plugin could not write
either, so the missing piece is added to the published API rather than worked around in the
add-on.

Its defaults are the cautious ones: off, bound to loopback, with a rate limit a panel
refreshing every few seconds never notices and a script in a loop hits quickly. Tokens are
made in game, with its own command.

## 9.6 Audit logging

Every action an operator may later have to answer for is written as one line on the dedicated
`com.uxplima.uxmessentials.audit` logger channel. It is not the plugin log, and that is the
point: a channel of its own is what lets an operator route it to a retained file in their
logging configuration while the plugin log rotates as usual.

The lines are structured, not prose:

```
event=player_mute actor=<uuid> target=<uuid> duration=permanent ok=true reason="spam in chat"
```

- `event=` first, then `actor=`, then the fields the event carries.
- A player is logged by UUID, because a name changes and a UUID does not. The name is carried
  where the catalogue lists it.
- A free-form value, which in practice means a reason, is double quoted with backslash escapes
  so a reason with spaces in it stays one greppable field.
- One line per state change. `/kickall` writes one line with the count, never one per player.

Moderation, economy, vaults, itemworld, trade and the migration importer all write here. The
Discord bridge subscribes to the same stream, which is why turning the bridge on needs no
change anywhere else.

## 9.7 Login enforcement

A ban has to refuse a connection before player data loads, so the enforcement point is
`PlayerLoginEvent` at priority `HIGHEST`, and that is canon rather than preference. The
listener asks one use case whether a lockdown, an active tempban or an IP ban bars this login,
and disallows the connection when it does.

`HIGHEST` runs after the lower priorities and before `MONITOR`, so a plugin that only watches
the final decision still sees ours. A login another plugin has already denied is left denied.

The connecting IP is read from the event's real address, so the IP ban and the alt check are
real lookups rather than a UUID-only gate. The lockdown bypass is a live permission read off
the connecting player, which is the one place a permission can be resolved before data load.

## 9.8 Upgrading

Replace the jar and start the server. Nothing else is a step.

A file the operator already has is never overwritten: their values, their comments and their
formatting stay as they left them. What an update adds is the settings that did not exist when
they installed, appended as a commented HOCON block at the end of the file, so a new knob is
something they can read and edit rather than a default buried in the jar. Which keys count as
new is decided against the copy of the previously shipped default kept under `.defaults/`, so
a key they deleted on purpose stays deleted. A malformed file is reported and left alone, and
tried again on the next start.

`ConfigReachesAnOldServerTest` is the guard that keeps the call in the boot, and
`CONTRACT.md` §21 is the family rule it belongs to.

## 9.9 Checking an install

- `/uxmess doctor` runs the health checks and prints them: the database answers, the
  scheduler is the one this platform needs, the economy provider is the expected one, how
  many modules are on, how many peers the bus can see, whether the bus is really delivering
  rather than merely configured, which installed plugin owns a command we also own, which
  optional integrations are installed and enabled, and whether an update exists. The
  integration line warns on the case that costs an evening: a dependency the operator
  configured and the server cannot reach. Redis is probed only when the bus is on with a
  Redis carrier, by a short TCP connect to the configured host and port. `/uxmess doctor
  repair confirm` is the one that changes something.
- `/uxmess status` is the short version.
- `/uxmess reload` re-reads what can be re-read. The `network` block cannot: the channel, the
  captured `server-id` and the chosen transport are bound once at enable, so a change there
  needs a restart.
