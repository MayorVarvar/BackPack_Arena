package dev.backpackarena.core.economy;

import dev.backpackarena.core.model.Rarity;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Параметры economy.yml. Все числа — из конфига, значения по умолчанию заданы в ресурсе. */
public record EconomyConfig(
        int startGold,
        int baseIncome,
        int incomeGrowth,
        int maxIncome,
        int winBonus,
        int streakBonus,
        int streakCap,
        double sellRatio,
        double saleDiscount,
        int shopSize,
        List<Integer> rerollCosts,
        int craftHammerBase,
        int craftHammerStep,
        int lives,
        int winsToFinish,
        int relicRound,
        int relicChoices,
        int maxRelics,
        int storageSize,
        TreeMap<Integer, EnumMap<Rarity, Double>> rarityWeights,
        Rewards rewards) {

    public record Rewards(int xpPerWin, int xpPerMatch, int gemsPerWin, int gemsForFullRun) {
    }

    /** Доход в начале раунда {@code round} (раунд 1 — стартовое золото). */
    public int income(int round, boolean wonLast, int streak) {
        if (round <= 1) {
            return startGold;
        }
        int income = Math.min(baseIncome + incomeGrowth * (round - 2), maxIncome);
        if (wonLast) {
            income += winBonus + Math.min(streak, streakCap) * streakBonus;
        }
        return income;
    }

    /** Цена n-го реролла в раунде (n с 1). Последний элемент списка повторяется. */
    public int rerollCost(int n) {
        int idx = Math.min(Math.max(n, 1) - 1, rerollCosts.size() - 1);
        return rerollCosts.get(idx);
    }

    public int craftHammerCost(int uses) {
        return craftHammerBase + craftHammerStep * uses;
    }

    public Map<Rarity, Double> weightsFor(int round) {
        Map.Entry<Integer, EnumMap<Rarity, Double>> e = rarityWeights.floorEntry(round);
        return e != null ? e.getValue() : rarityWeights.firstEntry().getValue();
    }

    public int salePrice(int price) {
        return (int) Math.ceil(price * (1.0 - saleDiscount));
    }
}
