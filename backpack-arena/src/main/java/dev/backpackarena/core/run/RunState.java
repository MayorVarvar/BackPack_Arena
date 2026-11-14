package dev.backpackarena.core.run;

import dev.backpackarena.core.content.GameContent;
import dev.backpackarena.core.economy.Shop;
import dev.backpackarena.core.grid.BackpackGrid;
import dev.backpackarena.core.grid.ItemInstance;
import dev.backpackarena.core.model.HeroDef;
import dev.backpackarena.core.model.ItemDef;
import dev.backpackarena.core.model.RelicDef;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Состояние одного участника в матче: герой, золото, жизни, рюкзак, хранилище, магазин. */
public final class RunState {

    /** A gift starts at rank 1 and can be strengthened twice during a run. */
    public static final int MAX_GIFT_LEVEL = 3;
    private static final double GIFT_LEVEL_BONUS = 0.25;

    private final String ownerName;
    private final HeroDef hero;
    private final GameContent content;
    private final BackpackGrid grid = new BackpackGrid();
    private final List<ItemInstance> storage = new ArrayList<>();
    private final List<RelicDef> relics = new ArrayList<>();
    private final Map<String, Integer> giftLevels = new LinkedHashMap<>();
    private final Shop shop;
    private final Random random;

    private int nextInstanceId = 1;
    private int gold;
    private int lives;
    private int wins;
    private int losses;
    private int round = 1;
    private int streak;
    private boolean wonLast;
    private int hammerUses;
    private int giftTokens;
    private boolean rewarded;

    public RunState(String ownerName, HeroDef hero, GameContent content, Random random) {
        this.ownerName = ownerName;
        this.hero = hero;
        this.content = content;
        this.random = random;
        this.lives = content.economy().lives();
        this.gold = content.economy().income(1, false, 0);
        this.shop = new Shop(this);
        giveStartingKit();
        shop.roll(true);
    }

    private void giveStartingKit() {
        for (String bagId : hero.startingBags()) {
            ItemInstance bag = newInstance(content.item(bagId));
            if (!grid.autoPlace(bag)) {
                storage.add(bag);
            }
        }
        for (String itemId : hero.startingItems()) {
            ItemInstance item = newInstance(content.item(itemId));
            if (!grid.autoPlace(item)) {
                storage.add(item);
            }
        }
    }

    public ItemInstance newInstance(ItemDef def) {
        return new ItemInstance(nextInstanceId++, def);
    }

    /** Кладёт предмет в хранилище, если есть место. */
    public boolean addToStorage(ItemInstance inst) {
        if (storage.size() >= content.economy().storageSize()) {
            return false;
        }
        storage.add(inst);
        return true;
    }

    public boolean hasStorageSpace() {
        return storage.size() < content.economy().storageSize();
    }

    /** Итог боя: обновляет жизни/победы, начисляет доход, открывает новый раунд. */
    public void finishRound(Outcome outcome) {
        switch (outcome) {
            case WIN -> {
                wins++;
                streak = wonLast ? streak + 1 : 1;
                wonLast = true;
            }
            case LOSS -> {
                losses++;
                lives--;
                streak = 0;
                wonLast = false;
            }
            case DRAW -> {
                streak = 0;
                wonLast = false;
            }
        }
        round++;
        if (!isFinished()) {
            addGold(content.economy().income(round, wonLast, streak));
            shop.startRound();
        }
    }

    public boolean isFinished() {
        return lives <= 0 || wins >= content.economy().winsToFinish();
    }

    public boolean isRelicRound() {
        return round == content.economy().relicRound() && relics.size() < content.economy().maxRelics();
    }

    /**
     * Adds a match gift and grants the token used to improve one of the player's gifts.
     * A gift cannot be received twice in the same run.
     *
     * @return false when the player already owns this gift
     */
    public boolean receiveGift(RelicDef relic) {
        if (relic == null || relics.stream().anyMatch(existing -> existing.id().equals(relic.id()))) {
            return false;
        }
        relics.add(relic);
        giftLevels.put(relic.id(), 1);
        giftTokens++;
        return true;
    }

    /** Current rank of a received gift; legacy callers that add to {@link #relics()} get rank 1. */
    public int giftLevel(String relicId) {
        if (relics.stream().noneMatch(relic -> relic.id().equals(relicId))) {
            return 0;
        }
        return giftLevels.getOrDefault(relicId, 1);
    }

    public int giftLevel(RelicDef relic) {
        return relic == null ? 0 : giftLevel(relic.id());
    }

    /** The multiplier applied to every modifier of an upgraded gift. */
    public static double giftMultiplier(int level) {
        return 1.0 + (Math.max(1, level) - 1) * GIFT_LEVEL_BONUS;
    }

    /** Spends one match token to increase a selected gift by one rank. */
    public GiftUpgradeResult upgradeGift(String relicId) {
        int level = giftLevel(relicId);
        if (level == 0) {
            return GiftUpgradeResult.NOT_OWNED;
        }
        if (level >= MAX_GIFT_LEVEL) {
            return GiftUpgradeResult.MAX_LEVEL;
        }
        if (giftTokens <= 0) {
            return GiftUpgradeResult.NOT_ENOUGH_TOKENS;
        }
        giftTokens--;
        giftLevels.put(relicId, level + 1);
        return GiftUpgradeResult.OK;
    }

    /**
     * Snapshot of gifts with rank applied. Combat uses this instead of the mutable collection
     * returned by {@link #relics()} so upgraded modifiers are deterministic for a battle.
     */
    public List<RelicDef> combatRelics() {
        return relics.stream().map(relic -> {
            int level = giftLevel(relic);
            double multiplier = giftMultiplier(level);
            Map<String, Double> modifiers = new LinkedHashMap<>();
            relic.modifiers().forEach((key, value) -> modifiers.put(key, value * multiplier));
            return new RelicDef(relic.id(), relic.name(), relic.description(), relic.material(), relic.rarity(), modifiers);
        }).toList();
    }

    public void addGold(int amount) {
        gold = Math.max(0, gold + amount);
    }

    /** Списывает золото; false — не хватает (баланс никогда не уходит в минус). */
    public boolean spend(int amount) {
        if (amount < 0 || gold < amount) {
            return false;
        }
        gold -= amount;
        return true;
    }

    public double relicMod(String key) {
        double sum = 0;
        for (RelicDef r : combatRelics()) {
            sum += r.mod(key);
        }
        return sum;
    }

    public enum Outcome { WIN, LOSS, DRAW }

    // ---------------------------------------------------------------- getters

    public String ownerName() {
        return ownerName;
    }

    public HeroDef hero() {
        return hero;
    }

    public GameContent content() {
        return content;
    }

    public BackpackGrid grid() {
        return grid;
    }

    public List<ItemInstance> storage() {
        return storage;
    }

    public List<RelicDef> relics() {
        return relics;
    }

    public int giftTokens() {
        return giftTokens;
    }

    public Map<String, Integer> giftLevels() {
        return Map.copyOf(giftLevels);
    }

    public Shop shop() {
        return shop;
    }

    public Random random() {
        return random;
    }

    public int gold() {
        return gold;
    }

    public int lives() {
        return lives;
    }

    public int wins() {
        return wins;
    }

    public int losses() {
        return losses;
    }

    public int round() {
        return round;
    }

    public int streak() {
        return streak;
    }

    public int hammerUses() {
        return hammerUses;
    }

    public void incrementHammerUses() {
        hammerUses++;
    }

    /** Отмечает выдачу награды; возвращает false, если награда уже выдавалась. */
    public boolean markRewarded() {
        if (rewarded) {
            return false;
        }
        rewarded = true;
        return true;
    }

    public enum GiftUpgradeResult {
        OK("Дар усилен."),
        NOT_OWNED("Этот дар ещё не получен."),
        NOT_ENOUGH_TOKENS("Нужен 1 жетон дара."),
        MAX_LEVEL("Этот дар уже максимального уровня.");

        private final String message;

        GiftUpgradeResult(String message) {
            this.message = message;
        }

        public String message() {
            return message;
        }
    }
}
