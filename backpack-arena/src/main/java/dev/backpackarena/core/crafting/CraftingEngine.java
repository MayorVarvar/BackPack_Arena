package dev.backpackarena.core.crafting;

import dev.backpackarena.core.grid.BackpackGrid;
import dev.backpackarena.core.grid.ItemInstance;
import dev.backpackarena.core.grid.PlacedPiece;
import dev.backpackarena.core.model.RecipeDef;
import dev.backpackarena.core.run.RunState;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Крафт соседством: ингредиенты рецепта должны образовывать связную группу
 * (каждый касается стороной хотя бы одного другого). Заблокированные предметы не участвуют.
 */
public final class CraftingEngine {

    public record Match(RecipeDef recipe, List<PlacedPiece> pieces) {
    }

    private CraftingEngine() {
    }

    /** Все непересекающиеся совпадения рецептов в рюкзаке (жадно, в порядке recipes.yml). */
    public static List<Match> findMatches(BackpackGrid grid, List<RecipeDef> recipes) {
        List<Match> result = new ArrayList<>();
        Set<PlacedPiece> used = new HashSet<>();
        for (RecipeDef recipe : recipes) {
            boolean found = true;
            while (found) {
                found = false;
                for (PlacedPiece seed : grid.items()) {
                    Optional<List<PlacedPiece>> m = matchFrom(grid, recipe, seed, used);
                    if (m.isPresent()) {
                        used.addAll(m.get());
                        result.add(new Match(recipe, m.get()));
                        found = true;
                        break;
                    }
                }
            }
        }
        return result;
    }

    /** Совпадения, в которых участвует данный предмет (для подсветки в интерфейсе). */
    public static boolean isPartOfMatch(BackpackGrid grid, List<RecipeDef> recipes, PlacedPiece piece) {
        for (Match m : findMatches(grid, recipes)) {
            if (m.pieces().contains(piece)) {
                return true;
            }
        }
        return false;
    }

    private static Optional<List<PlacedPiece>> matchFrom(BackpackGrid grid, RecipeDef recipe,
                                                         PlacedPiece seed, Set<PlacedPiece> used) {
        if (!usable(seed, used) || !seed.item().def().id().equals(recipe.ingredients().get(0))) {
            return Optional.empty();
        }
        List<String> remaining = new ArrayList<>(recipe.ingredients().subList(1, recipe.ingredients().size()));
        List<PlacedPiece> chosen = new ArrayList<>();
        chosen.add(seed);
        return search(grid, remaining, chosen, used) ? Optional.of(chosen) : Optional.empty();
    }

    private static boolean search(BackpackGrid grid, List<String> remaining, List<PlacedPiece> chosen,
                                  Set<PlacedPiece> used) {
        if (remaining.isEmpty()) {
            return true;
        }
        Set<PlacedPiece> frontier = new LinkedHashSet<>();
        for (PlacedPiece c : chosen) {
            frontier.addAll(grid.neighbours(c));
        }
        for (PlacedPiece candidate : frontier) {
            String id = candidate.item().def().id();
            if (chosen.contains(candidate) || !usable(candidate, used) || !remaining.contains(id)) {
                continue;
            }
            remaining.remove(id);
            chosen.add(candidate);
            if (search(grid, remaining, chosen, used)) {
                return true;
            }
            chosen.remove(chosen.size() - 1);
            remaining.add(id);
        }
        return false;
    }

    private static boolean usable(PlacedPiece p, Set<PlacedPiece> used) {
        return !used.contains(p) && !p.item().craftLocked();
    }

    /**
     * Выполняет все найденные рецепты: удаляет компоненты, ставит результат на освободившееся
     * место (или куда угодно в рюкзак, или в хранилище). Возвращает созданные предметы.
     */
    public static List<ItemInstance> craftAll(RunState run) {
        List<ItemInstance> created = new ArrayList<>();
        for (Match m : findMatches(run.grid(), run.content().recipes())) {
            created.add(apply(run, m));
        }
        return created;
    }

    public static ItemInstance apply(RunState run, Match match) {
        BackpackGrid grid = run.grid();
        for (PlacedPiece p : match.pieces()) {
            grid.remove(p);
        }
        ItemInstance result = run.newInstance(run.content().item(match.recipe().result()));
        boolean placed = false;
        // сначала пробуем поставить результат на место одного из компонентов
        for (PlacedPiece p : match.pieces()) {
            if (grid.place(result, p.x(), p.y()) == BackpackGrid.Result.OK) {
                placed = true;
                break;
            }
        }
        if (!placed && !grid.autoPlace(result)) {
            // временное хранилище может переполниться только крафтом — допускаем это
            run.storage().add(result);
        }
        return result;
    }
}
