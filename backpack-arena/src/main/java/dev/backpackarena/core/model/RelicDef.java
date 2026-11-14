package dev.backpackarena.core.model;

import java.util.Map;

/**
 * Реликвия — пассивный модификатор героя на весь матч.
 * Ключи модификаторов: max-hp, start-armor, mana-regen, burn-bonus, pet-damage,
 * lifesteal, accuracy, crit, stamina-regen, thorns.
 */
public record RelicDef(String id, String name, String description, String material, Rarity rarity,
                       Map<String, Double> modifiers) {

    public double mod(String key) {
        Double v = modifiers.get(key);
        return v == null ? 0 : v;
    }
}
