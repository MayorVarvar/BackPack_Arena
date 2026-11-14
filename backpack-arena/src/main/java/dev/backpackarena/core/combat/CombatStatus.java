package dev.backpackarena.core.combat;

import java.util.Locale;

/**
 * Снимок действующего эффекта в конкретный момент боя.
 *
 * <p>Снимки кладутся в события боя, поэтому клиентский повтор и журнал не
 * пытаются заново вычислять боевую логику. {@code beneficial} относится к
 * владельцу эффекта: положительные эффекты показываются отдельно от дебаффов.</p>
 */
public record CombatStatus(String id, String name, double value, boolean beneficial) {

    /** Короткая подпись для панели состояния и книги журнала. */
    public String display() {
        if (value <= 0) {
            return name;
        }
        String suffix = value == Math.rint(value)
                ? String.valueOf((long) value)
                : String.format(Locale.ROOT, "%.1f", value);
        return name + " " + suffix;
    }
}
