package dev.backpackarena.core.combat;

import java.util.List;

/**
 * Итог боя. {@code winner}: 0 или 1 — индекс победителя, -1 — ничья.
 */
public record CombatResult(int winner, double duration, List<CombatEvent> events,
                           double[] finalHp, double[] maxHp, String[] names) {

    public boolean isDraw() {
        return winner < 0;
    }
}
