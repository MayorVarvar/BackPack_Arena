package dev.backpackarena.core.model;

/** Клетка сетки: x — столбец (0..8), y — строка (0..5). */
public record Cell(int x, int y) {

    public Cell offset(int dx, int dy) {
        return new Cell(x + dx, y + dy);
    }

    public boolean isAdjacent(Cell other) {
        return Math.abs(x - other.x) + Math.abs(y - other.y) == 1;
    }
}
