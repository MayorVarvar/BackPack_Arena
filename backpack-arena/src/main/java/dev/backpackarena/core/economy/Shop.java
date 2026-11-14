package dev.backpackarena.core.economy;

import dev.backpackarena.core.grid.BackpackGrid;
import dev.backpackarena.core.grid.ItemInstance;
import dev.backpackarena.core.grid.PlacedPiece;
import dev.backpackarena.core.model.ItemDef;
import dev.backpackarena.core.model.Rarity;
import dev.backpackarena.core.run.RunState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Магазин игрока: предложения, покупка, продажа, реролл, резерв. */
public final class Shop {

    public enum Result {
        OK("Готово"),
        NOT_ENOUGH_GOLD("Не хватает золота"),
        STORAGE_FULL("Хранилище заполнено"),
        EMPTY_SLOT("Здесь ничего нет"),
        BAG_NOT_EMPTY("Сначала освободите сумку");

        private final String message;

        Result(String message) {
            this.message = message;
        }

        public String message() {
            return message;
        }
    }

    public static final class Offer {
        private final ItemDef item;
        private final boolean onSale;
        private boolean reserved;

        Offer(ItemDef item, boolean onSale) {
            this.item = item;
            this.onSale = onSale;
        }

        public ItemDef item() {
            return item;
        }

        public boolean onSale() {
            return onSale;
        }

        public boolean reserved() {
            return reserved;
        }
    }

    private final RunState run;
    private final List<Offer> offers = new ArrayList<>();
    private int rerollsThisRound;

    public Shop(RunState run) {
        this.run = run;
    }

    private EconomyConfig eco() {
        return run.content().economy();
    }

    /** Новый раунд: счётчик рерролов сбрасывается, ассортимент обновляется (кроме резерва). */
    public void startRound() {
        rerollsThisRound = 0;
        roll(false);
    }

    /** Обновляет все незарезервированные слоты. {@code reset} — сбросить и резерв. */
    public void roll(boolean reset) {
        List<Offer> next = new ArrayList<>();
        for (int i = 0; i < eco().shopSize(); i++) {
            Offer old = i < offers.size() ? offers.get(i) : null;
            if (!reset && old != null && old.reserved) {
                next.add(old);
            } else {
                ItemDef def = pickItem(run.random());
                next.add(def == null ? null : new Offer(def,
                        run.random().nextDouble() < run.hero().saleChance()));
            }
        }
        offers.clear();
        offers.addAll(next);
    }

    private ItemDef pickItem(Random rng) {
        Map<Rarity, Double> weights = eco().weightsFor(run.round());
        // повторяем, если выпала редкость без доступных предметов
        for (int attempt = 0; attempt < 10; attempt++) {
            Rarity rarity = weightedRarity(weights, rng);
            List<ItemDef> pool = new ArrayList<>();
            for (ItemDef def : run.content().allItems()) {
                if (def.inShop() && def.rarity() == rarity && def.availableFor(run.hero().id())) {
                    pool.add(def);
                }
            }
            if (!pool.isEmpty()) {
                return pool.get(rng.nextInt(pool.size()));
            }
        }
        return null;
    }

    private static Rarity weightedRarity(Map<Rarity, Double> weights, Random rng) {
        double total = 0;
        for (double w : weights.values()) {
            total += w;
        }
        double roll = rng.nextDouble() * total;
        for (Map.Entry<Rarity, Double> e : weights.entrySet()) {
            roll -= e.getValue();
            if (roll <= 0) {
                return e.getKey();
            }
        }
        return Rarity.COMMON;
    }

    public int priceOf(Offer offer) {
        return offer.onSale ? eco().salePrice(offer.item.price()) : offer.item.price();
    }

    public Result buy(int slot) {
        Offer offer = slot >= 0 && slot < offers.size() ? offers.get(slot) : null;
        if (offer == null) {
            return Result.EMPTY_SLOT;
        }
        if (!run.hasStorageSpace()) {
            return Result.STORAGE_FULL;
        }
        if (!run.spend(priceOf(offer))) {
            return Result.NOT_ENOUGH_GOLD;
        }
        run.addToStorage(run.newInstance(offer.item));
        offers.set(slot, null);
        return Result.OK;
    }

    public int nextRerollCost() {
        return eco().rerollCost(rerollsThisRound + 1);
    }

    public Result reroll() {
        if (!run.spend(nextRerollCost())) {
            return Result.NOT_ENOUGH_GOLD;
        }
        rerollsThisRound++;
        roll(false);
        return Result.OK;
    }

    public Result toggleReserve(int slot) {
        Offer offer = slot >= 0 && slot < offers.size() ? offers.get(slot) : null;
        if (offer == null) {
            return Result.EMPTY_SLOT;
        }
        offer.reserved = !offer.reserved;
        return Result.OK;
    }

    /** Продаёт предмет из хранилища или рюкзака. */
    public Result sell(ItemInstance inst) {
        if (run.storage().remove(inst)) {
            run.addGold(inst.def().sellPrice(eco().sellRatio()));
            return Result.OK;
        }
        PlacedPiece piece = run.grid().find(inst).orElse(null);
        if (piece == null) {
            return Result.EMPTY_SLOT;
        }
        if (run.grid().remove(piece) != BackpackGrid.Result.OK) {
            return Result.BAG_NOT_EMPTY;
        }
        run.addGold(inst.def().sellPrice(eco().sellRatio()));
        return Result.OK;
    }

    public List<Offer> offers() {
        return offers;
    }

    public int rerollsThisRound() {
        return rerollsThisRound;
    }
}
