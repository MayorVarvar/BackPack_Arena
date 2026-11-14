package dev.backpackarena.core.grid;

import dev.backpackarena.core.model.ItemDef;
import dev.backpackarena.core.model.Shape;

/** Конкретный экземпляр предмета у игрока: поворот, блокировка крафта, уровень. */
public final class ItemInstance {

    private final int id;
    private final ItemDef def;
    private int rotation;
    private boolean craftLocked;
    private int level = 1;

    public ItemInstance(int id, ItemDef def) {
        this.id = id;
        this.def = def;
    }

    public int id() {
        return id;
    }

    public ItemDef def() {
        return def;
    }

    public int rotation() {
        return rotation;
    }

    public Shape shape() {
        return def.shape().rotate(rotation);
    }

    /** Поворот на 90°; возвращает false, если тип предмета не вращается. */
    public boolean rotate() {
        if (!def.rotatable()) {
            return false;
        }
        rotation = (rotation + 1) % 4;
        return true;
    }

    public void setRotation(int rotation) {
        this.rotation = Math.floorMod(rotation, 4);
    }

    public boolean craftLocked() {
        return craftLocked;
    }

    public void setCraftLocked(boolean craftLocked) {
        this.craftLocked = craftLocked;
    }

    public int level() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }
}
