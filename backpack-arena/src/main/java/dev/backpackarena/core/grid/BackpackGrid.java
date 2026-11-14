package dev.backpackarena.core.grid;

import dev.backpackarena.core.model.Cell;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Рюкзак 6×9 (6 строк × 9 столбцов, как двойной сундук).
 * Сумки ставятся на пустые клетки поля, предметы — только на клетки сумок.
 */
public final class BackpackGrid {

    public static final int WIDTH = 9;
    public static final int HEIGHT = 6;

    public enum Result {
        OK("Готово"),
        OUT_OF_BOUNDS("Не помещается в рюкзак"),
        OVERLAP("Клетки заняты"),
        NO_BAG("Предметы кладутся только в сумки"),
        BAG_NOT_EMPTY("Сначала освободите сумку");

        private final String message;

        Result(String message) {
            this.message = message;
        }

        public String message() {
            return message;
        }
    }

    private final List<PlacedPiece> bags = new ArrayList<>();
    private final List<PlacedPiece> items = new ArrayList<>();
    private final Map<Cell, PlacedPiece> bagCells = new HashMap<>();
    private final Map<Cell, PlacedPiece> itemCells = new HashMap<>();

    public static boolean inBounds(Cell c) {
        return c.x() >= 0 && c.y() >= 0 && c.x() < WIDTH && c.y() < HEIGHT;
    }

    public Result check(ItemInstance inst, int x, int y) {
        List<Cell> cells = inst.shape().at(x, y);
        for (Cell c : cells) {
            if (!inBounds(c)) {
                return Result.OUT_OF_BOUNDS;
            }
        }
        for (Cell c : cells) {
            if (inst.def().isBag()) {
                if (bagCells.containsKey(c)) {
                    return Result.OVERLAP;
                }
            } else {
                if (!bagCells.containsKey(c)) {
                    return Result.NO_BAG;
                }
                if (itemCells.containsKey(c)) {
                    return Result.OVERLAP;
                }
            }
        }
        return Result.OK;
    }

    public Result place(ItemInstance inst, int x, int y) {
        Result r = check(inst, x, y);
        if (r != Result.OK) {
            return r;
        }
        PlacedPiece piece = new PlacedPiece(inst, x, y);
        Map<Cell, PlacedPiece> index = inst.def().isBag() ? bagCells : itemCells;
        (inst.def().isBag() ? bags : items).add(piece);
        for (Cell c : piece.cells()) {
            index.put(c, piece);
        }
        return Result.OK;
    }

    public Result remove(PlacedPiece piece) {
        if (piece.item().def().isBag()) {
            for (Cell c : piece.cells()) {
                if (itemCells.containsKey(c)) {
                    return Result.BAG_NOT_EMPTY;
                }
            }
            bags.remove(piece);
            piece.cells().forEach(bagCells::remove);
        } else {
            items.remove(piece);
            piece.cells().forEach(itemCells::remove);
        }
        return Result.OK;
    }

    /** Ищет место для предмета, перебирая клетки и (если можно) повороты. Поворот предмета меняется. */
    public Optional<Cell> findSpot(ItemInstance inst) {
        int original = inst.rotation();
        int rotations = inst.def().rotatable() ? 4 : 1;
        for (int r = 0; r < rotations; r++) {
            inst.setRotation(original + r);
            for (int y = 0; y < HEIGHT; y++) {
                for (int x = 0; x < WIDTH; x++) {
                    if (check(inst, x, y) == Result.OK) {
                        return Optional.of(new Cell(x, y));
                    }
                }
            }
        }
        inst.setRotation(original);
        return Optional.empty();
    }

    public boolean autoPlace(ItemInstance inst) {
        return findSpot(inst).map(c -> place(inst, c.x(), c.y()) == Result.OK).orElse(false);
    }

    public PlacedPiece itemAt(Cell c) {
        return itemCells.get(c);
    }

    public PlacedPiece bagAt(Cell c) {
        return bagCells.get(c);
    }

    public Optional<PlacedPiece> find(ItemInstance inst) {
        for (PlacedPiece p : inst.def().isBag() ? bags : items) {
            if (p.item() == inst) {
                return Optional.of(p);
            }
        }
        return Optional.empty();
    }

    /** Предметы, соприкасающиеся с данным стороной клетки. */
    public Set<PlacedPiece> neighbours(PlacedPiece piece) {
        Set<PlacedPiece> result = new LinkedHashSet<>();
        for (Cell c : piece.cells()) {
            for (Cell n : List.of(c.offset(1, 0), c.offset(-1, 0), c.offset(0, 1), c.offset(0, -1))) {
                PlacedPiece other = itemCells.get(n);
                if (other != null && other != piece) {
                    result.add(other);
                }
            }
        }
        return result;
    }

    public List<PlacedPiece> items() {
        return Collections.unmodifiableList(items);
    }

    public List<PlacedPiece> bags() {
        return Collections.unmodifiableList(bags);
    }

    public int freeItemCells() {
        return bagCells.size() - itemCells.size();
    }

    public int totalValue() {
        int sum = 0;
        for (PlacedPiece p : bags) {
            sum += p.item().def().price();
        }
        for (PlacedPiece p : items) {
            sum += p.item().def().price();
        }
        return sum;
    }
}
