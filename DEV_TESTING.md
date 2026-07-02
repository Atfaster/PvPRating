# Development Test Launch

Эта инструкция описывает локальный запуск мода для PvP-тестирования.

Используется один dedicated server и два dev-клиента:

- `server` - локальный Forge dedicated server.
- `MainDev` - первый клиент.
- `OffDev` - второй клиент.

## Запуск внутри VS Code

1. Открыть корень этого репозитория в VS Code.
2. Нажать `Ctrl+Shift+P`.
3. Выбрать `Tasks: Run Task`.
4. Запустить задачу:

```text

```

Эта задача запускает три integrated-terminal task:

- `Dev: Server`
- `Dev: MainDev Client`
- `Dev: OffDev Client`

Клиенты стартуют с небольшой задержкой, чтобы сервер успел подняться.

После запуска оба клиента подключать к серверу через:

```text
localhost
```

или:

```text
127.0.0.1:25565
```

Останавливать сервер нужно командой в серверном терминале:

```text
stop
```

## Запуск внешними PowerShell-окнами

Если VS Code tasks не нужны, можно запустить скрипт вручную:

```powershell
.\scripts\start-dev-pvp.ps1
```

Скрипт откроет три отдельных PowerShell-окна: сервер, `MainDev`, `OffDev`.

Можно изменить задержку запуска клиентов:

```powershell
.\scripts\start-dev-pvp.ps1 -ClientDelaySeconds 40
```

## Важные настройки dev-сервера

Для локальных dev-клиентов `MainDev` и `OffDev` сервер должен работать в offline mode:

```properties
online-mode=false
enforce-secure-profile=false
```

Эти настройки находятся в:

```text
run/server/server.properties
```

На публичном сервере такие настройки использовать нельзя. Они нужны только для локального dev-тестирования.

## PvPRating dev config overrides

The dedicated dev world uses:

```toml
spawnKillProtectionSeconds = 0
```

This makes repeated PvP kills easier to test locally. The code defaults remain safer for normal generated configs: `spawnKillProtectionSeconds = 60`.

## Towny dev plugin preparation

Before the `server` Gradle task starts, the dev scripts run:

```powershell
.\scripts\prepare-dev-towny.ps1
```

The script copies the local Towny test plugin jars from:

```text
libs/towny/
```

to:

```text
run/server/plugins/
```

It currently prepares:

```text
Towny-0.101.2.0.jar
TownyChat-0.119.jar
FlagWar-0.7.0.jar
```

The script also removes older managed Towny/TownyChat/FlagWar jars from the dev `plugins/` directory so Arclight does not load stale incompatible versions.

If local reference configs exist in:

```text
server-reference/Towny/settings/
```

the script also copies `config.yml` and `townyperms.yml` into:

```text
run/server/plugins/Towny/settings/
```

Existing Towny config files in `run/server/plugins/Towny/settings/` are not overwritten unless the script is run manually with:

```powershell
.\scripts\prepare-dev-towny.ps1 -OverwriteConfigs
```

For local PvP testing, the script forces `economy.using_economy` to `false` in the copied Towny config. This avoids requiring Vault, Reserve, or another economy plugin just to create towns and nations in the dev runtime.

Important: the standard ForgeGradle `server` run is still a Forge dedicated server. It prepares the plugin files, but Bukkit plugins load only when the server runtime is Arclight or another compatible Forge/Bukkit hybrid server.

## Arclight Towny runtime

The VS Code task `Dev: Start PvP Stack In VS Code` now starts:

- `Dev: Arclight Server`
- `Dev: MainDev Client`
- `Dev: OffDev Client`

Before using it, put the Arclight server jar here:

```text
local-runtime/arclight/server.jar
```

This file is local-only and ignored by Git. Use an Arclight 1.20.1 server jar matching the real server as closely as possible. The real server reported:

```text
arclight-1.20.1-1.0.6-SNAPSHOT-6de9fec
```

The local Arclight server files are stored here:

```text
run/arclight-server/
```

On start, `scripts/start-dev-arclight-server.ps1` builds PvPRating, copies the built `pvprating-*.jar` into `run/arclight-server/mods/`, prepares Towny plugins, writes a local `eula.txt`, and creates a dev `server.properties` with:

```properties
online-mode=false
enforce-secure-profile=false
```

The clients now wait longer before launch because Arclight startup is slower than the ForgeGradle dev server. If the clients open before the server is ready, wait for the server console to finish startup and connect to:

```text
localhost
```
