package dev.backpackarena.core.model;

public enum ItemCategory {
    WEAPON("Оружие"),
    ARMOR("Броня"),
    POTION("Зелье"),
    FOOD("Еда"),
    PET("Питомец"),
    MAGIC("Магический предмет"),
    MATERIAL("Материал"),
    ACCESSORY("Аксессуар"),
    BAG("Сумка");

    private final String displayName;

    ItemCategory(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
