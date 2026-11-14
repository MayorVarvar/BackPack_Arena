package dev.backpackarena.core.content;

import dev.backpackarena.core.combat.CombatConfig;
import dev.backpackarena.core.economy.EconomyConfig;
import dev.backpackarena.core.model.AbilityDef;
import dev.backpackarena.core.model.EffectDef;
import dev.backpackarena.core.model.HeroDef;
import dev.backpackarena.core.model.ItemCategory;
import dev.backpackarena.core.model.ItemDef;
import dev.backpackarena.core.model.Rarity;
import dev.backpackarena.core.model.RecipeDef;
import dev.backpackarena.core.model.RelicDef;
import dev.backpackarena.core.model.Shape;
import dev.backpackarena.core.model.WeaponStats;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * Собирает {@link GameContent} из разобранных YAML-файлов (Map), не завися от Bukkit.
 * Ошибки накапливаются и выбрасываются одним {@link ContentException}, чтобы админ
 * видел все проблемы конфига сразу.
 */
public final class ContentLoader {

    public static final List<String> FILES = List.of("heroes.yml", "items.yml", "recipes.yml",
            "relics.yml", "economy.yml", "combat.yml");

    private final List<String> errors = new ArrayList<>();

    public static GameContent load(Function<String, Map<String, Object>> files) {
        return new ContentLoader().loadAll(files);
    }

    private GameContent loadAll(Function<String, Map<String, Object>> files) {
        Map<String, ItemDef> items = loadItems(section(files.apply("items.yml"), "items"));
        Map<String, HeroDef> heroes = loadHeroes(section(files.apply("heroes.yml"), "heroes"));
        Map<String, RelicDef> relics = loadRelics(section(files.apply("relics.yml"), "relics"));
        List<RecipeDef> recipes = loadRecipes(section(files.apply("recipes.yml"), "recipes"));
        EconomyConfig economy = loadEconomy(files.apply("economy.yml"));
        CombatConfig combat = loadCombat(files.apply("combat.yml"));

        validate(items, heroes, recipes);
        if (!errors.isEmpty()) {
            throw new ContentException(errors);
        }
        return new GameContent(heroes, items, relics, recipes, economy, combat);
    }

    // ---------------------------------------------------------------- items

    private Map<String, ItemDef> loadItems(Map<String, Object> root) {
        Map<String, ItemDef> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : root.entrySet()) {
            String id = e.getKey();
            Map<String, Object> m = asMap(e.getValue());
            try {
                WeaponStats weapon = null;
                if (m.get("weapon") != null) {
                    Map<String, Object> w = asMap(m.get("weapon"));
                    List<Object> dmg = asList(w.get("damage"));
                    weapon = new WeaponStats(num(dmg.get(0)), num(dmg.get(dmg.size() - 1)),
                            num(w, "cooldown", 2), num(w, "stamina", 1), num(w, "accuracy", 0.8),
                            num(w, "crit", 0), bool(w, "ranged", false), bool(w, "pet", false));
                    if (weapon.cooldown() <= 0) {
                        errors.add("items." + id + ": cooldown должен быть > 0");
                    }
                }
                ItemDef def = new ItemDef(id,
                        str(m, "name", id),
                        str(m, "description", ""),
                        Rarity.valueOf(str(m, "rarity", "COMMON").toUpperCase(Locale.ROOT)),
                        ItemCategory.valueOf(str(m, "category", "MATERIAL").toUpperCase(Locale.ROOT)),
                        str(m, "material", "PAPER"),
                        Shape.parse(stringList(m.get("shape"))),
                        (int) num(m, "price", 1),
                        bool(m, "rotatable", true),
                        (String) m.get("hero"),
                        weapon,
                        loadEffects(id, m.get("effects")),
                        new HashSet<>(stringList(m.get("tags"))),
                        bool(m, "in-shop", true));
                result.put(id, def);
            } catch (RuntimeException ex) {
                errors.add("items." + id + ": " + ex.getMessage());
            }
        }
        return result;
    }

    private List<EffectDef> loadEffects(String owner, Object raw) {
        List<EffectDef> list = new ArrayList<>();
        for (Object o : asList(raw)) {
            Map<String, Object> m = asMap(o);
            try {
                EffectDef.Trigger trigger = EffectDef.Trigger.valueOf(str(m, "trigger", "START").toUpperCase(Locale.ROOT));
                EffectDef.Action action = EffectDef.Action.valueOf(str(m, "action", "ARMOR").toUpperCase(Locale.ROOT));
                double interval = num(m, "interval", 0);
                if (trigger == EffectDef.Trigger.TIMER && interval <= 0) {
                    errors.add(owner + ": эффект TIMER требует interval > 0");
                }
                list.add(new EffectDef(trigger, action, num(m, "amount", 1), num(m, "chance", 1),
                        (int) num(m, "mana", 0), interval));
            } catch (RuntimeException ex) {
                errors.add(owner + ": неверный эффект " + m + " (" + ex.getMessage() + ")");
            }
        }
        return list;
    }

    // ---------------------------------------------------------------- heroes

    private Map<String, HeroDef> loadHeroes(Map<String, Object> root) {
        Map<String, HeroDef> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : root.entrySet()) {
            String id = e.getKey();
            Map<String, Object> m = asMap(e.getValue());
            try {
                Map<String, Object> s = asMap(m.get("stats"));
                Map<String, Object> a = asMap(m.get("ability"));
                Map<String, Double> params = new LinkedHashMap<>();
                asMap(a.get("params")).forEach((k, v) -> params.put(k, num(v)));
                AbilityDef ability = new AbilityDef(
                        AbilityDef.Type.valueOf(str(a, "type", "IRON_GUARD").toUpperCase(Locale.ROOT)),
                        str(a, "name", "Способность"), str(a, "description", ""),
                        num(a, "cooldown", 10), params);
                result.put(id, new HeroDef(id, str(m, "name", id), str(m, "title", ""),
                        str(m, "description", ""), str(m, "material", "PLAYER_HEAD"),
                        num(s, "max-hp", 50), num(s, "start-armor", 0),
                        num(s, "max-stamina", 5), num(s, "stamina-regen", 1),
                        num(s, "max-mana", 0), num(s, "mana-regen", 0),
                        num(s, "crit-chance", 0), num(s, "crit-multiplier", 2),
                        num(s, "sale-chance", 0.1), ability,
                        stringList(m.get("starting-bags")), stringList(m.get("starting-items"))));
            } catch (RuntimeException ex) {
                errors.add("heroes." + id + ": " + ex.getMessage());
            }
        }
        return result;
    }

    // ---------------------------------------------------------------- relics & recipes

    private Map<String, RelicDef> loadRelics(Map<String, Object> root) {
        Map<String, RelicDef> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : root.entrySet()) {
            String id = e.getKey();
            Map<String, Object> m = asMap(e.getValue());
            try {
                Map<String, Double> mods = new LinkedHashMap<>();
                asMap(m.get("modifiers")).forEach((k, v) -> mods.put(k, num(v)));
                result.put(id, new RelicDef(id, str(m, "name", id), str(m, "description", ""),
                        str(m, "material", "NETHER_STAR"),
                        Rarity.valueOf(str(m, "rarity", "EPIC").toUpperCase(Locale.ROOT)), mods));
            } catch (RuntimeException ex) {
                errors.add("relics." + id + ": " + ex.getMessage());
            }
        }
        return result;
    }

    private List<RecipeDef> loadRecipes(Map<String, Object> root) {
        List<RecipeDef> result = new ArrayList<>();
        for (Map.Entry<String, Object> e : root.entrySet()) {
            Map<String, Object> m = asMap(e.getValue());
            result.add(new RecipeDef(e.getKey(), stringList(m.get("ingredients")), str(m, "result", "")));
        }
        return result;
    }

    // ---------------------------------------------------------------- economy & combat

    private EconomyConfig loadEconomy(Map<String, Object> m) {
        Map<String, Object> income = asMap(m.get("income"));
        Map<String, Object> shop = asMap(m.get("shop"));
        Map<String, Object> match = asMap(m.get("match"));
        Map<String, Object> rewards = asMap(m.get("rewards"));

        TreeMap<Integer, EnumMap<Rarity, Double>> weights = new TreeMap<>();
        asMap(shop.get("rarity-weights")).forEach((round, raw) -> {
            EnumMap<Rarity, Double> w = new EnumMap<>(Rarity.class);
            asMap(raw).forEach((r, v) -> w.put(Rarity.valueOf(r.toUpperCase(Locale.ROOT)), num(v)));
            weights.put(Integer.parseInt(round), w);
        });
        if (weights.isEmpty()) {
            errors.add("economy.yml: shop.rarity-weights пуст");
            EnumMap<Rarity, Double> w = new EnumMap<>(Rarity.class);
            w.put(Rarity.COMMON, 1.0);
            weights.put(1, w);
        }
        List<Integer> rerolls = new ArrayList<>();
        for (Object o : asList(shop.get("reroll-costs"))) {
            rerolls.add((int) num(o));
        }
        if (rerolls.isEmpty()) {
            rerolls.add(1);
        }

        return new EconomyConfig(
                (int) num(income, "start-gold", 10), (int) num(income, "base", 8),
                (int) num(income, "growth", 1), (int) num(income, "max", 14),
                (int) num(income, "win-bonus", 2), (int) num(income, "streak-bonus", 1),
                (int) num(income, "streak-cap", 3),
                num(shop, "sell-ratio", 0.5), num(shop, "sale-discount", 0.33),
                (int) num(shop, "size", 5), rerolls,
                (int) num(shop, "craft-hammer-base", 5), (int) num(shop, "craft-hammer-step", 5),
                (int) num(match, "lives", 4), (int) num(match, "wins-to-finish", 10),
                (int) num(match, "relic-round", 5), (int) num(match, "relic-choices", 3),
                (int) num(match, "max-relics", 3), (int) num(match, "storage-size", 27),
                weights,
                new EconomyConfig.Rewards((int) num(rewards, "xp-per-win", 20),
                        (int) num(rewards, "xp-per-match", 30), (int) num(rewards, "gems-per-win", 1),
                        (int) num(rewards, "gems-full-run", 10)));
    }

    private CombatConfig loadCombat(Map<String, Object> m) {
        CombatConfig d = CombatConfig.defaults();
        Map<String, Object> s = asMap(m.get("status"));
        return new CombatConfig(
                num(m, "tick", d.tick()), num(m, "max-duration", d.maxDuration()),
                num(m, "status-interval", d.statusInterval()),
                num(m, "fatigue-start", d.fatigueStart()), num(m, "fatigue-damage", d.fatigueDamage()),
                num(s, "burn-damage", d.burnDamage()), num(s, "burn-decay", d.burnDecay()), num(s, "burn-cap", d.burnCap()),
                num(s, "poison-damage", d.poisonDamage()), num(s, "poison-cap", d.poisonCap()),
                num(s, "chill-slow", d.chillSlow()), num(s, "chill-decay", d.chillDecay()), num(s, "chill-cap", d.chillCap()),
                num(s, "haste-speed", d.hasteSpeed()), num(s, "haste-decay", d.hasteDecay()), num(s, "haste-cap", d.hasteCap()),
                num(s, "stun-seconds", d.stunSeconds()), num(s, "stun-cap", d.stunCap()),
                num(s, "empower-damage", d.empowerDamage()), num(s, "empower-cap", d.empowerCap()),
                num(s, "thorns-cap", d.thornsCap()), num(s, "lifesteal-cap", d.lifestealCap()),
                num(s, "armor-cap", d.armorCap()),
                num(m, "min-speed", d.minSpeed()), num(m, "max-speed", d.maxSpeed()),
                (int) num(m, "max-logged-events", d.maxLoggedEvents()));
    }

    // ---------------------------------------------------------------- validation

    private void validate(Map<String, ItemDef> items, Map<String, HeroDef> heroes, List<RecipeDef> recipes) {
        if (heroes.isEmpty()) {
            errors.add("heroes.yml: нет ни одного героя");
        }
        for (ItemDef item : items.values()) {
            if (item.hero() != null && !heroes.containsKey(item.hero())) {
                errors.add("items." + item.id() + ": неизвестный герой " + item.hero());
            }
            if (item.category() == ItemCategory.WEAPON && item.weapon() == null) {
                errors.add("items." + item.id() + ": оружие без блока weapon");
            }
        }
        for (HeroDef hero : heroes.values()) {
            for (String id : hero.startingBags()) {
                ItemDef def = items.get(id);
                if (def == null || !def.isBag()) {
                    errors.add("heroes." + hero.id() + ": starting-bags содержит не сумку " + id);
                }
            }
            for (String id : hero.startingItems()) {
                if (!items.containsKey(id)) {
                    errors.add("heroes." + hero.id() + ": неизвестный стартовый предмет " + id);
                }
            }
        }
        for (RecipeDef r : recipes) {
            if (r.ingredients().size() < 2) {
                errors.add("recipes." + r.id() + ": нужно минимум 2 ингредиента");
            }
            if (!items.containsKey(r.result())) {
                errors.add("recipes." + r.id() + ": неизвестный результат " + r.result());
            }
            for (String ing : r.ingredients()) {
                if (!items.containsKey(ing)) {
                    errors.add("recipes." + r.id() + ": неизвестный ингредиент " + ing);
                }
            }
        }
    }

    // ---------------------------------------------------------------- helpers

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        if (o instanceof Map<?, ?> m) {
            Map<String, Object> result = new LinkedHashMap<>();
            m.forEach((k, v) -> result.put(String.valueOf(k), v));
            return result;
        }
        return Map.of();
    }

    private static Map<String, Object> section(Map<String, Object> root, String key) {
        return root == null ? Map.of() : asMap(root.get(key));
    }

    @SuppressWarnings("unchecked")
    private static List<Object> asList(Object o) {
        return o instanceof List<?> l ? (List<Object>) l : List.of();
    }

    private static List<String> stringList(Object o) {
        List<String> list = new ArrayList<>();
        for (Object x : asList(o)) {
            list.add(String.valueOf(x));
        }
        return list;
    }

    private static double num(Object o) {
        if (o instanceof Number n) {
            return n.doubleValue();
        }
        return Double.parseDouble(String.valueOf(o));
    }

    private static double num(Map<String, Object> m, String key, double def) {
        Object o = m.get(key);
        return o == null ? def : num(o);
    }

    private static String str(Map<String, Object> m, String key, String def) {
        Object o = m.get(key);
        return o == null ? def : String.valueOf(o);
    }

    private static boolean bool(Map<String, Object> m, String key, boolean def) {
        Object o = m.get(key);
        return o == null ? def : Boolean.parseBoolean(String.valueOf(o));
    }
}
