package dev.backpackarena.core.combat;

import dev.backpackarena.core.model.AbilityDef;
import dev.backpackarena.core.model.EffectDef;
import dev.backpackarena.core.model.EffectDef.Action;
import dev.backpackarena.core.model.EffectDef.Trigger;
import dev.backpackarena.core.model.ItemDef;
import dev.backpackarena.core.model.WeaponStats;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Серверный детерминированный автобой. Одинаковые входы и seed дают одинаковый результат,
 * поэтому бой можно пересчитать для проверки или повтора.
 *
 * Порядок тика: реген → статусы (раз в statusInterval) → усталость → для каждой стороны:
 * оглушение → способность → таймеры предметов → атаки. Эффекты не запускают другие
 * эффекты, поэтому бесконечных цепочек активаций быть не может.
 */
public final class CombatSimulator {

    enum Status { THORNS, BURN, POISON, CHILL, STUN, HASTE, EMPOWER, LIFESTEAL }

    static final class Attacker {
        final String name;
        final WeaponStats weapon;
        final List<EffectDef> onHit = new ArrayList<>();
        final List<EffectDef> onCrit = new ArrayList<>();
        final double expiresAt;
        double timer;

        Attacker(String name, WeaponStats weapon, double expiresAt) {
            this.name = name;
            this.weapon = weapon;
            this.expiresAt = expiresAt;
        }
    }

    static final class Timed {
        final String name;
        final EffectDef effect;
        double timer;

        Timed(String name, EffectDef effect) {
            this.name = name;
            this.effect = effect;
        }
    }

    final class Fighter {
        final int side;
        final FighterSpec spec;
        final Map<Status, Double> st = new EnumMap<>(Status.class);
        final List<Attacker> attackers = new ArrayList<>();
        final List<Timed> timers = new ArrayList<>();
        final List<EffectDef> onAttacked = new ArrayList<>();
        final List<String> onAttackedNames = new ArrayList<>();
        double hp, maxHp, armor, stamina, maxStamina, staminaRegen, mana, maxMana, manaRegen;
        double crit, critMult, accuracyBonus, burnBonus, petDamageMult;
        double abilityTimer;
        boolean fire = true;
        double reflectUntil = -1;
        int rangedCount;

        Fighter(int side, FighterSpec spec) {
            this.side = side;
            this.spec = spec;
            var h = spec.hero();
            maxHp = h.maxHp() + spec.relicMod("max-hp");
            hp = maxHp;
            armor = h.startArmor() + spec.relicMod("start-armor");
            maxStamina = h.maxStamina();
            stamina = maxStamina;
            staminaRegen = h.staminaRegen() + spec.relicMod("stamina-regen");
            maxMana = h.maxMana();
            manaRegen = h.manaRegen() + spec.relicMod("mana-regen");
            crit = h.critChance() + spec.relicMod("crit");
            critMult = h.critMultiplier();
            accuracyBonus = spec.relicMod("accuracy");
            burnBonus = spec.relicMod("burn-bonus");
            petDamageMult = 1 + spec.relicMod("pet-damage");
            for (Status s : Status.values()) {
                st.put(s, 0.0);
            }
            add(Status.LIFESTEAL, spec.relicMod("lifesteal"));
            add(Status.THORNS, spec.relicMod("thorns"));

            for (ItemDef item : spec.items()) {
                Attacker attacker = null;
                if (item.weapon() != null) {
                    attacker = new Attacker(item.name(), item.weapon(), Double.MAX_VALUE);
                    attackers.add(attacker);
                    if (item.weapon().ranged()) {
                        rangedCount++;
                    }
                }
                for (EffectDef e : item.effects()) {
                    switch (e.trigger()) {
                        case TIMER -> timers.add(new Timed(item.name(), e));
                        case ON_ATTACKED -> {
                            onAttacked.add(e);
                            onAttackedNames.add(item.name());
                        }
                        case ON_HIT -> {
                            if (attacker != null) {
                                attacker.onHit.add(e);
                            }
                        }
                        case ON_CRIT -> {
                            if (attacker != null) {
                                attacker.onCrit.add(e);
                            }
                        }
                        case START -> { /* применяется в начале боя */ }
                    }
                }
            }
        }

        double get(Status s) {
            return st.get(s);
        }

        void add(Status s, double v) {
            double cap = switch (s) {
                case THORNS -> cfg.thornsCap();
                case BURN -> cfg.burnCap();
                case POISON -> cfg.poisonCap();
                case CHILL -> cfg.chillCap();
                case STUN -> cfg.stunCap() * cfg.stunSeconds();
                case HASTE -> cfg.hasteCap();
                case EMPOWER -> cfg.empowerCap();
                case LIFESTEAL -> cfg.lifestealCap();
            };
            st.put(s, Math.max(0, Math.min(cap, st.get(s) + v)));
        }

        double speed() {
            double sp = 1 + get(Status.HASTE) * cfg.hasteSpeed() - get(Status.CHILL) * cfg.chillSlow();
            return Math.max(cfg.minSpeed(), Math.min(cfg.maxSpeed(), sp));
        }

        boolean alive() {
            return hp > 0;
        }
    }

    private final CombatConfig cfg;
    private final Random rng;
    private final List<CombatEvent> events = new ArrayList<>();
    private Fighter[] f;
    private double now;

    private CombatSimulator(CombatConfig cfg, long seed) {
        this.cfg = cfg;
        this.rng = new Random(seed);
    }

    public static CombatResult simulate(FighterSpec a, FighterSpec b, CombatConfig cfg, long seed) {
        return new CombatSimulator(cfg, seed).run(a, b);
    }

    private CombatResult run(FighterSpec a, FighterSpec b) {
        f = new Fighter[]{new Fighter(0, a), new Fighter(1, b)};

        for (Fighter x : f) {
            for (ItemDef item : x.spec.items()) {
                for (EffectDef e : item.effects()) {
                    if (e.trigger() == Trigger.START) {
                        applyEffect(x, enemy(x), e, item.name());
                    }
                }
            }
        }

        int ticks = (int) Math.round(cfg.maxDuration() / cfg.tick());
        int statusEvery = Math.max(1, (int) Math.round(cfg.statusInterval() / cfg.tick()));
        int fatigueStartTick = (int) Math.round(cfg.fatigueStart() / cfg.tick());
        int fatigueStacks = 0;
        int winner = -1;
        int tick;
        for (tick = 1; tick <= ticks; tick++) {
            now = tick * cfg.tick();
            for (Fighter x : f) {
                x.stamina = Math.min(x.maxStamina, x.stamina + x.staminaRegen * cfg.tick());
                x.mana = Math.min(x.maxMana, x.mana + x.manaRegen * cfg.tick());
            }
            if (tick % statusEvery == 0) {
                for (Fighter x : f) {
                    tickStatuses(x);
                }
                if (tick >= fatigueStartTick) {
                    fatigueStacks++;
                    for (Fighter x : f) {
                        double dmg = cfg.fatigueDamage() * fatigueStacks;
                        x.hp -= dmg;
                        log(x.side, CombatEvent.Type.FATIGUE, dmg, "Усталость: −" + fmt(dmg));
                    }
                }
            }
            // стороны ходят по очереди первыми, чтобы не было преимущества у side 0
            int first = tick % 2;
            for (int i = 0; i < 2; i++) {
                Fighter x = f[(first + i) % 2];
                if (x.alive() && enemy(x).alive()) {
                    act(x);
                }
            }
            if (!f[0].alive() || !f[1].alive()) {
                break;
            }
        }
        if (f[0].alive() && !f[1].alive()) {
            winner = 0;
        } else if (f[1].alive() && !f[0].alive()) {
            winner = 1;
        } else if (f[0].alive() && f[1].alive()) {
            // время вышло — побеждает тот, у кого больше доля HP
            double r0 = f[0].hp / f[0].maxHp, r1 = f[1].hp / f[1].maxHp;
            winner = Math.abs(r0 - r1) < 1e-9 ? -1 : (r0 > r1 ? 0 : 1);
        }
        String endText = winner < 0 ? "Ничья" : "Победа: " + f[winner].spec.name();
        events.add(event(-1, CombatEvent.Type.END, 0, endText));
        return new CombatResult(winner, now, List.copyOf(events),
                new double[]{Math.max(0, f[0].hp), Math.max(0, f[1].hp)},
                new double[]{f[0].maxHp, f[1].maxHp},
                new String[]{f[0].spec.name(), f[1].spec.name()});
    }

    private Fighter enemy(Fighter x) {
        return f[1 - x.side];
    }

    private void act(Fighter x) {
        if (x.get(Status.STUN) > 0) {
            x.st.put(Status.STUN, Math.max(0, x.get(Status.STUN) - cfg.tick()));
            return;
        }
        double dt = cfg.tick() * x.speed();

        AbilityDef ability = x.spec.hero().ability();
        x.abilityTimer += cfg.tick();
        if (x.abilityTimer >= ability.cooldown()) {
            x.abilityTimer -= ability.cooldown();
            useAbility(x, ability);
        }

        for (Timed t : x.timers) {
            t.timer += dt;
            if (t.timer >= t.effect.interval()) {
                t.timer -= t.effect.interval();
                applyEffect(x, enemy(x), t.effect, t.name);
            }
        }

        x.attackers.removeIf(a -> now >= a.expiresAt);
        for (Attacker a : new ArrayList<>(x.attackers)) {
            a.timer += dt;
            if (a.timer >= a.weapon.cooldown()) {
                a.timer -= a.weapon.cooldown();
                attack(x, a);
                if (!enemy(x).alive() || !x.alive()) {
                    return;
                }
            }
        }
    }

    private void attack(Fighter x, Attacker a) {
        Fighter target = enemy(x);
        WeaponStats w = a.weapon;
        if (!w.pet() && w.stamina() > 0) {
            if (x.stamina < w.stamina()) {
                // по правилам: урона нет, выносливость не тратится, КД перезапускается
                log(x.side, CombatEvent.Type.NO_STAMINA, 0, a.name + ": нет выносливости");
                return;
            }
            x.stamina -= w.stamina();
        }
        if (rng.nextDouble() >= w.accuracy() + x.accuracyBonus) {
            log(x.side, CombatEvent.Type.MISS, 0, a.name + ": промах");
            return;
        }
        double dmg = w.minDamage() + rng.nextDouble() * (w.maxDamage() - w.minDamage());
        dmg += x.get(Status.EMPOWER) * cfg.empowerDamage();
        if (w.pet()) {
            dmg *= x.petDamageMult;
        }
        boolean crit = rng.nextDouble() < w.critChance() + x.crit;
        if (crit) {
            dmg *= x.critMult;
        }

        // блок со щитов противника
        for (int i = 0; i < target.onAttacked.size(); i++) {
            EffectDef e = target.onAttacked.get(i);
            if (e.action() == Action.BLOCK) {
                if (rng.nextDouble() < e.chance()) {
                    double blocked = Math.min(dmg, e.amount());
                    dmg -= blocked;
                    log(target.side, CombatEvent.Type.BLOCK, blocked,
                            target.onAttackedNames.get(i) + ": блок " + fmt(blocked));
                }
            } else {
                applyEffect(target, x, e, target.onAttackedNames.get(i));
            }
        }

        double dealt = damage(target, dmg, x);
        log(x.side, crit ? CombatEvent.Type.CRIT : CombatEvent.Type.HIT, dealt,
                a.name + (crit ? " КРИТ " : " ") + fmt(dealt));

        if (!w.ranged() && !w.pet() && target.get(Status.THORNS) > 0) {
            double thorns = target.get(Status.THORNS);
            x.hp -= thorns;
            log(target.side, CombatEvent.Type.THORNS, thorns, "Шипы: " + fmt(thorns));
        }
        if (x.get(Status.LIFESTEAL) > 0 && dealt > 0) {
            heal(x, x.get(Status.LIFESTEAL), "Вампиризм");
        }
        if (!w.pet() && x.spec.hero().ability().type() == AbilityDef.Type.ELEMENT_SWITCH) {
            AbilityDef ab = x.spec.hero().ability();
            if (x.fire) {
                target.add(Status.BURN, ab.param("burn", 1) + x.burnBonus);
            } else {
                target.add(Status.CHILL, ab.param("chill", 1));
            }
        }
        for (EffectDef e : a.onHit) {
            applyEffect(x, target, e, a.name);
        }
        if (crit) {
            for (EffectDef e : a.onCrit) {
                applyEffect(x, target, e, a.name);
            }
        }
    }

    /** Урон через броню. Возвращает урон по HP+броне. Обрабатывает отражение «Железной стойки». */
    private double damage(Fighter target, double dmg, Fighter source) {
        if (dmg <= 0) {
            return 0;
        }
        double absorbed = Math.min(target.armor, dmg);
        target.armor -= absorbed;
        target.hp -= dmg - absorbed;
        if (absorbed > 0 && source != null && now <= target.reflectUntil) {
            double reflected = absorbed * target.spec.hero().ability().param("reflect", 0);
            if (reflected > 0) {
                source.hp -= reflected;
                log(target.side, CombatEvent.Type.THORNS, reflected, "Отражение: " + fmt(reflected));
            }
        }
        return dmg;
    }

    private void heal(Fighter x, double amount, String source) {
        double before = x.hp;
        x.hp = Math.min(x.maxHp, x.hp + amount);
        if (x.hp > before) {
            log(x.side, CombatEvent.Type.HEAL, x.hp - before, source + ": +" + fmt(x.hp - before));
        }
    }

    private void tickStatuses(Fighter x) {
        double burn = x.get(Status.BURN);
        if (burn > 0) {
            double dmg = burn * cfg.burnDamage();
            damage(x, dmg, null);
            x.add(Status.BURN, -cfg.burnDecay());
            log(x.side, CombatEvent.Type.STATUS, dmg, "Ожог: −" + fmt(dmg));
        }
        double poison = x.get(Status.POISON);
        if (poison > 0) {
            double dmg = poison * cfg.poisonDamage();
            x.hp -= dmg; // яд игнорирует броню
            log(x.side, CombatEvent.Type.STATUS, dmg, "Яд: −" + fmt(dmg));
        }
        x.add(Status.CHILL, -cfg.chillDecay());
        x.add(Status.HASTE, -cfg.hasteDecay());
    }

    private void useAbility(Fighter x, AbilityDef ab) {
        Fighter target = enemy(x);
        switch (ab.type()) {
            case IRON_GUARD -> {
                x.armor = Math.min(cfg.armorCap(), x.armor + ab.param("armor", 15));
                x.reflectUntil = now + ab.param("duration", 4);
                log(x.side, CombatEvent.Type.ABILITY, ab.param("armor", 15), ab.name() + ": +броня, отражение");
            }
            case ELEMENT_SWITCH -> {
                x.fire = !x.fire;
                log(x.side, CombatEvent.Type.ABILITY, 0, ab.name() + ": " + (x.fire ? "огонь" : "лёд"));
            }
            case SWARM -> {
                int count = (int) ab.param("count", 2);
                WeaponStats bee = new WeaponStats(ab.param("damage-min", 2), ab.param("damage-max", 3),
                        ab.param("interval", 1.5), 0, 0.85, 0, false, true);
                for (int i = 0; i < count; i++) {
                    Attacker a = new Attacker("Пчела", bee, now + ab.param("duration", 6));
                    a.onHit.add(new EffectDef(Trigger.ON_HIT, Action.POISON, ab.param("poison", 1), 1, 0, 0));
                    x.attackers.add(a);
                }
                log(x.side, CombatEvent.Type.ABILITY, count, ab.name() + ": пчёл " + count);
            }
            case VOLLEY -> {
                int arrows = (int) ab.param("arrows", 3);
                double perArrow = ab.param("damage", 3) + x.rangedCount * ab.param("per-ranged", 1);
                log(x.side, CombatEvent.Type.ABILITY, arrows, ab.name() + ": стрел " + arrows);
                for (int i = 0; i < arrows && target.alive(); i++) {
                    if (rng.nextDouble() >= 0.85 + x.accuracyBonus) {
                        continue;
                    }
                    double dmg = perArrow;
                    if (rng.nextDouble() < x.crit + ab.param("crit", 0)) {
                        dmg *= x.critMult;
                    }
                    double dealt = damage(target, dmg, x);
                    log(x.side, CombatEvent.Type.HIT, dealt, "Стрела " + fmt(dealt));
                }
            }
        }
    }

    private void applyEffect(Fighter owner, Fighter enemy, EffectDef e, String source) {
        if (e.chance() < 1 && rng.nextDouble() >= e.chance()) {
            return;
        }
        if (e.manaCost() > 0) {
            if (owner.mana < e.manaCost()) {
                return;
            }
            owner.mana -= e.manaCost();
        }
        double v = e.amount();
        switch (e.action()) {
            case ARMOR -> owner.armor = Math.min(cfg.armorCap(), owner.armor + v);
            case THORNS -> owner.add(Status.THORNS, v);
            case HEAL -> heal(owner, v, source);
            case HASTE -> owner.add(Status.HASTE, v);
            case EMPOWER -> owner.add(Status.EMPOWER, v);
            case LIFESTEAL -> owner.add(Status.LIFESTEAL, v);
            case MANA -> owner.mana = Math.min(owner.maxMana, owner.mana + v);
            case BLOCK -> { /* обрабатывается при атаке */ }
            case BURN -> enemy.add(Status.BURN, v + owner.burnBonus);
            case POISON -> enemy.add(Status.POISON, v);
            case CHILL -> enemy.add(Status.CHILL, v);
            case STUN -> enemy.add(Status.STUN, v * cfg.stunSeconds());
            case DAMAGE -> damage(enemy, v, owner);
        }
        if (e.trigger() == Trigger.START || e.trigger() == Trigger.TIMER) {
            log(owner.side, CombatEvent.Type.STATUS, v, source + ": " + e.action().displayName() + " " + fmt(v));
        }
    }

    private void log(int side, CombatEvent.Type type, double amount, String text) {
        if (events.size() < cfg.maxLoggedEvents()) {
            events.add(event(side, type, amount, text));
        }
    }

    private CombatEvent event(int side, CombatEvent.Type type, double amount, String text) {
        return new CombatEvent(now, side, type, amount, text,
                Math.max(0, f[0].hp), Math.max(0, f[1].hp), f[0].armor, f[1].armor);
    }

    private static String fmt(double v) {
        return String.valueOf(Math.round(v * 10) / 10.0).replace(".0", "");
    }
}
