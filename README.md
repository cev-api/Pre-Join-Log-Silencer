# Pre-Join Log Silencer

Bots scan every public Minecraft server. They connect, never log in, then disconnect. The server writes a line
for each one, and those lines drown out the disconnects that matter.

```
[15:22:01 INFO]: Obs_probe (/32.188.152.209:51894) lost connection: Disconnected
```

That connection never became a player. It stopped during login, before it reached the game. This plugin and mod
delete those lines.

Your real players are never affected. Someone who joins and later disconnects still gets their line. Warnings,
errors, stack traces and crashes are untouched. Nothing is blocked, cancelled or rate limited.

## Compatibility

| Platform | Versions |
| --- | --- |
| CraftBukkit, Spigot, Paper, Purpur, Pufferfish, Folia | 1.21.x through 26.3 |
| Fabric | 1.21.x through 26.3 |

Java 21 or higher. The code uses no version-specific classes, no NMS, no mixins and no scheduler, which is why
one JAR covers the whole range. Folia is fully supported.

## Install

- Bukkit family: `prejoin-log-silencer-bukkit-1.0.0.jar` into `plugins/`
- Fabric: `prejoin-log-silencer-fabric-1.0.0.jar` into `mods/`

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
