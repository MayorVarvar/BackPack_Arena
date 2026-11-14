package dev.backpackarena.core.model;

import java.util.List;

/** Рецепт: ингредиенты должны образовывать связную группу соседей в рюкзаке. */
public record RecipeDef(String id, List<String> ingredients, String result) {
}
