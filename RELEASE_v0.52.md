# PvPRating v0.52

Релиз для Minecraft 1.20.1 / Forge 47.x.

## Главное

- Добавлены 10 PvP-рангов с локализацией EN/RU: Neutral/Нейтрал, Fighter/Боец, Warrior/Воин, Knight/Рыцарь, Veteran/Ветеран, Gladiator/Гладиатор, Elite/Элита, Champion/Чемпион, Legend/Легенда, Immortal/Бессмертный.
- Ранги и иконки мечей теперь можно считать по ручным порогам `rankThresholds`; старое масштабирование от максимального известного рейтинга доступно через `rankAbsoluteSystem`.
- Добавлена система combat power: рейтинг за убийство и потеря жертвы масштабируются по разнице экипировки, брони, твёрдости брони и урона атаки.
- Spawn-kill защита теперь может эскалировать время кулдауна для повторных убийств одной пары и учитывать боеготовность жертвы.
- Админские команды `/pvprating` расширены командами для рангов, combat power, spawn-kill защиты, формулы рейтинга, отображения и Towny-защит.
- Ручные изменения рейтинга теперь работают с известными оффлайн-игроками, если в мире есть их `playerdata`.
- Локальные dev/prod-like скрипты обновлены для тестирования на Arclight и PortableMC клиентах.

## Новые настройки

- `rankAbsoluteSystem`
- `rankThresholds`
- `spawnKillProtectionMaxSeconds`
- `spawnKillProtectionMultiplier`
- `combatPowerEnabled`
- `combatPowerMinReadyArmor`
- `combatPowerMinReadyAttackDamage`
- `combatPowerArmorWeight`
- `combatPowerToughnessWeight`
- `combatPowerAttackDamageWeight`
- `combatPowerMinGainCoefficient`
- `combatPowerReverseAttemptPenaltyEnabled`

## Команды

Добавлены или расширены группы команд:

- `/pvprating rank ...`
- `/pvprating combatpower ...`
- `/pvprating spawnkill ...`
- `/pvprating formula ...`
- `/pvprating display ...`
- `/pvprating towny ...`

Также улучшены сообщения ошибок и подсказки команд. Дробные числа в командах можно вводить как через точку, так и через запятую.

## Важно при обновлении

- Уже сгенерированный `pvprating-server.toml` сохранит старые значения. Чтобы получить новые значения по умолчанию, остановите сервер и удалите старый `pvprating-server.toml`, после чего Forge создаст новый файл.
- Если конфиг сохраняется вручную, добавьте новые параметры из списка выше или проверьте их через команды `/pvprating rank`, `/pvprating combatpower` и `/pvprating spawnkill`.
- Combat power включён по умолчанию. Если нужна старая логика без оценки экипировки, установите `combatPowerEnabled = false`.

## Артефакт

Загрузите jar из `build/libs` после сборки:

```text
pvprating-0.52-&-1.20.1.jar
```
