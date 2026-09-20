# Permissions

What a node looks like, what an operator may grant, and the three conventions that make the
whole surface predictable. It is written from the code that cites it, so every rule below is
one a class already follows.

**The full list is not here, and that is deliberate.** `PermissionCatalog` is the one place a
node is declared, and the plugin reads it back rather than asking anybody to keep a page in
step with it:

- `/uxmess permissions` lists the areas.
- `/uxmess permissions <area> [page]` lists an area, eight nodes to a page, with the
  description and the default of each.
- `/uxmess permissions export` writes the whole catalogue as markdown into the data folder.

What the running plugin prints is what the build actually has. A page cannot be out of date
in a way the plugin cannot. The audit that produced the catalogue found thirty nine live
nodes the server was never told about, two advertised under a name no code reads, and four
that nothing read at all, all because a node had to be written in three places that nothing
kept in step.

## The node space

Every node begins `uxmessentials.`, and the constructor of `PermissionSpec` refuses one that
does not. Every node carries a description an operator can act on, and a blank one is refused
the same way.

After the prefix comes the area, then the verb: `uxmessentials.economy.pay`,
`uxmessentials.home.limit`, `uxmessentials.moderation.exempt`. An area is a module id, or
`shared` for the kernel's own.

Each node declares the default it registers with:

| Default | Who has it | Used for |
|---|---|---|
| `FALSE` | nobody until granted | anything that changes another player or the world |
| `TRUE` | everybody | the self-service verbs a normal player is expected to have |
| `OP` | server operators | the administrative and moderation surfaces |
| `NOT_OP` | everybody except operators | an exemption, where operators are already covered |

## The four shapes

`PermissionShape` says how a node is completed.

- **`FIXED`.** One node, exactly as written. These are the ones registered with the server on
  enable, so a permission plugin can suggest and complete them.
- **`QUOTA`.** A family completed with a number where more is better:
  `uxmessentials.home.limit.<n>`. A player holding several keeps the largest.
- **`TIER`.** A family completed with a number of seconds where less is better: a cooldown or
  a warmup. A player holding several keeps the smallest, and `0` removes the wait.
- **`LABEL`.** A family completed with a name the operator chose: a warp, a kit, a module, a
  world. The set is open because the content is theirs, not ours.

A family is written with its placeholder visible, as `.<n>` or `.<name>`, and the constructor
refuses a family that hides it or a `FIXED` node that pretends to be one. Families are not
registered with the server: the set is open, so there is nothing finite to register.

## The numbered-node convention

A limit is never a rank. There is no `vip` branch anywhere in this plugin. An operator grants
a number on a node and names the group whatever they like:

```
uxmessentials.home.limit.5          # five homes
uxmessentials.home.limit.nether.2   # two in the world called nether
uxmessentials.tp.warmup.0           # no warmup
uxmessentials.vault.size.54         # a 54 slot vault
```

The families in use today are `uxmessentials.home.limit`, `uxmessentials.pwarp.limit`,
`uxmessentials.vault.size`, `uxmessentials.vault.amount`, `uxmessentials.rtp.radius`,
`uxmessentials.tp.warmup` and `uxmessentials.economy.salary.amount`. The list grows; the rule
does not.

### Uniform numeric reducer

One reducer resolves every numbered family, so a quota behaves the same whichever context
asks. It folds four sources into one number:

1. every unscoped node the player holds, `uxmessentials.<family>.<value>`,
2. every world-scoped node, `uxmessentials.<family>.<world>.<value>`, when the caller passes a
   world,
3. the numeric meta value, when a permission plugin exposes one,
4. the configured default, so a player holding no node still resolves.

The direction is the family's:

- **Maximum** for a quota, because more is better. `-1` is the unlimited sentinel and beats
  every number.
- **Minimum** for a cooldown or a warmup, because less is better.
- **Sum** for a family declared as stacking, which is for servers that want tiers to add up
  rather than to outrank each other. The configured default is used only when the player holds
  no tier node at all; it is never added on top of the tiers.

The reducer is pure and world scoping is part of it, so `uxmessentials.home.limit.nether.2` is
not a special case anybody implements twice.

### The meta convention

A family node maps to a meta key by dropping the prefix and joining the rest with a hyphen:
`uxmessentials.home.limit` reads the meta key `home-limit`. The caller never writes a meta
name, which is what keeps the two in step.

LuckPerms stays a soft dependency through this seam. The default `MetaSource.none()` reports
no meta and the reducer runs on the numbered nodes alone; the LuckPerms source binds only when
LuckPerms is on the classpath and registered.

## A command is a literal and a base node

Every command literal is paired with exactly one base permission, in the context's command
surface, and the pairing is data rather than a check written inside the handler. An operator
who grants the base node has the command.

### Economy

`uxmessentials.economy.balance`, `.pay`, `.pay.toggle`, `.payall`, `.baltop`, `.worth`,
`.sell` and the rest each guard the literal of the same name, and `uxmessentials.economy.admin`
guards `/eco` itself.

Two things in this area are not base permissions and are easy to mistake for them. The fine
grained `uxmessentials.economy.admin.give`, `.take`, `.set` and `.bulk` gate what an
administrator may do once inside `/eco`. The `uxmessentials.economy.baltop.exempt` marker is
read off a player to keep them out of the rich list; nothing grants a command with it.

### Itemworld

The item verbs are grouped, and a group can be switched off in
`modules/itemworld/config.conf` as well as ungranted. `uxmessentials.item.use` is the base of
`/item`, and each verb carries its own node inside the group it belongs to. A command whose
group is off answers as though it did not exist, whatever the player holds.

### Audit trail

Only the abusable verbs are audited, not every read. Giving an item, repairing, giving to
everybody, and the equivalents in the other areas write one structured line each to the audit
channel. The format, the channel name and how to route it to a retained file are in
`09-deployment.md` §Audit logging.

## Three gates that outrank every node

A node grants a verb. It does not override a sanction, and the sanction is checked in one
place so every use case applies the same one.

- **`uxmessentials.moderation.exempt`** makes a player immune to being muted, jailed,
  tempbanned, kicked, warned, IP banned or frozen. It is a target-side check: it says what may
  be done to the holder, not what the holder may do.
- **Mute.** A muted player's `/msg`, `/reply`, `/mail send` and `/helpop` are blocked
  regardless of the messaging nodes they hold.
- **Jail.** A jailed player's self-initiated teleports are blocked regardless of the teleport
  nodes: `/home`, `/tpa`, `/rtp`, `/back`, `/spawn` and the homes and warps verbs that delegate
  to them.

Mute and jail are moderation state, and the other contexts ask for it through a port rather
than reading it. When the moderation module is off, both ports bind to their `NEVER`
implementation, so messaging and teleport degrade to "nobody is muted" and "nobody is jailed"
rather than failing. That is the same degrade-when-the-other-module-is-off pattern the warp
cost uses against economy.

## What keeps this true

`PermissionCatalogDriftTest` checks the catalogue against what the code reads, so a node
cannot quietly exist without being declared, and a declared node nothing reads is caught too.
`QuotaNodeReducerTest` pins the reducer's directions and the unlimited sentinel. The
registration itself happens before anything can check a permission, so a permission plugin
sees the whole surface rather than the subset a hand written file happened to list.
