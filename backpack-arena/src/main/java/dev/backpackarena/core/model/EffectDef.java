package dev.backpackarena.core.model;

/**
 * Эффект предмета: при срабатывании {@link Trigger} выполняет {@link Action}.
 *
 * @param amount   сила (стаки, урон, лечение)
 * @param chance   шанс срабатывания 0..1
 * @param manaCost мана, которую нужно потратить (0 — бесплатно)
 * @param interval период для TIMER, секунды
 */
public record EffectDef(Trigger trigger, Action action, double amount, double chance, int manaCost, double interval) {

    public enum Trigger {
        /** В начале боя. */
        START,
        /** Каждые {@code interval} секунд. */
        TIMER,
        /** Когда этот предмет (оружие/питомец) попал. */
        ON_HIT,
        /** Когда этот предмет нанёс крит. */
        ON_CRIT,
        /** Когда владельца атаковали (щиты, броня). */
        ON_ATTACKED
    }

    public enum Action {
        ARMOR("Броня", true),
        THORNS("Шипы", true),
        HEAL("Лечение", true),
        HASTE("Ускорение", true),
        EMPOWER("Усиление", true),
        LIFESTEAL("Вампиризм", true),
        MANA("Мана", true),
        /** Блок части урона входящей атаки (только ON_ATTACKED). */
        BLOCK("Блок", true),
        BURN("Ожог", false),
        POISON("Яд", false),
        CHILL("Холод", false),
        STUN("Оглушение", false),
        DAMAGE("Урон", false);

        private final String displayName;
        private final boolean self;

        Action(String displayName, boolean self) {
            this.displayName = displayName;
            this.self = self;
        }

        public String displayName() {
            return displayName;
        }

        /** true — действует на владельца, false — на противника. */
        public boolean targetsSelf() {
            return self;
        }
    }

    public String describe() {
        StringBuilder sb = new StringBuilder();
        sb.append(switch (trigger) {
            case START -> "В начале боя: ";
            case TIMER -> "Каждые " + fmt(interval) + " с: ";
            case ON_HIT -> "При попадании: ";
            case ON_CRIT -> "При крите: ";
            case ON_ATTACKED -> "Когда вас атакуют: ";
        });
        if (chance < 1.0) {
            sb.append(Math.round(chance * 100)).append("% ");
        }
        if (manaCost > 0) {
            sb.append("потратить ").append(manaCost).append(" маны → ");
        }
        sb.append(action.displayName()).append(' ').append(fmt(amount));
        return sb.toString();
    }

    private static String fmt(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v);
    }
}
