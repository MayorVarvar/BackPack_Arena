package dev.backpackarena.core.model;

/** Шесть уровней редкости из ТЗ. Цвет — hex для отображения в Minecraft. */
public enum Rarity {
    COMMON("Обычный", 0xFFFFFF),
    RARE("Редкий", 0x5555FF),
    EPIC("Эпический", 0xAA00AA),
    LEGENDARY("Легендарный", 0xFFAA00),
    MYTHIC("Мифический", 0xFF5555),
    UNIQUE("Уникальный", 0xFFFF55);

    private final String displayName;
    private final int color;

    Rarity(String displayName, int color) {
        this.displayName = displayName;
        this.color = color;
    }

    public String displayName() {
        return displayName;
    }

    public int color() {
        return color;
    }
}
