package dev.backpackarena.core;

import dev.backpackarena.core.grid.BackpackGrid;
import dev.backpackarena.core.grid.ItemInstance;
import dev.backpackarena.core.model.Cell;
import dev.backpackarena.core.model.Shape;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GridAndShapeTest {

    @Test
    void rotateLine() {
        Shape line = Shape.parse(List.of("X", "X", "X"));
        assertEquals(1, line.width());
        assertEquals(3, line.height());
        Shape rotated = line.rotate();
        assertEquals(3, rotated.width());
        assertEquals(1, rotated.height());
    }

    @Test
    void fourRotationsReturnOriginal() {
        Shape l = Shape.parse(List.of("X.", "X.", "XX"));
        assertEquals(l.cells(), l.rotate(4).cells());
        assertTrue(!l.cells().equals(l.rotate().cells()));
    }

    @Test
    void itemsNeedBags() {
        var content = TestContent.get();
        BackpackGrid grid = new BackpackGrid();
        ItemInstance sword = new ItemInstance(1, content.item("wooden_sword"));
        assertEquals(BackpackGrid.Result.NO_BAG, grid.check(sword, 0, 0));

        ItemInstance pouch = new ItemInstance(2, content.item("leather_pouch"));
        assertEquals(BackpackGrid.Result.OK, grid.place(pouch, 0, 0));
        assertEquals(BackpackGrid.Result.OK, grid.place(sword, 0, 0));

        ItemInstance sword2 = new ItemInstance(3, content.item("wooden_sword"));
        assertEquals(BackpackGrid.Result.OVERLAP, grid.check(sword2, 0, 0));
        assertEquals(BackpackGrid.Result.OK, grid.place(sword2, 1, 0));

        // 1×2 предмет не помещается на краю сетки
        ItemInstance sword3 = new ItemInstance(4, content.item("wooden_sword"));
        assertEquals(BackpackGrid.Result.OUT_OF_BOUNDS, grid.check(sword3, 8, 5));
    }

    @Test
    void bagCannotBeRemovedWhileUsed() {
        var content = TestContent.get();
        BackpackGrid grid = new BackpackGrid();
        ItemInstance pouch = new ItemInstance(1, content.item("leather_pouch"));
        grid.place(pouch, 3, 2);
        ItemInstance apple = new ItemInstance(2, content.item("apple"));
        grid.place(apple, 4, 3);
        var bagPiece = grid.bagAt(new Cell(3, 2));
        assertEquals(BackpackGrid.Result.BAG_NOT_EMPTY, grid.remove(bagPiece));
        grid.remove(grid.itemAt(new Cell(4, 3)));
        assertEquals(BackpackGrid.Result.OK, grid.remove(bagPiece));
        assertTrue(grid.bags().isEmpty());
    }

    @Test
    void neighboursAreSideAdjacent() {
        var content = TestContent.get();
        BackpackGrid grid = new BackpackGrid();
        grid.place(new ItemInstance(1, content.item("satchel")), 0, 0); // 3×2
        grid.place(new ItemInstance(2, content.item("apple")), 0, 0);
        grid.place(new ItemInstance(3, content.item("cactus")), 1, 0);
        grid.place(new ItemInstance(4, content.item("diamond")), 2, 1); // по диагонали к кактусу
        var cactus = grid.itemAt(new Cell(1, 0));
        assertEquals(1, grid.neighbours(grid.itemAt(new Cell(0, 0))).size());
        assertEquals(1, grid.neighbours(cactus).size());
    }
}
