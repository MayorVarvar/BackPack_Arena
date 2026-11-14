package dev.backpackarena.core.model;

import java.util.List;
import java.util.Set;

/**
 * Определение предмета из items.yml.
 *
 * @param hero     id героя, которому доступен предмет (null — общий)
 * @param weapon   null, если предмет не атакует
 * @param tags     свободные метки для синергий (например "nature", "fire")
 * @param inShop   false — предмет нельзя купить, только скрафтить или получить наградой
 */
public record ItemDef(String id, String name, String description, Rarity rarity, ItemCategory category,
                      String material, Shape shape, int price, boolean rotatable, String hero,
                      WeaponStats weapon, List<EffectDef> effects, Set<String> tags, boolean inShop) {

    public boolean isBag() {
        return category == ItemCategory.BAG;
    }

    public boolean attacks() {
        return weapon != null;
    }

    public boolean availableFor(String heroId) {
        return hero == null || hero.equals(heroId);
    }

    public int sellPrice(double ratio) {
        return (int) Math.ceil(price * ratio);
    }
}
