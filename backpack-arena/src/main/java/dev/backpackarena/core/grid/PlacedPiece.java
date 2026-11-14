package dev.backpackarena.core.grid;

import dev.backpackarena.core.model.Cell;

import java.util.List;

/** Предмет или сумка, стоящие в рюкзаке. (x, y) — левый верхний угол формы. */
public record PlacedPiece(ItemInstance item, int x, int y) {

    public List<Cell> cells() {
        return item.shape().at(x, y);
    }

    public Cell origin() {
        return new Cell(x, y);
    }
}
