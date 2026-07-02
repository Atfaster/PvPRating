# PvPRating

PvPRating is a Forge mod for Minecraft 1.20.1 that stores and displays a configurable PvP rating for each player. The rating is not a bounty payout. It is a server-side score for estimating how dangerous a player is on a PvP server and for giving players a value to compare.

Developer setup notes are kept separately in [LOCAL_DEVELOPMENT.md](LOCAL_DEVELOPMENT.md).

## English

### PvP Events

When one survival-mode player kills another survival-mode player:

- the killer can gain rating points;
- the victim can lose rating points;
- self-kills, non-player kills, and non-survival kills do not change the default PvP rating;
- optional Towny integration can skip default rating changes for same-town, same-nation, allied-nation, and mutual-friend kills.

### Rating Points

Rating points represent each player's PvP rating. The value is saved on the player as the `rating` persistent data key. The numeric display uses the integer part of that value; the sync packet also sends the full rating and the current known maximum rating for icon scaling.

Default rating math:

- Killer: `current rating + gain + current rating * killer multiplier + victim rating * claim multiplier`
- Victim: `current rating - loss - current rating * target multiplier`
- If complete loss is enabled: `victim rating = -loss - current rating * target multiplier`.
- If `preventNegativeRating` is enabled, the victim rating is clamped to `0` after the loss calculation.

### Rating Display

The default rating display adds a rank word before the player name and a small sword icon before the numeric rating. The icon is rendered through a custom font glyph, so it follows the same player display name path as the rating value and appears in name tags, the player list, and other display-name contexts.

Hovering over either the sword icon or the displayed rating number shows the tooltip `PvP Rating`.

PvPRating includes 10 rank levels. The rank word and sword icon are synchronized, so the same rating level always uses the same rank and icon.

Default rank names:

| Level | English | Russian |
| --- | --- | --- |
| 1 | Neutral | Нейтрал |
| 2 | Fighter | Боец |
| 3 | Warrior | Воин |
| 4 | Knight | Рыцарь |
| 5 | Veteran | Ветеран |
| 6 | Gladiator | Гладиатор |
| 7 | Elite | Элита |
| 8 | Champion | Чемпион |
| 9 | Legend | Легенда |
| 10 | Immortal | Бессмертный |

By default, ranks use manual thresholds from `rankThresholds`. The default thresholds are distributed evenly from rating `0` to rating `200`, so rank 10 starts at `200`.

If `rankAbsoluteSystem` is enabled, rank and icon levels use the older absolute scaling behavior. Rating `0` and negative ratings use level 1. The highest known positive rating on the server, including offline players already known to PvPRating, uses level 10. Positive ratings between `0` and that maximum are scaled evenly across the available levels, so small servers naturally skip some intermediate levels when the maximum rating is below `10`.

The icon resources are stored here:

```text
src/main/resources/assets/pvprating/font/rating_icons.json
src/main/resources/assets/pvprating/textures/font/rating_sword_1.png
src/main/resources/assets/pvprating/textures/font/rating_sword_10.png
```

Replace `rating_sword_1.png` through `rating_sword_10.png` to customize the visuals without changing Java code.

The display sync packet is server-to-client only. Clients can receive updated display values, but the real server-side rating is changed only by server-side death handling after a valid player kill.

### Spawn Kill Protection

`spawnKillProtectionSeconds` protects the rating from being farmed by repeatedly killing the same victim with the same killer.

- The protection is tracked per killer UUID and victim UUID pair.
- If the same killer kills the same victim again before the configured number of seconds has passed, the default PvP rating change is skipped.
- The killer does not gain rating and the victim does not lose rating while the protection is active.
- Blocked repeat kills refresh the stored time for that killer-victim pair.
- Set `spawnKillProtectionSeconds = 0` to disable this protection.

Example with `spawnKillProtectionSeconds = 300`:

1. PlayerA kills PlayerB: rating changes.
2. PlayerA kills PlayerB again after 60 seconds: rating does not change.
3. PlayerA kills PlayerB again after 301 seconds from the last stored kill time: rating changes.
4. PlayerC kills PlayerB: rating changes because this is a different killer-victim pair.

### Optional Towny Rating Protection

If PvPRating is running on a hybrid Forge/Bukkit server with Towny installed, the default PvP rating change can be skipped for social or political relationships managed by Towny.

The default settings skip rating changes when the killer and victim are:

- residents of the same town;
- residents of towns in the same nation;
- residents of towns in mutually allied nations;
- mutual Towny friends, meaning both players added each other with Towny resident friends.

Only the default PvP rating is skipped.

To configure this on a server, stop the server, open:

```text
world/serverconfig/pvprating-server.toml
```

Then change any of these options:

```toml
townyDisableRatingSameTown = true
townyDisableRatingSameNation = true
townyDisableRatingAlliedNations = true
townyDisableRatingMutualFriends = true
```

Set a value to `false` to allow rating changes for that relationship. For example, to allow rating changes between mutual Towny friends but keep the town and nation protections:

```toml
townyDisableRatingMutualFriends = false
```

If Towny is not installed or not loaded, these settings have no effect and PvPRating uses the normal rating logic.

When Towny skips a rating change, both players receive a translated chat message. The killer sees that rating did not change for the kill, and the victim sees that rating did not change for the death. The message includes the exact Towny reason: same town, same nation, allied nations, or mutual Towny friends.

Spawn-kill protection also sends translated messages to both players when it skips a rating change. The message includes how many seconds passed and how many seconds are required by `spawnKillProtectionSeconds`.

### Audit Logging

PvPRating writes security-oriented audit events to:

```text
logs/pvprating-audit.log
```

In the local development server in this repository, the path is:

```text
run/server/logs/pvprating-audit.log
```

The audit file uses JSON Lines: each line is one JSON object. This makes it easier to search, archive, or parse with scripts.

Logged events:

- valid PvP kills that apply, partially apply, or try to apply rating changes;
- skipped rating changes from spawn-kill protection and Towny protection;
- manual operator rating changes;
- operator freeze and unfreeze changes;
- successful PvPRating config changes made through commands.

Not logged:

- display sync packets;
- packet handling and client-side display updates;
- read-only PvPRating commands;
- non-player deaths, self-kills, non-survival kills, and other deaths that never reach the rating logic;
- player inventories, NBT data, IP addresses, or chat messages.

Log growth protection:

- `auditMaxFileSizeBytes` rotates `pvprating-audit.log` when it reaches the configured size;
- `auditMaxFiles` limits how many audit files are kept, including the current file;
- `auditRateLimitWindowSeconds` suppresses repeated skipped-kill entries for the same killer, victim, and reason during the configured window;
- suppressed skipped-kill entries are summarized with an `audit_suppressed` event;
- coordinates are excluded by default and only written when `auditIncludeCoordinates` is enabled.

### Player Commands

All players can view PvP rating leaderboards:

| Command | Description |
| --- | --- |
| `/pvprating` | Shows a short PvPRating description and points to the main lookup commands. |
| `/pvprating top [page]` | Shows the absolute PvP rating leaderboard for known online and offline players with rating above `0`. |
| `/pvprating top online [page]` | Shows only currently online players with rating above `0`. |
| `/pvprating get <player>` | Shows the known PvP rating for one player by nickname. |

Both leaderboard commands are paginated by 10 players per page. `/pvprating get <player>` uses PvPRating's known player data and updates online players before searching.

### Admin Commands

Operators with permission level 4 can manually manage one known online or offline player's rating at a time and update server config values from chat.

Rating management:

| Command | Description |
| --- | --- |
| `/pvprating set <player> <rating>` | Sets a known online or offline player's absolute PvP rating. |
| `/pvprating add <player> <amount>` | Adds rating to a known online or offline player. |
| `/pvprating increase <player> <amount>` | Alias for `add`. |
| `/pvprating remove <player> <amount>` | Subtracts rating from a known online or offline player. |
| `/pvprating subtract <player> <amount>` | Alias for `remove`. |
| `/pvprating freeze <player>` | Freezes the player's rating so valid PvP events cannot change it. |
| `/pvprating unfreeze <player>` | Allows valid PvP events to change the player's rating again. |

`<player>` suggestions use online players plus offline players already known to PvPRating. Offline manual edits require the player's `world/playerdata/<uuid>.dat` file to exist.

Rating system and rank display:

| Command | Description |
| --- | --- |
| `/pvprating system` | Shows whether the default PvP rating math is enabled. |
| `/pvprating system <true\|false>` | Enables or disables the default PvP rating math. |
| `/pvprating rank` | Shows rank scaling mode and configured manual thresholds. |
| `/pvprating rank absolute` | Shows whether absolute rank scaling is enabled. |
| `/pvprating rank absolute <true\|false>` | Enables absolute rank/icon scaling from the highest known rating, or disables it to use manual thresholds. |
| `/pvprating rank threshold` | Lists the rating thresholds for rank levels 1 through 10. |
| `/pvprating rank threshold <level> <rating>` | Sets the rating required for one rank level. Levels are `1` to `10`; thresholds must stay in ascending order. |

Display settings:

| Command | Description |
| --- | --- |
| `/pvprating display` | Shows display enabled state, cooldown, color, and text styles. |
| `/pvprating display <true\|false>` | Shows or hides the rating display in player names. |
| `/pvprating display cooldown` | Shows the display refresh cooldown in ticks. |
| `/pvprating display cooldown <ticks>` | Sets the display refresh cooldown. |
| `/pvprating display color` | Shows the RGB decimal color used for the rating value. |
| `/pvprating display color <rgbDecimal>` | Sets the RGB decimal color used for the rating value. |
| `/pvprating display style` | Shows enabled text styles. |
| `/pvprating display style bold <true\|false>` | Toggles bold style for the rating value. |
| `/pvprating display style italic <true\|false>` | Toggles italic style for the rating value. |
| `/pvprating display style underlined <true\|false>` | Toggles underlined style for the rating value. |
| `/pvprating display style strikethrough <true\|false>` | Toggles strikethrough style for the rating value. |

Spawn-kill protection and combat power:

| Command | Description |
| --- | --- |
| `/pvprating spawnkill` | Shows base cooldown, maximum escalated cooldown, and escalation multiplier. |
| `/pvprating spawnkill base` | Shows the base spawn-kill protection cooldown. |
| `/pvprating spawnkill base <seconds>` | Sets the base spawn-kill protection cooldown. `0` disables the protection. |
| `/pvprating spawnkill max-seconds` | Shows the maximum escalated spawn-kill cooldown. |
| `/pvprating spawnkill max-seconds <seconds>` | Sets the maximum escalated spawn-kill cooldown. |
| `/pvprating spawnkill multiplier` | Shows the cooldown escalation multiplier. |
| `/pvprating spawnkill multiplier <value>` | Sets how much blocked repeat kills multiply the pair cooldown. |
| `/pvprating combatpower` | Shows combat power settings used by spawn-kill protection and gain scaling. |
| `/pvprating combatpower enabled` | Shows whether combat power checks are enabled. |
| `/pvprating combatpower enabled <true\|false>` | Enables or disables combat power checks. |
| `/pvprating combatpower min-ready-armor` | Shows the armor value needed to count a victim as combat-ready. |
| `/pvprating combatpower min-ready-armor <value>` | Sets the armor value needed to count a victim as combat-ready. |
| `/pvprating combatpower min-ready-attack` | Shows the attack damage needed to count a victim as combat-ready. |
| `/pvprating combatpower min-ready-attack <value>` | Sets the attack damage needed to count a victim as combat-ready. |
| `/pvprating combatpower armor-weight` | Shows the armor weight in combat power calculation. |
| `/pvprating combatpower armor-weight <value>` | Sets the armor weight in combat power calculation. |
| `/pvprating combatpower toughness-weight` | Shows the armor toughness weight in combat power calculation. |
| `/pvprating combatpower toughness-weight <value>` | Sets the armor toughness weight in combat power calculation. |
| `/pvprating combatpower attack-weight` | Shows the attack damage weight in combat power calculation. |
| `/pvprating combatpower attack-weight <value>` | Sets the attack damage weight in combat power calculation. |
| `/pvprating combatpower min-gain-coefficient` | Shows the minimum rating-change coefficient when the victim has lower combat power. |
| `/pvprating combatpower min-gain-coefficient <value>` | Sets the minimum rating-change coefficient when the victim has lower combat power. |
| `/pvprating combatpower reverse-attempt-penalty` | Shows whether the comeback penalty for repeated blocked reverse-pair attempts is enabled. |
| `/pvprating combatpower reverse-attempt-penalty <true\|false>` | Toggles the comeback penalty for repeated blocked reverse-pair attempts. |

Formula settings:

| Command | Description |
| --- | --- |
| `/pvprating formula` | Shows all rating formula settings and a short summary of how the formula works. |
| `/pvprating formula gain` | Shows base rating gain on kill. |
| `/pvprating formula gain <value>` | Sets base rating gain on kill. |
| `/pvprating formula loss` | Shows base rating loss on death. |
| `/pvprating formula loss <value>` | Sets base rating loss on death. |
| `/pvprating formula killer-multiplier` | Shows the multiplier applied to the killer's current rating. |
| `/pvprating formula killer-multiplier <value>` | Sets the multiplier applied to the killer's current rating. |
| `/pvprating formula claim-multiplier` | Shows the multiplier applied to the victim's current rating and added to the killer gain. |
| `/pvprating formula claim-multiplier <value>` | Sets the multiplier applied to the victim's current rating and added to the killer gain. |
| `/pvprating formula target-multiplier` | Shows the multiplier applied to the victim's current rating and added to the victim loss. |
| `/pvprating formula target-multiplier <value>` | Sets the multiplier applied to the victim's current rating and added to the victim loss. |
| `/pvprating formula complete-loss` | Shows whether death sets rating from the complete-loss formula. |
| `/pvprating formula complete-loss <true\|false>` | Toggles the complete-loss victim formula. |
| `/pvprating formula prevent-negative` | Shows whether rating is clamped at zero. |
| `/pvprating formula prevent-negative <true\|false>` | Toggles the zero floor for rating changes. |

Towny protection:

| Command | Description |
| --- | --- |
| `/pvprating towny` | Shows all Towny relationship protections. |
| `/pvprating towny same-town` | Shows whether same-town kills are skipped. |
| `/pvprating towny same-town <true\|false>` | Toggles skipping rating changes for same-town kills. |
| `/pvprating towny same-nation` | Shows whether same-nation kills are skipped. |
| `/pvprating towny same-nation <true\|false>` | Toggles skipping rating changes for same-nation kills. |
| `/pvprating towny allied-nations` | Shows whether allied-nation kills are skipped. |
| `/pvprating towny allied-nations <true\|false>` | Toggles skipping rating changes for allied-nation kills. |
| `/pvprating towny mutual-friends` | Shows whether mutual Towny friend kills are skipped. |
| `/pvprating towny mutual-friends <true\|false>` | Toggles skipping rating changes for mutual Towny friends. |

Manual rating changes bypass the normal PvP formulas and immediately sync the display. Online changed players receive a chat message with the old and new rating.

Freezing a player stores `ratingFrozen = true` on that player. A frozen player keeps their rating during valid PvP kills and deaths, but their opponent can still gain or lose rating normally. The frozen player receives a chat message when a valid PvP event tries to change their rating, and also when an admin freezes or unfreezes them.

`/pvprating spawnkill base <seconds>` changes `spawnKillProtectionSeconds` immediately and saves the server config, so the value survives a server restart. Use `0` to disable spawn-kill protection. The command reports invalid input and config save failures to the admin in chat.

Config commands apply their value immediately and save the server config. Decimal values may be entered with either `.` or `,`; displayed decimal values are rounded to 4 digits. Boolean arguments suggest `true` and `false` through TAB completion. Display-related commands resync all online player displays after a successful change.

### Languages

PvPRating uses standard Minecraft language files for player-facing text. English is the default language, and Russian is included.

```text
src/main/resources/assets/pvprating/lang/en_us.json
src/main/resources/assets/pvprating/lang/ru_ru.json
```

To add another language later, add a new JSON file in the same folder using Minecraft's locale code, for example `de_de.json`, and copy the same translation keys from `en_us.json`.

### Main Config File

On a dedicated server, the generated config is stored in the world server config folder:

```text
world/serverconfig/pvprating-server.toml
```

In the local development server in this repository, the path is:

```text
run/server/world/serverconfig/pvprating-server.toml
```

Stop the server before editing the file, then restart the server after saving it.

### Main Config Options

| Option | Default | Effect |
| --- | --- | --- |
| `"Enable the warning in the server start"` | `true` | Sends an operator warning reminding the server owner to configure the mod. |
| `"Enable the default maths, disable the display to prevent the name change"` | `true` | Enables the default PvP rating system. |
| `"Enable the display"` | `true` | Shows the rating value in player name displays. |
| `"How much ticks before displays get updated"` | `100` | Cooldown between display updates. 20 ticks are about 1 second. |
| `"RGB Decimal color of the rating in chat"` | `16755200` | Decimal RGB color used for the rating value. |
| `"Make the color bold"` | `true` | Makes the displayed value bold. |
| `"Make the color italic"` | `false` | Makes the displayed value italic. |
| `"Underline the color"` | `false` | Underlines the displayed value. |
| `"Strikethrough the color"` | `false` | Strikes through the displayed value. |
| `"Text before the value"` | `" ["` | Text placed before the icon and displayed rating value. Existing generated values ending in `$` are supported and have the trailing `$` removed before the icon is inserted. |
| `"Text after the value"` | `"]"` | Text placed after the displayed rating value. |
| `rankAbsoluteSystem` | `false` | If enabled, rank words and sword icons scale from the highest known rating. If disabled, `rankThresholds` are used. |
| `rankThresholds` | `[0.0, 22.22..., ..., 200.0]` | Rating thresholds for rank levels 1 through 10. The default list is evenly distributed from `0` to `200`. |
| `"Loss the equivalent of your entire rating on death"` | `false` | Uses the complete-loss victim formula instead of subtracting from the current rating. |
| `preventNegativeRating` | `true` | Prevents the victim rating from going below `0`. |
| `spawnKillProtectionSeconds` | `60` | Minimum seconds between counted rating changes for the same killer-victim pair. `0` disables the protection. |
| `spawnKillProtectionMaxSeconds` | `43200` | Maximum escalated spawn-kill protection time for one killer-victim pair. |
| `spawnKillProtectionMultiplier` | `2.0` | Multiplier applied to a pair cooldown when the same killer repeats a blocked kill against the same victim. |
| `combatPowerEnabled` | `true` | Enables combat readiness checks based on armor, armor toughness, and attack damage. |
| `combatPowerMinReadyArmor` | `8.0` | Minimum armor points required for a victim to be considered combat-ready enough to bypass an active pair cooldown. |
| `combatPowerMinReadyAttackDamage` | `4.0` | Minimum attack damage required for a victim to be considered combat-ready enough to bypass an active pair cooldown. |
| `combatPowerArmorWeight` | `1.0` | Weight of armor points in combat power calculation. |
| `combatPowerToughnessWeight` | `1.0` | Weight of armor toughness in combat power calculation. |
| `combatPowerAttackDamageWeight` | `2.0` | Weight of attack damage in combat power calculation. |
| `combatPowerMinGainCoefficient` | `0.15` | Minimum rating-change coefficient when the victim has lower combat power than the killer. |
| `combatPowerReverseAttemptPenaltyEnabled` | `true` | Enables a comeback penalty when the opposite killer-victim pair has repeated blocked kills. |
| `townyDisableRatingSameTown` | `true` | If Towny is installed, skips default PvP rating changes when killer and victim are residents of the same town. |
| `townyDisableRatingSameNation` | `true` | If Towny is installed, skips default PvP rating changes when killer and victim are in the same nation. |
| `townyDisableRatingAlliedNations` | `true` | If Towny is installed, skips default PvP rating changes when killer and victim are in mutually allied nations. |
| `townyDisableRatingMutualFriends` | `true` | If Towny is installed, skips default PvP rating changes when killer and victim have added each other as Towny friends. |
| `auditLoggingEnabled` | `true` | Writes PvPRating security/audit events to `logs/pvprating-audit.log`. |
| `auditLogAppliedKills` | `true` | Logs valid PvP kills that apply, partially apply, or try to apply rating changes. |
| `auditLogSkippedKills` | `true` | Logs rating changes skipped by spawn-kill or Towny protection. |
| `auditLogAdminChanges` | `true` | Logs operator rating, freeze, and PvPRating config changes. |
| `auditIncludeCoordinates` | `false` | Adds player block coordinates and dimension to audit events. |
| `auditMaxFileSizeBytes` | `10485760` | Rotates the current audit file after it reaches this size. |
| `auditMaxFiles` | `5` | Maximum audit log files to keep, including the current file. |
| `auditRateLimitWindowSeconds` | `60` | Suppresses repeated skipped-kill audit entries for the same killer, victim, and reason during this window. `0` disables suppression. |
| `auditSuspiciousDeltaThreshold` | `1000.0` | Marks a single-kill rating delta as suspicious when its absolute value is at or above this threshold. `0` disables this flag. |
| `"Rating Minimum Value"` | `-Double.MAX_VALUE` | Legacy minimum value setting. Current default rating logic uses `preventNegativeRating` for the zero floor. |
| `"Rating Maximum Value"` | `Double.MAX_VALUE` | Legacy maximum value setting. |
| `"Rating Gain On Killing"` | `1.0` | Base rating gained by the killer. |
| `"Rating Loss On Death"` | `1.0` | Base rating lost by the victim. |
| `"Killer-Multiplier multiply from your own rating every time you kill someone 1 = 100%"` | `0.0` | Multiplies the killer's current rating and adds it to the killer rating gain. |
| `"Claim-Multiplier, how much rating you take from your victim 1 = 100%"` | `0.09` | Adds a percentage of the victim's rating to the killer rating gain. |
| `"Target-Multiplier, how much rating is subtracted when you get killed 1 = 100%"` | `0.1` | Applies an additional victim-side rating loss based on the victim's current rating. |

Existing generated configs keep their old values. Delete the generated `pvprating-server.toml` while the server is stopped if you want Forge to regenerate it with the new defaults.

Random rating options from older generated configs are ignored by current PvPRating versions.

## Русский

### PvP-события

Когда один игрок в survival-режиме убивает другого игрока в survival-режиме:

- убийца может получить очки рейтинга;
- жертва может потерять очки рейтинга;
- самоубийства, убийства не игроком и убийства вне survival-режима не меняют стандартный PvP-рейтинг.
- опциональная интеграция Towny может пропускать стандартные изменения рейтинга для убийств внутри одного города, одной нации, союзных наций и взаимных друзей.

### Очки Рейтинга

Очки рейтинга показывают PvP-рейтинг игрока. Это не награда за голову и не обещание выплаты. Значение нужно для оценки опасности игрока на PvP-сервере и для сравнения между игроками.

Значение хранится на игроке в persistent data под ключом `rating`. Число в отображении использует целую часть этого значения; пакет синхронизации также передаёт полный рейтинг и текущий известный максимум для расчёта иконки.

Базовая формула рейтинга:

- Убийца: `текущий рейтинг + gain + текущий рейтинг * killer multiplier + рейтинг жертвы * claim multiplier`
- Жертва: `текущий рейтинг - loss - текущий рейтинг * target multiplier`
- Если включена полная потеря: `рейтинг жертвы = -loss - текущий рейтинг * target multiplier`.
- Если включён `preventNegativeRating`, рейтинг жертвы после расчёта обрезается до `0`.

### Отображение Рейтинга

Стандартное отображение рейтинга добавляет слово ранга перед ником игрока и маленькую иконку меча перед числом рейтинга. Иконка рисуется через custom font glyph, поэтому использует тот же путь отображаемого имени игрока и видна над головой, в списке игроков и в других местах, где Minecraft показывает display name.

При наведении на иконку меча или на число рейтинга показывается подсказка `PvP Rating`.

В PvPRating есть 10 уровней ранга. Слово ранга и иконка меча синхронизированы, поэтому один и тот же уровень рейтинга всегда использует один и тот же ранг и одну и ту же иконку.

Названия рангов по умолчанию:

| Уровень | Русский | Английский |
| --- | --- | --- |
| 1 | Нейтрал | Neutral |
| 2 | Боец | Fighter |
| 3 | Воин | Warrior |
| 4 | Рыцарь | Knight |
| 5 | Ветеран | Veteran |
| 6 | Гладиатор | Gladiator |
| 7 | Элита | Elite |
| 8 | Чемпион | Champion |
| 9 | Легенда | Legend |
| 10 | Бессмертный | Immortal |

По умолчанию ранги используют ручные пороги из `rankThresholds`. Стандартные пороги равномерно распределены от рейтинга `0` до рейтинга `200`, поэтому десятый ранг начинается с `200`.

Если включить `rankAbsoluteSystem`, ранги и иконки будут использовать старое абсолютное масштабирование. Рейтинг `0` и отрицательные значения используют уровень 1. Самый высокий известный положительный рейтинг на сервере, включая оффлайн-игроков, уже известных PvPRating, использует уровень 10. Положительные значения между `0` и этим максимумом равномерно распределяются по доступным уровням, поэтому на небольшом сервере часть промежуточных уровней может естественно пропускаться.

Ресурсы иконок лежат здесь:

```text
src/main/resources/assets/pvprating/font/rating_icons.json
src/main/resources/assets/pvprating/textures/font/rating_sword_1.png
src/main/resources/assets/pvprating/textures/font/rating_sword_10.png
```

Чтобы изменить внешний вид без правки Java-кода, замени файлы `rating_sword_1.png` - `rating_sword_10.png`.

Пакет синхронизации отображения работает только от сервера к клиенту. Клиент может получить новое отображаемое значение, но реальный server-side рейтинг меняется только серверной обработкой смерти после валидного убийства игрока.

### Защита От Spawn Kill

`spawnKillProtectionSeconds` защищает рейтинг от фарма через повторные убийства одной и той же жертвы одним и тем же убийцей.

- Защита хранится отдельно для каждой пары UUID убийцы и UUID жертвы.
- Если тот же убийца убивает ту же жертву раньше, чем прошло указанное число секунд, стандартное изменение PvP-рейтинга пропускается, если только включённая проверка боеспособности не признаёт жертву готовой к бою.
- Убийца не получает рейтинг, а жертва не теряет рейтинг, пока защита активна.
- Заблокированное повторное убийство обновляет сохранённое время для этой пары убийца-жертва.
- Установи `spawnKillProtectionSeconds = 0`, чтобы отключить защиту.

Пример при `spawnKillProtectionSeconds = 300`:

1. PlayerA убивает PlayerB: рейтинг меняется.
2. PlayerA снова убивает PlayerB через 60 секунд: рейтинг не меняется.
3. PlayerA снова убивает PlayerB через 301 секунду от последнего сохранённого времени: рейтинг меняется.
4. PlayerC убивает PlayerB: рейтинг меняется, потому что это другая пара убийца-жертва.

### Оценка Боеспособности

Если `combatPowerEnabled = true`, PvPRating оценивает экипировку убийцы и жертвы перед применением spawn-kill защиты и расчётом изменения рейтинга. Эта оценка решает, блокировать ли повторное убийство активным кулдауном пары, и каким коэффициентом умножить прирост убийцы и потерю жертвы.

Боеспособность игрока считается по текущим атрибутам на момент смерти:

```text
боеспособность = armor * combatPowerArmorWeight
  + armorToughness * combatPowerToughnessWeight
  + attackDamage * combatPowerAttackDamageWeight
```

Где `armor` берётся из текущего значения брони игрока, `armorToughness` - из атрибута твёрдости брони, а `attackDamage` - из атрибута урона атаки. Итоговое значение не может быть ниже `0`.

Жертва считается боеготовой, если одновременно выполнены два условия:

- её броня не меньше `combatPowerMinReadyArmor`;
- её урон атаки не меньше `combatPowerMinReadyAttackDamage`.

Боеготовность используется только для активного кулдауна пары убийца-жертва. Если тот же убийца убивает ту же жертву раньше требуемого времени, но жертва сейчас боеготова, убийство засчитывается, рейтинг меняется, кулдаун пары сбрасывается на базовое `spawnKillProtectionSeconds`, а штрафы противоположной пары очищаются. Если жертва не боеготова, изменение рейтинга пропускается, текущее время пары обновляется, а следующий требуемый кулдаун увеличивается через `spawnKillProtectionMultiplier`, но не выше `spawnKillProtectionMaxSeconds`.

Изменение рейтинга масштабируется отдельно. Сначала обычная формула считает базовый прирост убийцы:

```text
gain + killerRating * killerMultiplier + victimRating * claimMultiplier
```

Затем этот прирост и потеря рейтинга жертвы умножаются на коэффициент боеспособности. Если боеспособность жертвы не ниже боеспособности убийцы, коэффициент равен `1.0`. Если жертва слабее убийцы, коэффициент равен `victimCombatPower / killerCombatPower`, но не ниже `combatPowerMinGainCoefficient`. Например, при боеспособности убийцы `40`, боеспособности жертвы `10` и `combatPowerMinGainCoefficient = 0.15` убийца получит `25%` обычного прироста, а жертва потеряет `25%` обычной потери. При боеспособности жертвы `0` оба изменения всё равно применяются минимум на `15%`.

Если `combatPowerReverseAttemptPenaltyEnabled = true`, применяется дополнительный штраф за обратную пару. Он срабатывает, когда игрок получает засчитываемое убийство против того, кто до этого имел повторные заблокированные убийства в обратном направлении. Коэффициент равен `1 / (violations + 1)`, где `violations` - число подряд заблокированных повторных убийств обратной пары, и тоже не опускается ниже `combatPowerMinGainCoefficient`. Этот штраф умножается на обычный коэффициент боеспособности, а итоговый коэффициент изменения рейтинга ограничивается диапазоном от `0` до `1`.

Если `combatPowerEnabled = false`, вся эта механика отключается: повторные убийства внутри spawn-kill кулдауна блокируются только по времени, боеготовность не проверяется, а изменения рейтинга не режутся по разнице экипировки.

### Опциональная Towny-Защита Рейтинга

Если PvPRating работает на hybrid Forge/Bukkit сервере с установленным Towny, стандартное изменение PvP-рейтинга может быть пропущено для социальных и политических отношений, которыми управляет Towny.

Стандартные настройки пропускают изменение рейтинга, когда убийца и жертва:

- жители одного города;
- жители городов в одной нации;
- жители городов во взаимно союзных нациях;
- взаимные друзья Towny, то есть оба игрока добавили друг друга в друзья через Towny resident friends.

Пропускается только стандартный PvP-рейтинг.

Чтобы настроить это на сервере, останови сервер и открой:

```text
world/serverconfig/pvprating-server.toml
```

Затем измени нужные параметры:

```toml
townyDisableRatingSameTown = true
townyDisableRatingSameNation = true
townyDisableRatingAlliedNations = true
townyDisableRatingMutualFriends = true
```

Поставь значение `false`, чтобы разрешить изменение рейтинга для конкретного отношения. Например, чтобы разрешить изменения между взаимными друзьями Towny, но оставить защиту города и нации:

```toml
townyDisableRatingMutualFriends = false
```

Если Towny не установлен или не загружен, эти настройки ни на что не влияют и PvPRating использует обычную логику рейтинга.

Когда Towny блокирует изменение рейтинга, оба игрока получают переведённое сообщение. Убийца видит, что рейтинг не изменился за убийство, жертва видит, что рейтинг не изменился за смерть. В сообщении указывается точная причина Towny: один город, одна нация, союзные нации или взаимные друзья Towny.

Защита от spawn kill тоже отправляет переведённые сообщения обоим игрокам, когда пропускает изменение рейтинга. В сообщении указано, сколько секунд прошло и сколько секунд требуется по настройке `spawnKillProtectionSeconds`.

### Аудит Логов

PvPRating пишет security/audit-события в:

```text
logs/pvprating-audit.log
```

На локальном dev-сервере этого репозитория путь такой:

```text
run/server/logs/pvprating-audit.log
```

Файл аудита использует JSON Lines: каждая строка является отдельным JSON-объектом. Такой формат удобно искать, архивировать и разбирать скриптами.

Логируются:

- валидные PvP-убийства, которые применяют, частично применяют или пытаются применить изменение рейтинга;
- изменения рейтинга, пропущенные защитой от spawn kill или Towny;
- ручные изменения рейтинга оператором;
- заморозка и разморозка рейтинга оператором;
- успешные изменения настроек PvPRating через команды.

Не логируются:

- пакеты синхронизации отображения;
- обработка пакетов и client-side обновления display name;
- read-only команды PvPRating;
- смерти не от игрока, самоубийства, убийства вне survival-режима и другие смерти, которые не доходят до логики рейтинга;
- инвентари, NBT-данные, IP-адреса и сообщения чата.

Защита от разрастания логов:

- `auditMaxFileSizeBytes` ротирует `pvprating-audit.log`, когда файл достигает настроенного размера;
- `auditMaxFiles` ограничивает количество файлов аудита, включая текущий файл;
- `auditRateLimitWindowSeconds` подавляет повторяющиеся записи о пропущенных убийствах для той же пары убийца-жертва и той же причины в течение настроенного окна;
- подавленные записи суммируются событием `audit_suppressed`;
- координаты по умолчанию не пишутся и добавляются только при включённом `auditIncludeCoordinates`.

### Языки

PvPRating использует стандартные языковые файлы Minecraft для текста, который видят игроки. Английский язык используется по умолчанию, русский уже добавлен.

```text
src/main/resources/assets/pvprating/lang/en_us.json
src/main/resources/assets/pvprating/lang/ru_ru.json
```

Если позже понадобится добавить другой язык, добавь новый JSON-файл в эту же папку с кодом локали Minecraft, например `de_de.json`, и скопируй в него те же ключи перевода из `en_us.json`.

### Команды Игроков

Все игроки могут смотреть топ PvP-рейтинга:

| Команда | Описание |
| --- | --- |
| `/pvprating` | Показывает краткое описание PvPRating и основные команды просмотра. |
| `/pvprating top [page]` | Показывает абсолютный топ PvP-рейтинга для известных онлайн- и оффлайн-игроков с рейтингом выше `0`. |
| `/pvprating top online [page]` | Показывает только игроков, которые сейчас онлайн и имеют рейтинг выше `0`. |
| `/pvprating get <player>` | Показывает известный PvP-рейтинг одного игрока по нику. |

Обе команды топа выводят по 10 игроков на страницу. `/pvprating get <player>` использует известные PvPRating данные игроков и обновляет онлайн-игроков перед поиском.

### Админские Команды

Операторы с уровнем прав 4 могут вручную управлять рейтингом одного известного онлайн- или оффлайн-игрока за раз и менять настройки сервера из чата.

Управление рейтингом:

| Команда | Описание |
| --- | --- |
| `/pvprating set <player> <rating>` | Устанавливает абсолютный PvP-рейтинг известного онлайн- или оффлайн-игрока. |
| `/pvprating add <player> <amount>` | Добавляет рейтинг известному онлайн- или оффлайн-игроку. |
| `/pvprating increase <player> <amount>` | Алиас команды `add`. |
| `/pvprating remove <player> <amount>` | Вычитает рейтинг у известного онлайн- или оффлайн-игрока. |
| `/pvprating subtract <player> <amount>` | Алиас команды `remove`. |
| `/pvprating freeze <player>` | Замораживает рейтинг игрока, чтобы валидные PvP-события его не меняли. |
| `/pvprating unfreeze <player>` | Снова разрешает валидным PvP-событиям менять рейтинг игрока. |

Подсказки для `<player>` используют онлайн-игроков и оффлайн-игроков, уже известных PvPRating. Для ручного изменения оффлайн-игрока должен существовать его файл `world/playerdata/<uuid>.dat`.

Система рейтинга и ранги:

| Команда | Описание |
| --- | --- |
| `/pvprating system` | Показывает, включена ли стандартная математика PvP-рейтинга. |
| `/pvprating system <true\|false>` | Включает или отключает стандартную математику PvP-рейтинга. |
| `/pvprating rank` | Показывает режим расчёта рангов и ручные пороги. |
| `/pvprating rank absolute` | Показывает, включено ли абсолютное масштабирование рангов. |
| `/pvprating rank absolute <true\|false>` | Включает масштабирование рангов/иконок от максимального известного рейтинга или отключает его для ручных порогов. |
| `/pvprating rank threshold` | Показывает пороги рейтинга для уровней ранга 1-10. |
| `/pvprating rank threshold <level> <rating>` | Задаёт рейтинг, нужный для одного уровня ранга. Уровни: `1`-`10`; пороги должны идти по возрастанию. |

Настройки отображения:

| Команда | Описание |
| --- | --- |
| `/pvprating display` | Показывает состояние отображения, кулдаун, цвет и стили текста. |
| `/pvprating display <true\|false>` | Показывает или скрывает отображение рейтинга в именах игроков. |
| `/pvprating display cooldown` | Показывает кулдаун обновления отображения в тиках. |
| `/pvprating display cooldown <ticks>` | Задаёт кулдаун обновления отображения. |
| `/pvprating display color` | Показывает RGB decimal цвет значения рейтинга. |
| `/pvprating display color <rgbDecimal>` | Задаёт RGB decimal цвет значения рейтинга. |
| `/pvprating display style` | Показывает включённые стили текста. |
| `/pvprating display style bold <true\|false>` | Включает или отключает жирный стиль значения рейтинга. |
| `/pvprating display style italic <true\|false>` | Включает или отключает курсив значения рейтинга. |
| `/pvprating display style underlined <true\|false>` | Включает или отключает подчёркивание значения рейтинга. |
| `/pvprating display style strikethrough <true\|false>` | Включает или отключает зачёркивание значения рейтинга. |

Защита от spawn kill и боеспособность:

| Команда | Описание |
| --- | --- |
| `/pvprating spawnkill` | Показывает базовый кулдаун, максимальный усиленный кулдаун и множитель роста. |
| `/pvprating spawnkill base` | Показывает базовый кулдаун защиты от spawn kill. |
| `/pvprating spawnkill base <seconds>` | Задаёт базовый кулдаун защиты от spawn kill. `0` отключает защиту. |
| `/pvprating spawnkill max-seconds` | Показывает максимальный усиленный кулдаун защиты от spawn kill. |
| `/pvprating spawnkill max-seconds <seconds>` | Задаёт максимальный усиленный кулдаун защиты от spawn kill. |
| `/pvprating spawnkill multiplier` | Показывает множитель роста кулдауна. |
| `/pvprating spawnkill multiplier <value>` | Задаёт, во сколько раз заблокированные повторные убийства увеличивают кулдаун пары. |
| `/pvprating combatpower` | Показывает настройки боеспособности, которые используются защитой и масштабированием прироста. |
| `/pvprating combatpower enabled` | Показывает, включены ли проверки боеспособности. |
| `/pvprating combatpower enabled <true\|false>` | Включает или отключает проверки боеспособности. |
| `/pvprating combatpower min-ready-armor` | Показывает значение брони, нужное для признания жертвы боеспособной. |
| `/pvprating combatpower min-ready-armor <value>` | Задаёт значение брони, нужное для признания жертвы боеспособной. |
| `/pvprating combatpower min-ready-attack` | Показывает урон атаки, нужный для признания жертвы боеспособной. |
| `/pvprating combatpower min-ready-attack <value>` | Задаёт урон атаки, нужный для признания жертвы боеспособной. |
| `/pvprating combatpower armor-weight` | Показывает вес брони в расчёте боеспособности. |
| `/pvprating combatpower armor-weight <value>` | Задаёт вес брони в расчёте боеспособности. |
| `/pvprating combatpower toughness-weight` | Показывает вес твёрдости брони в расчёте боеспособности. |
| `/pvprating combatpower toughness-weight <value>` | Задаёт вес твёрдости брони в расчёте боеспособности. |
| `/pvprating combatpower attack-weight` | Показывает вес урона атаки в расчёте боеспособности. |
| `/pvprating combatpower attack-weight <value>` | Задаёт вес урона атаки в расчёте боеспособности. |
| `/pvprating combatpower min-gain-coefficient` | Показывает минимальный коэффициент изменения рейтинга, когда у жертвы меньше боеспособность. |
| `/pvprating combatpower min-gain-coefficient <value>` | Задаёт минимальный коэффициент изменения рейтинга, когда у жертвы меньше боеспособность. |
| `/pvprating combatpower reverse-attempt-penalty` | Показывает, включён ли штраф за обратный камбэк после повторных заблокированных попыток. |
| `/pvprating combatpower reverse-attempt-penalty <true\|false>` | Включает или отключает штраф за обратный камбэк после повторных заблокированных попыток. |

Настройки формулы:

| Команда | Описание |
| --- | --- |
| `/pvprating formula` | Показывает все настройки формулы рейтинга и краткое описание её работы. |
| `/pvprating formula gain` | Показывает базовый прирост рейтинга за убийство. |
| `/pvprating formula gain <value>` | Задаёт базовый прирост рейтинга за убийство. |
| `/pvprating formula loss` | Показывает базовую потерю рейтинга при смерти. |
| `/pvprating formula loss <value>` | Задаёт базовую потерю рейтинга при смерти. |
| `/pvprating formula killer-multiplier` | Показывает множитель текущего рейтинга убийцы. |
| `/pvprating formula killer-multiplier <value>` | Задаёт множитель текущего рейтинга убийцы. |
| `/pvprating formula claim-multiplier` | Показывает множитель рейтинга жертвы, который добавляется к приросту убийцы. |
| `/pvprating formula claim-multiplier <value>` | Задаёт множитель рейтинга жертвы, который добавляется к приросту убийцы. |
| `/pvprating formula target-multiplier` | Показывает множитель рейтинга жертвы, который добавляется к потере жертвы. |
| `/pvprating formula target-multiplier <value>` | Задаёт множитель рейтинга жертвы, который добавляется к потере жертвы. |
| `/pvprating formula complete-loss` | Показывает, используется ли формула полной потери при смерти. |
| `/pvprating formula complete-loss <true\|false>` | Включает или отключает формулу полной потери для жертвы. |
| `/pvprating formula prevent-negative` | Показывает, ограничивается ли рейтинг нулём снизу. |
| `/pvprating formula prevent-negative <true\|false>` | Включает или отключает нижнюю границу рейтинга `0`. |

Защита Towny:

| Команда | Описание |
| --- | --- |
| `/pvprating towny` | Показывает все защиты по отношениям Towny. |
| `/pvprating towny same-town` | Показывает, пропускаются ли изменения рейтинга для убийств в одном городе. |
| `/pvprating towny same-town <true\|false>` | Включает или отключает пропуск изменения рейтинга для убийств в одном городе. |
| `/pvprating towny same-nation` | Показывает, пропускаются ли изменения рейтинга для убийств в одной нации. |
| `/pvprating towny same-nation <true\|false>` | Включает или отключает пропуск изменения рейтинга для убийств в одной нации. |
| `/pvprating towny allied-nations` | Показывает, пропускаются ли изменения рейтинга для убийств в союзных нациях. |
| `/pvprating towny allied-nations <true\|false>` | Включает или отключает пропуск изменения рейтинга для убийств в союзных нациях. |
| `/pvprating towny mutual-friends` | Показывает, пропускаются ли изменения рейтинга для взаимных друзей Towny. |
| `/pvprating towny mutual-friends <true\|false>` | Включает или отключает пропуск изменения рейтинга для взаимных друзей Towny. |

Ручное изменение рейтинга обходит обычные PvP-формулы и сразу синхронизирует отображение. Онлайн-игрок получает сообщение в чат со старым и новым рейтингом.

Заморозка игрока сохраняет `ratingFrozen = true` на этом игроке. У замороженного игрока рейтинг не меняется при валидных PvP-убийствах и смертях, но рейтинг его оппонента всё равно может измениться обычным образом. Замороженный игрок получает сообщение, когда валидное PvP-событие пытается изменить его рейтинг, а также когда админ замораживает или размораживает рейтинг.

`/pvprating spawnkill base <seconds>` сразу меняет `spawnKillProtectionSeconds` и сохраняет серверный конфиг, поэтому значение переживает перезапуск сервера. Используйте `0`, чтобы отключить защиту от spawn kill. Команда сообщает админу в чат о неверном вводе и ошибках сохранения конфига.

Команды настройки применяют значение сразу и сохраняют серверный конфиг. Дробные значения можно вводить как через `.`, так и через `,`; выводимые дробные значения округляются до 4 знаков. Булевые аргументы подсказывают `true` и `false` через TAB. Команды, связанные с отображением, после успешного изменения синхронизируют отображение всем игрокам онлайн.

### Основной Config-Файл

На dedicated server сгенерированный конфиг лежит в папке server config мира:

```text
world/serverconfig/pvprating-server.toml
```

На локальном dev-сервере этого репозитория путь такой:

```text
run/server/world/serverconfig/pvprating-server.toml
```

Перед редактированием останови сервер, после сохранения запусти его заново.

### Основные Настройки Конфига

| Настройка | По умолчанию | Что делает |
| --- | --- | --- |
| `"Enable the warning in the server start"` | `true` | Отправляет оператору предупреждение, что мод нужно настроить через server config. |
| `"Enable the default maths, disable the display to prevent the name change"` | `true` | Включает стандартную систему PvP-рейтинга. |
| `"Enable the display"` | `true` | Показывает рейтинг в отображаемом имени игрока. |
| `"How much ticks before displays get updated"` | `100` | Кулдаун между обновлениями отображения. 20 тиков примерно равны 1 секунде. |
| `"RGB Decimal color of the rating in chat"` | `16755200` | Decimal RGB цвет для значения рейтинга. |
| `"Make the color bold"` | `true` | Делает отображаемое значение жирным. |
| `"Make the color italic"` | `false` | Делает отображаемое значение курсивом. |
| `"Underline the color"` | `false` | Подчёркивает отображаемое значение. |
| `"Strikethrough the color"` | `false` | Зачёркивает отображаемое значение. |
| `"Text before the value"` | `" ["` | Текст перед иконкой и значением рейтинга. Уже сгенерированные значения, заканчивающиеся на `$`, поддерживаются: завершающий `$` удаляется перед вставкой иконки. |
| `"Text after the value"` | `"]"` | Текст после значения рейтинга. |
| `rankAbsoluteSystem` | `false` | Если включено, слова рангов и иконки мечей масштабируются от максимального известного рейтинга. Если отключено, используются `rankThresholds`. |
| `rankThresholds` | `[0.0, 22.22..., ..., 200.0]` | Пороги рейтинга для уровней ранга 1-10. Стандартный список равномерно распределён от `0` до `200`. |
| `"Loss the equivalent of your entire rating on death"` | `false` | Использует формулу полной потери жертвы вместо обычного вычитания из текущего рейтинга. |
| `preventNegativeRating` | `true` | Не даёт рейтингу жертвы уйти ниже `0`. |
| `spawnKillProtectionSeconds` | `60` | Минимальное число секунд между засчитываемыми изменениями рейтинга для одной пары убийца-жертва. `0` отключает защиту. |
| `spawnKillProtectionMaxSeconds` | `43200` | Максимальное усиленное время защиты от spawn kill для одной пары убийца-жертва. |
| `spawnKillProtectionMultiplier` | `2.0` | Множитель, который применяется к кулдауну пары, когда тот же убийца повторяет заблокированное убийство той же жертвы. |
| `combatPowerEnabled` | `true` | Включает проверки боеспособности по броне, твёрдости брони и урону атаки. |
| `combatPowerMinReadyArmor` | `8.0` | Минимальное значение брони, при котором жертва считается достаточно боеспособной для обхода активного кулдауна пары. |
| `combatPowerMinReadyAttackDamage` | `4.0` | Минимальный урон атаки, при котором жертва считается достаточно боеспособной для обхода активного кулдауна пары. |
| `combatPowerArmorWeight` | `1.0` | Вес брони в расчёте боеспособности. |
| `combatPowerToughnessWeight` | `1.0` | Вес твёрдости брони в расчёте боеспособности. |
| `combatPowerAttackDamageWeight` | `2.0` | Вес урона атаки в расчёте боеспособности. |
| `combatPowerMinGainCoefficient` | `0.15` | Минимальный коэффициент изменения рейтинга, когда у жертвы ниже боеспособность. |
| `combatPowerReverseAttemptPenaltyEnabled` | `true` | Включает штраф за камбэк, когда противоположная пара убийца-жертва имеет повторные заблокированные убийства. |
| `townyDisableRatingSameTown` | `true` | Если Towny установлен, пропускает стандартные изменения PvP-рейтинга, когда убийца и жертва являются жителями одного города. |
| `townyDisableRatingSameNation` | `true` | Если Towny установлен, пропускает стандартные изменения PvP-рейтинга, когда убийца и жертва находятся в одной нации. |
| `townyDisableRatingAlliedNations` | `true` | Если Towny установлен, пропускает стандартные изменения PvP-рейтинга, когда убийца и жертва находятся во взаимно союзных нациях. |
| `townyDisableRatingMutualFriends` | `true` | Если Towny установлен, пропускает стандартные изменения PvP-рейтинга, когда убийца и жертва взаимно добавлены в друзья Towny. |
| `auditLoggingEnabled` | `true` | Пишет security/audit-события PvPRating в `logs/pvprating-audit.log`. |
| `auditLogAppliedKills` | `true` | Логирует валидные PvP-убийства, которые применяют, частично применяют или пытаются применить изменение рейтинга. |
| `auditLogSkippedKills` | `true` | Логирует изменения рейтинга, пропущенные защитой от spawn kill или Towny. |
| `auditLogAdminChanges` | `true` | Логирует изменения рейтинга, freeze/unfreeze и настройки PvPRating, сделанные оператором. |
| `auditIncludeCoordinates` | `false` | Добавляет block coordinates и dimension игроков в audit-события. |
| `auditMaxFileSizeBytes` | `10485760` | Ротирует текущий audit-файл после достижения этого размера. |
| `auditMaxFiles` | `5` | Максимальное число audit-файлов, включая текущий файл. |
| `auditRateLimitWindowSeconds` | `60` | Подавляет повторяющиеся audit-записи о пропущенных убийствах для той же пары убийца-жертва и той же причины в течение этого окна. `0` отключает подавление. |
| `auditSuspiciousDeltaThreshold` | `1000.0` | Помечает изменение рейтинга за одно убийство как suspicious, если абсолютное значение delta не меньше этого порога. `0` отключает этот флаг. |
| `"Rating Minimum Value"` | `-Double.MAX_VALUE` | Legacy-настройка минимального значения. В текущей стандартной логике нижнюю границу `0` задаёт `preventNegativeRating`. |
| `"Rating Maximum Value"` | `Double.MAX_VALUE` | Legacy-настройка максимального значения. |
| `"Rating Gain On Killing"` | `1.0` | Базовый рейтинг, который получает убийца. |
| `"Rating Loss On Death"` | `1.0` | Базовый рейтинг, который теряет жертва. |
| `"Killer-Multiplier multiply from your own rating every time you kill someone 1 = 100%"` | `0.0` | Умножает текущий рейтинг убийцы и добавляет результат к приросту рейтинга убийцы. |
| `"Claim-Multiplier, how much rating you take from your victim 1 = 100%"` | `0.09` | Добавляет к приросту рейтинга убийцы процент от рейтинга жертвы. |
| `"Target-Multiplier, how much rating is subtracted when you get killed 1 = 100%"` | `0.1` | Добавляет жертве дополнительную потерю на основе её текущего рейтинга. |

Уже сгенерированные конфиги сохраняют старые значения. Чтобы Forge пересоздал `pvprating-server.toml` с новыми значениями по умолчанию, останови сервер и удали этот файл.

Случайные параметры рейтинга из старых сгенерированных конфигов игнорируются текущими версиями PvPRating.
