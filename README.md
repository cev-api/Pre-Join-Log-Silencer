# Pre-Join Log Silencer

Scanner bots hammer every public Minecraft server all day. They never join. A connection opens, stops during
LOGIN or CONFIGURATION, and goes away. Minecraft prints an INFO line for each one, and those lines bury the
disconnects you care about.

```
[15:22:01 INFO]: Obs_probe (/32.188.152.209:51894) lost connection: Disconnected
```

A connection that never reached the PLAY state never became a player. In practice these connections are
scanners and bots, so that line carries nothing you can act on. This plugin and mod delete exactly that line
and nothing else.

A real player who joined and later disconnected still gets their line. The tool never touches WARN, ERROR,
stack traces or crashes. It never blocks, cancels or rate limits a connection. It does not try to identify
bots, it only stops them from filling your log.

## Install

- Bukkit family (CraftBukkit, Spigot, Paper, Purpur, Pufferfish, Folia):
  `prejoin-log-silencer-bukkit-1.0.0.jar` into `plugins/`
- Fabric: `prejoin-log-silencer-fabric-1.0.0.jar` into `mods/`

Minecraft 1.21.x and 26.x. Java 21 bytecode. No scheduler, so it is Folia safe.

## Configure

Bukkit `plugins/PreJoinLogSilencer/config.yml`, Fabric `config/prejoin-log-silencer.properties`.

| Key | Default | Function |
| --- | --- | --- |
| `enabled` | `true` | Master switch. |
| `suppress-login-disconnects` | `true` | Delete the LOGIN disconnect line. |
| `suppress-configuration-disconnects` | `true` | Delete the CONFIGURATION disconnect line. |
| `log-suppressed-count` | `false` (`true` on Fabric) | Write the totals to the log once an hour. |
| `log-suppressed-to-file` | `false` | Copy each removed line to a file. |
| `debug` | `false` | Write data for each possible disconnect event. |

## Commands

Bukkit, operators only: `/ls debug` and `/ls reload`. Fabric has no command, so `log-suppressed-count` is on
there by default.

## Build

Needs JDK 21 or higher.

```
./gradlew build
```

MIT, see [LICENSE](LICENSE).
