package dev.backpackarena.core.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Форма предмета или сумки — набор клеток, нормализованный к (0,0).
 * В YAML задаётся строками: "XX", ".X" и т.п.
 */
public final class Shape {

    public static final Shape SINGLE = new Shape(Set.of(new Cell(0, 0)));

    private final Set<Cell> cells;
    private final int width;
    private final int height;

    public Shape(Set<Cell> cells) {
        if (cells.isEmpty()) {
            throw new IllegalArgumentException("Форма не может быть пустой");
        }
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = 0, maxY = 0;
        for (Cell c : cells) {
            minX = Math.min(minX, c.x());
            minY = Math.min(minY, c.y());
        }
        Set<Cell> normalized = new LinkedHashSet<>();
        for (Cell c : cells) {
            Cell n = c.offset(-minX, -minY);
            normalized.add(n);
            maxX = Math.max(maxX, n.x());
            maxY = Math.max(maxY, n.y());
        }
        this.cells = Collections.unmodifiableSet(normalized);
        this.width = maxX + 1;
        this.height = maxY + 1;
    }

    public static Shape parse(List<String> rows) {
        if (rows == null || rows.isEmpty()) {
            return SINGLE;
        }
        Set<Cell> cells = new LinkedHashSet<>();
        for (int y = 0; y < rows.size(); y++) {
            String row = rows.get(y);
            for (int x = 0; x < row.length(); x++) {
                if (row.charAt(x) == 'X' || row.charAt(x) == 'x') {
                    cells.add(new Cell(x, y));
                }
            }
        }
        return new Shape(cells);
    }

    /** Поворот на 90° по часовой стрелке. */
    public Shape rotate() {
        Set<Cell> rotated = new LinkedHashSet<>();
        for (Cell c : cells) {
            rotated.add(new Cell(height - 1 - c.y(), c.x()));
        }
        return new Shape(rotated);
    }

    public Shape rotate(int times) {
        Shape s = this;
        for (int i = 0; i < Math.floorMod(times, 4); i++) {
            s = s.rotate();
        }
        return s;
    }

    public List<Cell> at(int originX, int originY) {
        List<Cell> list = new ArrayList<>(cells.size());
        for (Cell c : cells) {
            list.add(c.offset(originX, originY));
        }
        return list;
    }

    public Set<Cell> cells() {
        return cells;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int size() {
        return cells.size();
    }

    public String describe() {
        return width + "×" + height;
    }
}
