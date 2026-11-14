# Backpack Arena — Minecraft-режим (Paper 1.21.x)

Автобаттлер с рюкзаком-инвентарём: выбираете героя, покупаете предметы, раскладываете их
в рюкзаке 6×9, объединяете рецепты — и смотрите автоматический бой. ТЗ: [docs/TZ.md](docs/TZ.md).

## Сборка

Нужна Java 21. Gradle скачивать не нужно — в проекте есть wrapper.

```bat
build.bat
```

Готовый плагин: `dist/BackpackArena-0.1.0.jar`. `build.bat` нужен потому, что Gradle на Windows
не запускает тесты, если в пути к домашней папке есть кириллица (`C:\Users\матвей`): скрипт
переносит кэш Gradle в `C:\Users\Public\gradle-home`.

Версия Paper задаётся в `build.gradle.kts` (`paperVersion`, сейчас `1.21.4-R0.1-SNAPSHOT`).

## Тестовый сервер

1. Скачайте Paper 1.21.4 с https://papermc.io/downloads/paper в отдельную папку.
2. Запустите `java -jar paper-1.21.4-*.jar --nogui`, примите EULA в `eula.txt`, запустите снова.
3. Положите `dist/BackpackArena-0.1.0.jar` в `plugins/` и перезапустите сервер.
4. В игре: `/ba play` — тренировка против бота, `/ba duel` — дуэль (нужны два игрока).

Админ: `/ba setarena a` и `/ba setarena b` — точки появления игроков; `/ba reload` — перечитать конфиги.

## Как играть

| Где | Действие |
|---|---|
| Магазин | ЛКМ — купить, ПКМ — резерв, подсолнух — обновить, наковальня — ускоренный крафт |
| Рюкзак | ЛКМ — взять/положить (клетка = левый верхний угол), ПКМ — повернуть, Shift+ЛКМ — запрет крафта |
| Хранилище (инвентарь игрока) | ЛКМ — взять в руку / вернуть, Shift+ЛКМ — продать |
| Хотбар | рука · повернуть · продать · магазин · рюкзак · … · в бой · выйти |

Сумки ставятся на пустые (чёрные) клетки, предметы — только на клетки сумок.
Рецепты срабатывают после боя, если ингредиенты лежат рядом и касаются сторонами.

## Структура

```
src/main/java/dev/backpackarena/
  core/       логика без Bukkit — тестируется JUnit
    model/      Rarity, Shape, ItemDef, HeroDef, EffectDef, AbilityDef, RelicDef, RecipeDef
    grid/       BackpackGrid (6×9, сумки, поворот, соседи)
    economy/    Shop, EconomyConfig (предложения, реролл, продажа, доход)
    crafting/   CraftingEngine (связные группы ингредиентов)
    combat/     CombatSimulator (детерминированный автобой), CombatConfig
    run/        RunState (золото, жизни, раунды, реликвии)
    bot/        BotFactory (соперник для тренировки)
    content/    ContentLoader (YAML → модели + проверка ссылок)
  plugin/     слой Paper
    match/      MatchService (тренировка, дуэль, награды), BattleReplayer (босс-бары, звуки)
    gui/        HeroMenu, ShopMenu, BackpackMenu, RelicMenu, Controls, ItemRenderer
    session/    PlayerSession, InventorySnapshot (инвентарь возвращается после матча)
    storage/    Database (SQLite: профили, история матчей)
    command/    /ba
src/main/resources/   heroes.yml items.yml recipes.yml relics.yml economy.yml combat.yml config.yml
```

## Как добавить контент

Всё задаётся в YAML внутри `plugins/BackpackArena/` (после первого запуска) — код менять не нужно:

* **Предмет** — новый блок в `items.yml` (форма строками `X`, эффекты триггер → действие).
* **Рецепт** — `recipes.yml`: список ингредиентов и результат.
* **Герой** — `heroes.yml`; способность выбирается из `IRON_GUARD`, `ELEMENT_SWITCH`, `SWARM`, `VOLLEY`.
  Новый тип способности — новая ветка в `CombatSimulator.useAbility`.
* **Баланс** — `economy.yml` (доход, рерролы, шансы редкости), `combat.yml` (статусы, лимиты).

`/ba reload` проверяет все файлы и, если есть ошибки, выводит их списком и оставляет старые настройки.

## Статус

* Ядро покрыто тестами (22 теста: сетка, крафт, магазин, экономика, бой, бот, полный забег).
* Плагин собирается под Paper API 1.21.4. Все иконки в YAML проверены по списку Material этой версии.
* На живом сервере режим ещё не запускался — следующий шаг: пройти тренировку и дуэль руками.

Следующие этапы ТЗ: подсказки рецептов (белый/жёлтый/зелёный), улучшения предметов 1–5,
сохранение сборок, модели героев и анимации боя на арене, рейтинг.
