package dev.backpackarena.core.model;

import java.util.List;

/**
 * Герой из heroes.yml.
 *
 * @param startingBags  сумки, которые ставятся в рюкзак в начале матча
 * @param startingItems предметы, которые кладутся в стартовые сумки (или в хранилище)
 */
public record HeroDef(String id, String name, String title, String description, String material,
                      double maxHp, double startArmor, double maxStamina, double staminaRegen,
                      double maxMana, double manaRegen, double critChance, double critMultiplier,
                      double saleChance, AbilityDef ability,
                      List<String> startingBags, List<String> startingItems) {
}
