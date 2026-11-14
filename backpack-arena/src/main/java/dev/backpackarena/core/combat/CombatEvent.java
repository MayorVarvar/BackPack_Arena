package dev.backpackarena.core.combat;

import java.util.List;

/**
 * Событие боя для журнала и повтора. {@code side} — кто действовал (0/1, -1 — система).
 * hp/armor и статусы — состояние обеих сторон после события, чтобы повтор мог
 * обновлять полоски и панели эффектов без повторного расчёта симуляции.
 */
public record CombatEvent(double time, int side, Type type, double amount, String text,
                          double hp0, double hp1, double armor0, double armor1,
                          List<CombatStatus> statuses0, List<CombatStatus> statuses1) {

    public enum Type {
        HIT, CRIT, MISS, NO_STAMINA, BLOCK, THORNS, ABILITY, STATUS, HEAL, FATIGUE, END
    }
}
